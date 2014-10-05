(ns chicken-coop.pinctrl
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]))


(def gpio-path "/sys/class/gpio")


(defn -safe-int [x]
  (try
    (Integer/parseInt x)
    (catch Exception e
      x)))


(defn read-pinout-spec [pinout-fn]
  (with-open [f (io/reader pinout-fn)]
    (let [non-cmt-lines (remove #(or (re-find #"^\#" %)
                                     (re-find "^\s*$" %))
                                (line-seq f))
          header (csv/read-csv (first non-cmt-lines))
          non-cmnt-lines (rest non-cmt-lines)]
      ; The key of the outer most hash: lets us find configs by (eg) (pinout :P8 9)
      (into
        {}
        (for [l non-cmt-lines]
          [[(keyword (non-cmt-lines 0))
            (-safe-int (non-cmt-lines 1))]
           (into {}
             (map vector header (csv/read-csv non-cmt-lines)))])))))

(def pinout-spec* (memoize read-pinout-spec))


(defn write-bytes [filename b]
  (with-open [f (io/output-stream filename)]
    (.write f (.getBytes b))))


(defn read-bytes [filename]
  (-> filename
      slurp
      clojure.string/split-lines
      first))


(defn get-gpio-n [header pin]
  (->
    (pinout-spec* "etc/pinout.csv")
    (apply [header pin])
    :name
    (re-matcher #"GPIO(\d)_(\d*)")
    (->> (map #(Integer/parseInt %)))
    (as-> m (+ (* 32 (m 1))
               (m 2)))
    (str)))


(defprotocol BBBPin
  (init! [p])
  (write! [p value])
  (read! [p]))

(defrecord GPIO [header pin mode]
  BBBPin
  (init! [_]
    ; should set :pre checks here?
    (let [pin-n (get-gpio-n header pin)]
      (write-bytes "/sys/class/gpio/export" pin-n)
      (write-bytes (str "/sys/class/gpio" pin-n "/direction") mode)))
  (write!
    "Specify value as on or off (as str or kw) or 1 or 0 (as str or int)"
    [_ value]
    {:pre [(#{:on :off} mode)]}
    (write-bytes
      (str "/sys/class/gpio" (get-gpio-n header pin) "/value")
      ({:on "1" :off "0"} value)))
  (read! [_]
    ({"1" :on "0" :off} (read-bytes (str "/sys/class/gpio" (get-gpio-n header pin) "/value")))))


(defn gpio [header pin mode]
  {:pre [(integer? pin) ; should make sure valid pin
         (#{"P8" "P9"} header)
         (#{"in" "out" "high" "low"} mode)]}
  (let [g (GPIO. pin mode)]
    (init! g)
    g))


(defn -main []
  (println "ready to initialize")
  (let [g (gpio :P8 3 :out)]
    (println "initialized successfully")
    (doseq [_ (range)]
      (println "Writing :on")
      (write! g :on)
      (Thread/sleep 1000)
      (println "Writing :off")
      (write! g :off)
      (Thread/sleep 1000))))


