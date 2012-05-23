(ns ring.middleware.logger
  (require
   [clj-logging-config.log4j :as log-config]
   [clojure.tools.logging :as log]
   [clansi.core :as ansi]
   ))

(def base-log-name "logs/ring.log")

;; The generation of the calling class, line numbers, etc. is
;; extremely slow, and should be used only in development mode or for
;; debugging. Production code should not log that information if
;; performance is an issue. See
;; http://logging.apache.org/log4j/companions/extras/apidocs/org/apache/log4j/EnhancedPatternLayout.html
;; for information on which fields are slow.
;;
(def debugging-log-prefix-format "%d %l [%p] : %throwable%m%n")
(def production-log-prefix-format "%d [%p] : %throwable%m%n")


;; Some basic logging adapters.
;; Many other logging infrastructures are possible. There are syslog
;; adapters, network socket adapters, etc.
;; For more information, see:
;; - https://github.com/malcolmsparks/clj-logging-config
;; - http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AppenderSkeleton.html for different log destinations
;; - http://logging.apache.org/log4j/companions/extras/apidocs/org/apache/log4j/EnhancedPatternLayout.html for prefix formatting options
;;
;; TODO: Make a custom layout class that colorizes the log level. Maybe this can be done in a filter.
;;
(def rotating-logger
  "This logging adapter rotates the logfile nightly at about midnight."
  (org.apache.log4j.DailyRollingFileAppender.
   (org.apache.log4j.EnhancedPatternLayout. production-log-prefix-format)
   base-log-name
   ".yyyy-MM-dd"))

(def appending-logger
  "This logging adapter simply appends new log lines to the existing logfile."
  (org.apache.log4j.FileAppender.
   (org.apache.log4j.EnhancedPatternLayout. production-log-prefix-format)
   base-log-name
   true))

(defn setup-default-logger!
  "Initializes the logger in the caller's current namespace. This must
  be called once per namespace that plans to log things, so if you
  want to use the same log facilities as defined in the Ring
  middleware, you need to call this function in each namespace from
  which you plan to log."
  []
  (log-config/set-logger! (str *ns*)
                          :level :debug
                          :out appending-logger))


;; We must initialize the Log4J backend once per namespace that will
;; be logging things. Here, we initialize it for ring.middleware.logger.
(setup-default-logger!)

(defn- log4j-color-logger [status totaltime {:keys [request-method uri remote-addr] :as req}]
  "Log4J-enabled logformatter for logging middleware.

Log messages will be colorized with ANSI escape codes. Use
log4j-colorless-logger if you want plaintext.

Sends all log messages at \"info\" level to the Log4J logging
  infrastructure, unless status is >= 500, in which case they are sent
  as errors."
  (let [colorstatus (apply ansi/style
                           (str status)
                           (cond
                            (< status 300) [:grey] 
                            (>= status 500) [:bright :red] 
                            (>= status 400) [:red] 
                            :else [:yellow]))
        log-message (str "(" remote-addr ") "
                         request-method " "
                         uri " "
                         "[Status: " colorstatus "] "
                         " (" totaltime " ms)"
                         ) ]

    (if (>= status 500)
      (log/error log-message)
      (log/info log-message))))

(defn- log4j-colorless-logger
  [status totaltime request]
  "Like log4j-color-logger, but doesn't log any ANSI color codes."
  (ansi/without-ansi (log4j-color-logger status totaltime request)))

;; wrap-with-logger originally based on
;; https://gist.github.com/kognate/noir.incubator/blob/master/src/noir.incubator/middleware.clj
(defn wrap-with-logger
  ([handler logger]
     "Adds logging for requests using the given logger function.
The logger function will receive 3 arguments: the integer response
status (e.g. 200 for OK), the total time taken by response generation,
and the request map."
      (fn [request]
        (let [start  (System/currentTimeMillis)
              resp   (handler request)
              status (:status resp)
              finish (System/currentTimeMillis)
              total  (- finish start)]
          (logger status total request)
          resp)))
  ([handler]
     "Adds logging using a prepackaged default logger."
     (wrap-with-logger log4j-color-logger)))

