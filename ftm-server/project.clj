(defproject ftm-server "0.1.0-SNAPSHOT"
  :description "'Find This Music' server"
  :url "http://github.com/thebusby/FindThisMusic/"
  :license {:name "The MIT License"
            :url "https://github.com/thebusby/FindThisMusic/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.5.1"]

                 [bagotricks "1.5.4"]
                 [org.clojure/tools.cli	"0.3.1"]

                 [http-kit "2.1.16"]
                 [org.clojure/data.json "0.2.4"]
                 [compojure "1.1.6"]
                 [javax.servlet/servlet-api "2.5"]
                 ]

  :aot :all
  :main ftm-server.core)
