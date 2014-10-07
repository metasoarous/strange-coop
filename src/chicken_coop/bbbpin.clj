(ns chicken-coop.bbbpin
  (:require [clojure.java.io :as io]
            [clojure.tools.trace :as tr]
            [chicken-coop.util :refer :all]))


(defn read-pinout-spec [pinout-fn]
  (println "about to read pinout spec")
  (with-open [f (io/reader pinout-fn)]
    (let [non-cmnt-lines (remove #(or (re-find #"^\#" %)
                                      (re-find #"^\s*$" %))
                                 (line-seq f))
          header (read-csv-row (first non-cmnt-lines))
          non-cmnt-lines (rest non-cmnt-lines)]
      ; Build up a hash mapping header, pin-n pairs to data about the pin
      (into
        {}
        (for [line-str non-cmnt-lines]
          (let [line-vec (read-csv-row line-str)]
            ; the key; eg [:P8 34], for header and pin
            [[(keyword (first line-vec))
              (safe-int (second line-vec))]
             ; the value; a map of key, value pairs for the corresponding header, pin pair
             (into {}
               (map #(vector (keyword %1) %2) header line-vec))]))))))

(def pinout-spec* (memoize (partial read-pinout-spec "etc/pinout.csv")))


(defn write-bytes [filename b]
  (with-open [f (io/output-stream filename)]
    (.write f (.getBytes b))))


(defn read-bytes [filename]
  (-> filename
      slurp
      clojure.string/split-lines
      first))


