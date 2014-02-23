(defproject ftm-server "0.1.0-SNAPSHOT"
  :description "'Find This Music' server"
  :url "http://github.com/thebusby/FindThisMusic/"
  :license {:name "The MIT License"
            :url "https://github.com/thebusby/FindThisMusic/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure   "1.5.1"]
                 [org.clojure/tools.cli	"0.3.1"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.xml  "0.0.7"]
                 [org.clojure/data.zip  "0.1.1"]

                 [bagotricks "1.5.4"]
                 [iota "1.1.1"]
                 [easy-parse "1.4.1"] 

                 [http-kit "2.1.16"]

                 [compojure "1.1.6"]
                 [javax.servlet/servlet-api "2.5"]
                 [enlive "1.1.5"]

                 [com.cemerick/pomegranate  "0.3.0"] ;; For adding dependencies as I go

                 ;; - = - - = - - = - - = - - = - - =
                 ;; Gracenote internal libraries :(
                 ;; - = - - = - - = - - = - - = - - =
                 [gnateway                  "1.5.1"] ;; For backend access to GN databases and services
                 [gn/cddbj                  "1.1.0"] ;; For text normalization and text comparisons
                 [confluence/confluence     "3.0.6"] ;; For some fast text indexing
                 ]

  :jvm-opts ["-Dswank.encoding=utf-8"
             "-server"
             "-Xms2000m"
             "-Xmx48000m"
             "-Xmn1000m"]
  :warn-on-reflection true

  :native-path "jni"

  :aot :all
  :main ftm-server.core)
