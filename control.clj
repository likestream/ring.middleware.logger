(use '[control.commands])

(defcluster :lsmaven
  :user ~(System/getenv "USER")
  :addresses ["maven.likestream.net"])

(deftask :deploy-to-maven "Deploy jar and pom to maven repo" [name version]
  (ssh (str "rm -f /home2/maven/maven2/net/likestream/" name "/" version "/" name "-" version ".jar") :sudo true)  
  (ssh (str "rm -f /home2/maven/maven2/net/likestream/" name "/" version "/" name "-" version ".pom") :sudo true)
  (ssh (str "mkdir -p /home2/maven/maven2/net/likestream/" name "/" version) :sudo true)
  (scp (str "./target/" name "-" version ".jar") (str "/home2/maven/maven2/net/likestream/" name "/" version) :sudo true)
  (scp "pom.xml" (str "/home2/maven/maven2/net/likestream/" name "/" version "/" name "-" version ".pom") :sudo true)
  (ssh (str "chown -R maven:daemon /home2/maven/maven2/net/likestream/" name) :sudo true))

(deftask :deploy "Deploy all libs to maven repo" []
  (call :deploy-to-maven "ring.middleware.logger" "0.2.3-SNAPSHOT"))





        
        
             
        
       
       
       




        
        
             
        
       
       
       


