(ns ftm-server.core
  "Server for 'Find This Music'"

  (:require [compojure.route        :as route]
            [compojure.handler      :as handler]
            [clojure.tools.cli      :as cli]
            [clojure.edn            :as edn]
            [clojure.data.json      :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.core.reducers  :as r]
            [iota                   :as io]
            [confluence             :as conf]
            [clojure.data.xml       :as xml]
            [org.httpkit.client     :as http]
            [easy-parse             :as ep]
            [clojure.data.zip.xml   :as zf]
            )

  (:use bagotricks
        compojure.core
        [clojure.stacktrace :only [print-stack-trace]]
        [org.httpkit.server :only [run-server]]
        )

  (:import  [cddbj Text])

  ;; Make the jar executable
  (:gen-class))


;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -
;; DEFONCE FOLLOWS
;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -

(defonce artist-ndx  (io/vec "artist_gn.ndx.tsv"))

(defonce gn-odp-creds {:client-id "foo" :client-tag "bar"})

;; Of the format, (def creds (register-user-id {:client-id "foo" :client-tag "bar"}))
(defonce ^:dynamic creds {}) 

(declare txt-clean)

(defonce clean-artist-names
  (->> (io/subvec artist-ndx 1)
       (r/filter identity)
       (r/map (fn [line]
                (let [[_ name yomi lookups-str] (split-tsv line)
                      lookups (to-long lookups-str)]
                  (if (> lookups 50000)
                    [(txt-clean name) (quot lookups 50000)]))))
       (r/filter identity)
       (fold-into-vec)
       (into {})))

;; To aid with dev
(defonce request-history? false)
(defonce request-history (atom []))



;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -
;; YOUTUBE!!!
;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -

(defn- url-encode [^String s]
  "Return an URL encoded version of the provided string"
  (when s
    (java.net.URLEncoder/encode s "utf-8")))

(defn- q-encode [terms]
  "Encode search terms per Google's query specification"
  (some->> terms
           (interpose " ")
           (apply str)
           url-encode))

(defn- search-youtube-music [terms]
  "Search youtube for videos associated with the provided terms"
  (try
   (some->> @(http/get (str "https://gdata.youtube.com/feeds/api/videos")
                      {:query-params {"v" "2"
                                      "alt" "jsonc"
                                      "max-results" "10"
                                      "strict" "true"
                                      "q" (q-encode (str terms " music"))}})
           :body
           json/read-json
           :data
           :items
           (sort-by :viewCount >)
           vec
           (assoc {} :youtube))

   (catch Exception e
     nil))) ;; return exception if one occurs



;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -
;; WEBAPI FUN
;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -

(defn get-webapi-url [{:keys [client-id]}]
  (str "https://" client-id ".web.cddbp.net/webapi/xml/1.0/"))

(defn parse-xml [format xml]
  "Provided a template, parse the xml and return the data"
  (ep/easy-parse (some->> xml
                          (java.io.StringReader.)
                          (xml/parse)
                          (clojure.zip/xml-zip)
                          )
                 format))

(def register-response-format {:user-id #(zf/xml1-> %1 :RESPONSE :USER zf/text)})

(defn register-user-id [{:keys [client-id client-tag user-id] :as creds}]
  (if user-id
    creds
    (let [request-xml (xml/emit-str
                       (xml/sexp-as-element [:QUERIES
                                             [:QUERY {:CMD "REGISTER"}
                                              [:CLIENT (str client-id "-" client-tag  "-" client-tag)]]]))]
      (->> request-xml
           (assoc {} :body)
           (http/post (get-webapi-url creds))
           deref
           :body
           (parse-xml register-response-format)
           :user-id
           (assoc creds :user-id )))))

(defn get-service-xml
  [{:keys [client-id client-tag user-id]} query-xml]
  (xml/emit-str (xml/element :QUERIES {}
                             (xml/element :AUTH {}
                                          (xml/element :CLIENT {} (str client-id "-" client-tag "-" client-tag))
                                          (xml/element :USER {} user-id))
                             (xml/element :LANG {} "jpn")
                             (xml/element :COUNTRY {} "japan")
                             query-xml)))

(defn get-text-search-request
  [{:keys [artist-name album-name track-name]}]
  (when (or artist-name album-name track-name)
    (xml/sexp-as-element [:QUERY {:CMD "ALBUM_SEARCH"}
                          [:MODE "SINGLE_BEST"]
                          (if artist-name
                            [:TEXT {:TYPE "ARTIST"} artist-name])
                          (if album-name
                            [:TEXT {:TYPE "ALBUM_TITLE"} album-name])
                          (if track-name
                            [:TEXT {:TYPE "TRACK_TITLE"} track-name])])))

(def search-response-format {:album-gnid #(zf/xml1-> %1 :RESPONSE :ALBUM :GN_ID zf/text)
                             :artist-name  #(zf/xml1-> %1 :RESPONSE :ALBUM :ARTIST zf/text)
                             :album-name #(zf/xml1-> %1 :RESPONSE :ALBUM :TITLE zf/text)})

(defn get-album-fetch-request
  [gn-id]
  (when gn-id
    (xml/sexp-as-element [:QUERY {:CMD "ALBUM_FETCH"}
                          [:GN_ID gn-id]
                          [:OPTION
                           [:PARAMETER "SELECT_EXTENDED"]
                           [:VALUE "COVER,REVIEW,ARTIST_BIOGRAPHY,ARTIST_IMAGE,ARTIST_OET,MOOD,TEMPO"]]
                          [:OPTION
                           [:PARAMETER "SELECT_DETAIL"]
                           [:VALUE "GENRE:3LEVEL,MOOD:2LEVEL,TEMPO:3LEVEL,ARTIST_ORIGIN:4LEVEL,ARTIST_ERA:2LEVEL,ARTIST_TYPE:2LEVEL"]]
                          [:OPTION
                           [:PARAMETER "COVER_SIZE"]
                           [:VALUE "MEDIUM,LARGE,XLARGE,SMALL,THUMBNAIL"]
                           ]])))

(def album-fetch-response-format {:album-gnid   #(zf/xml1-> %1 :RESPONSE :ALBUM :GN_ID zf/text)
                                  :coverart-url #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "COVERART") zf/text)
                                  :coverart-size #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "COVERART") (zf/attr :SIZE))
                                  :coverart-width #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "COVERART") (zf/attr :WIDTH))
                                  :coverart-height #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "COVERART") (zf/attr :HEIGHT))
                                  :artist-image-url #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "ARTIST_IMAGE") zf/text)
                                  :artist-image-size #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "ARTIST_IMAGE") (zf/attr :SIZE))
                                  :artist-image-width #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "ARTIST_IMAGE") (zf/attr :WIDTH))
                                  :artist-image-height #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "ARTIST_IMAGE") (zf/attr :HEIGHT))
                                  :biography-url #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "ARTIST_BIOGRAPHY") zf/text)
                                  :review-url #(zf/xml1-> %1 :RESPONSE :ALBUM :URL (zf/attr= :TYPE "REVIEW") zf/text)
                                  })

(defn do-request [creds input output-format]
  (when (and creds input output-format)
   (->> input
        (get-service-xml creds)
        (assoc {} :body)
        (http/post (get-webapi-url creds))
        deref
        :body
        (parse-xml output-format))))

(defn debug-request [creds input]
  (->> input
       (get-service-xml creds)
       (assoc {} :body)
       (http/post (get-webapi-url creds))
       deref
       :body))

(defn get-gn-extras [creds artist]
  "Provided an artist name, get GN's 'extras' for the artist"
  (future (try (let [youtube-resp (future (search-youtube-music artist))
                     search-resp (do-request creds
                                             (get-text-search-request {:artist-name artist})
                                             search-response-format)
                     fetch-resp (do-request creds
                                            (get-album-fetch-request (:album-gnid search-resp))
                                            album-fetch-response-format)]
                 (if fetch-resp
                   (merge {:artist-found artist
                           :vpop (-> artist
                                     txt-clean
                                     clean-artist-names)}
                          search-resp
                          fetch-resp
                          @youtube-resp)))
               (catch Exception e
                 nil))))


;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -
;; DATA FUN
;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -

(defn libcddb-normtxt-fixes [^String s]
  "*TEMPORARY* fix to eleviate issues with libcddb's normtxt table"
  (let [replace-chars [["ｳﾞ" "ヴ"]
                       ["ｯ" "ッ"] ;; Hankaku 'tsu' to Zenkaku 'tsu'
                       ["ｧ" "ァ"]
                       ["ｨ" "ィ"]
                       ["ｩ" "ゥ"]
                       ["ｪ" "ェ"]
                       ["ｫ" "ォ"]
                       ["ｬ" "ャ"]
                       ["ｭ" "ュ"]
                       ["ｮ" "ョ"]
                       ["ヴァ" "バ"]
                       ["ヴィ" "ビ"]
                       ["ヴェ" "ベ"]
                       ["ヴォ" "ボ"]]]
    (reduce (fn [str [before after]]
              (clojure.string/replace str before after))
            s
            replace-chars)))

(defn txt-clean [^String string]
  (some->> string
           libcddb-normtxt-fixes
           (Text/normalizeWeak)))

(defn str-cmp [x y]
  (Text/greedyTiling (txt-clean x)
                     (txt-clean y)))

(defn get-artists-on-page [html]
  "Return a list of artist names found on the web page"
  (->> html
       html/html-snippet
       (<- html/select [html/text-node])
       (filter #(not (re-matches #"\s+" %)))
       (filter (comp clean-artist-names txt-clean))
       (into #{})
       sort
       vec))



;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -
;; WEB SERVER
;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; - ;; -

(def default-port
  "Specifies the default port to server from"
  2014)

(defn add-to-history [obj]
  "Add obj to the request-history if enabled"
  (if request-history?
    (swap! request-history conj obj)))

(defn reset-history []
  "Add obj to the request-history if enabled"
  (reset! request-history []))

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
                    (prn body)
                    {:status 200
                     :body {:test-output (str "NOKAY: " body)}})))))

(defn ftm-route []
  "Reflects whatever is PUT/POST'ed"
  (context "/findthismusic" []
           (ANY "/" req
                (if-let [body (get-body req)]
                  (do
                    (add-to-history body)
                    {:status 200
                     :body  (->> body
                                 get-artists-on-page
                                 (into #{})
                                 (mapv #(get-gn-extras creds %1))
                                 (map deref)
                                 (filter identity)
                                 (sort-by :vpop >)
                                 (mapv #(dissoc % :vpop)))})))))

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
  (let [vacr-routes (->> [(route/not-found "404 Dude")] ;; Last route to execute
                         (concat [(alive-route)])
                         (concat [(test-route)])
                         (concat [(ftm-route)])
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


  (->> body
       get-artists-on-page
       (mapv get-gn-extras))


  (do
    (def request-history? true)
    (def kill-server (httpd {:port default-port})))

  (->> @request-history
       last
       get-artists-on-page
       (into #{})
       vec
       (mapv #(get-gn-extras creds %1))
       (mapv deref)
       (filter identity)
       (sort-by :vpop >)
       first
       time)


  (defn get-header [iovec]
    (->> iovec
         first
         split-tsv
         (map std-keyword)
         vec))

   (def clean-artist-names
     "Set of txt-clean'ed artist-names"
     (->> (io/subvec artist-ndx 1)
          (r/filter identity)
          (r/map (fn [line]
                   (let [[_ name yomi lookups] (split-tsv line)]
                     (if (> (to-long lookups) 50000)
                       (txt-clean name)))))
          (r/filter identity)
          (fold-into-vec)
          (into #{})))


  (use '[cemerick.pomegranate :only (add-dependencies)])
  (add-dependencies :coordinates '[[confluence "3.0.6"]])



  ;; END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS   END COMMENTS
  )
