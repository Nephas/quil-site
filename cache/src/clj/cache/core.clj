(ns cache.core
  (:require [cljs.build.api :as build]
            [ring.adapter.jetty :as jetty]
            [clojure.java.io :as io]))

(defn redirect [input output]
  (try
    (let [data (byte-array 1024)
          input (io/reader input)]
      (loop [line (.readLine input)]
        (when line
          (.write output line)
          (.write output "\n")
          (.flush output)
          (recur (.readLine input)))))
    (catch Exception e
      (.printStackTrace e))))

(defn stream-sh [& args]
  (let [proc (.exec (Runtime/getRuntime)
               ^"[Ljava.lang.String;" (into-array args))]
    (future (redirect (.getInputStream proc) *out*))
    (future (redirect (.getErrorStream proc) *err*))
    (.waitFor proc)
    (= (.exitValue proc) 0)))

(defn get-file [req]
  (let [uri (subs (:uri req) 1)]
    (if-let [file (io/resource uri)]
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body (slurp (io/reader file))}
      {:status 404})))

(defn start-server []
  (jetty/run-jetty #'get-file {:port 7788 :join? false}))

;(def serve (start-server))
;(.stop serve)

(defn build-cljs [watch]
  ((if watch build/watch build/build)
   "src/cljs"
   {:main 'cache.core
    :output-to "main.js"
    :output-dir "out"
    :target :nodejs
    :optimizations :none
    :pretty-print true}))

(defn run [& args]
  (let [server (start-server)]
    (build-cljs (= (first args) "watch"))
    (println "built cljs, running node")
    (stream-sh "node" "main.js")
    (println "node finished")
    (.stop server)
    (System/exit 0)))
