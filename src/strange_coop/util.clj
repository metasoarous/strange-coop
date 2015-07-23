(ns strange-coop.util
  (:require [clojure.data.csv :as csv]
            [cemerick.pomegranate :refer [add-dependencies]]))


(defn log
  [& args]
  (apply println "LOG" (int (/ (System/currentTimeMillis) 1000)) ":" args))


(defn log-tr
  [& args]
  (log args)
  (last args))


(defn safe-int [x]
  (try
    (Integer/parseInt x)
    (catch Exception e
      x)))


(defn read-csv-row [row]
  (first (csv/read-csv row)))


(defn kw->str [kw]
  (clojure.string/replace (str kw) ":" ""))


(defn setup-shutdown-hook!
  [f]
  (.addShutdownHook (Runtime/getRuntime) (Thread. f)))


(defn load-dep
  [dep]
  (add-dependencies :coordinates [dep] :repositories (merge cemerick.pomegranate.aether/maven-central {"clojars" "http://clojars.org/repo"})))

