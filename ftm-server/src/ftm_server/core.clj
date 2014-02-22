(ns ftm-server.core
  "Server for 'Find This Music'"

  (:require [compojure.route     :as route]
            [compojure.handler   :as handler]
            [clojure.tools.cli   :as cli]
            [clojure.edn         :as edn]
            [clojure.data.json   :as json]
            )

  (:use bagotricks
        compojure.core
        [clojure.stacktrace :only [print-stack-trace]]
        [org.httpkit.server :only [run-server]]
        )

  ;; Make the jar executable
  (:gen-class))

(def default-port
  "Specifies the default port to server from"
  8080)


;; To aid with dev
(def request-history? false)
(def request-history (atom []))

(defn add-to-history [obj]
  "Add obj to the request-history if enabled"
  (if request-history?
    (swap! request-history conj obj)))

(defn alive-route []
  "Returns a body and status code indicating that the server is operating properly"
  (context "/alive" []
           (GET "/" req {:body {:status "It's ALIVE!!!"}})))

(defn get-body [req]
  "Get the body from the request"
  (if-let [body-bis (some-> req :body)]
    (do (if (zero? (.available body-bis))
          (.reset body-bis))
        (slurp body-bis))))

(defn reflect-route []
  "Reflects whatever is PUT/POST'ed"
  (context "/reflect" []
           (ANY "/" req (let [body (get-body req)]
                          (prn body)
                          {:body {:request-body body}}))))

(defn test-route []
  "Reflects whatever is PUT/POST'ed"
  (context "/test" []
           (ANY "/" req
                (if-let [body (get-body req)]
                  (do
                    (add-to-history body)
                    (prn body)
                    {:status 200
                     :body {:test-output (str "NOKAY: " body)}})))))

(defn make-it-json [app]
  "Convert the response body from EDN to JSON"
  (fn [{:keys [params] :as req}]
    (let [response (app req)]
      (if-let [body (:body response)]
        (assoc response :body (json/write-str body :key-fn name))
        response))))

(defn httpd
  "Starts the server with the arguments specified."
  [{:keys [port] :as server-args}]
  (let [vacr-routes (->> [(route/not-found "Not Found")] ;; Last route to execute
                         (concat [(alive-route)])
                         (concat [(test-route)])
                         (concat [(reflect-route)]) ;; FOR DEBUGGING!!!!
                         (apply routes)
                         (make-it-json)
                         handler/api)]
    (run-server vacr-routes {:port port})))

(defn -main [& args]
  (let [port (or default-port)]
    (println "Starting server on " port)
    (httpd {:port port})))









(comment
  ;; BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS   BEGIN COMMENTS


  (def kill-server (httpd {:port default-port}))




  ;; END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS
  )
