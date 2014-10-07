(ns chicken-coop.util
  (:require [clojure.data.csv :as csv]))


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


