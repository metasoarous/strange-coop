(ns strange-coop.util
  (:require [clojure.data.csv :as csv]
            [clojure.core.async :as async :refer [>!! <!!]]))

(defn log
  [& args]
  (apply println "LOG" (int (/ (System/currentTimeMillis) 1000)) ":" args))

(defn notify
  [component level message]
  (>!! (-> component :channels :notify)
       {:type level :time (System/currentTimeMillis) :message message}))

(defn log-tr
  [& args]
  (log args)
  (last args))

(defn safe-int [x]
  (try
    (Integer/parseInt x)
    (catch Exception e
      x)))

(defmacro wait-till [test & body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))

(defmacro try-times
  [i message & body]
  `(loop [i# ~i]
     (let [result#
             (when (> i# 0)
               (try
                 ~@body
                 (catch Exception e#
                   (println ~message ":" e#)
                   ::failed)))]
       (if (= result# ::failed)
         (recur (dec i#))
         result#))))

(defn read-csv-row [row]
  (first (csv/read-csv row)))

(defn kw->str [kw]
  (clojure.string/replace (str kw) ":" ""))

(defn setup-shutdown-hook!
  [f]
  (.addShutdownHook (Runtime/getRuntime) (Thread. f)))

(defn dequalify-keyword
  [kw]
  (-> :this/that str (clojure.string/split #"\/") last keyword))

(comment
  ;; for dev only
  (require '[cemerick.pomegranate :refer [add-dependencies]])
  (defn load-dep
    [dep]
    (add-dependencies :coordinates [dep] :repositories (merge cemerick.pomegranate.aether/maven-central {"clojars" "http://clojars.org/repo"})))
  )

