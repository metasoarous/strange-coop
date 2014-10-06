(ns chicken-coop.pinctrl
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.tools.trace :as tr]))


(def gpio-path "/sys/class/gpio")


(defn -safe-int [x]
  (try
    (Integer/parseInt x)
    (catch Exception e
      x)))


(defn -read-csv-row [row]
  (first (csv/read-csv row)))


(defn read-pinout-spec [pinout-fn]
  (println "about to read pinout spec")
  (with-open [f (io/reader pinout-fn)]
    (let [non-cmnt-lines (remove #(or (re-find #"^\#" %)
                                      (re-find #"^\s*$" %))
                                 (line-seq f))
          header (-read-csv-row (first non-cmnt-lines))
          non-cmnt-lines (rest non-cmnt-lines)]
      ; Build up a hash mapping header, pin-n pairs to data about the pin
      (into
        {}
        (for [line-str non-cmnt-lines]
          (let [line-vec (-read-csv-row line-str)]
            ; the key; eg [:P8 34], for header and pin
            [[(keyword (first line-vec))
              (-safe-int (second line-vec))]
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


(defn get-gpio-n [header pin]
  (->
    (pinout-spec*)
    (apply [[header pin]])
    :name
    (->> (re-matches #"GPIO(\d)_(\d*)")
         (rest)
         (map #(Integer/parseInt %)))
    (as-> m (+ (* 32 (first m))
               (second m)))
    (str)))


(defn kw->str [kw]
  (clojure.string/replace (str kw) ":" ""))


(defprotocol BBBPin
  (init! [this]
    "Initialize pin")
  (write! [this value]
    "Write to pin (:on or :off for gpio)")
  (read! [this]
    "Read from pin")
  (close! [this]
    "Close pin for operation (cleanup)"))

(defrecord GPIO [header pin direction]
  BBBPin
  (init! [this]
    ; should set :pre checks here?
    (let [pin-n (get-gpio-n header pin)]
      (assoc this :pin-n pin-n)
      ; Activate the pin
      (write-bytes "/sys/class/gpio/export" pin-n)
      (try
        (Thread/sleep 1000) ; Actually takes a sec for the control files to init...
        ; Set the direction
        (write-bytes (str "/sys/class/gpio/gpio" pin-n "/direction") (kw->str direction))
        ; In case anything goes wrong with that, try to close the gpio
        (catch Exception e
          (close! this)
          (throw e)))))
  (write! [_ value]
    {:pre [(#{:on :off} direction)]}
    (write-bytes
      (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value")
      ({:on "1" :off "0"} value)))
  (read! [_]
    ({"1" :on "0" :off}
     (read-bytes
       (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value"))))
  (close! [_]
    (write-bytes
      (str "/sys/class/gpio/unexport")
      (get-gpio-n header pin))))


(defn gpio [header pin direction]
  {:pre [(integer? pin) ; should make sure valid pin
         (#{:P8 :P9} header)
         (#{:in :out :high :low} direction)]}
  (let [g (GPIO. header pin direction)]
    (init! g)
    g))


(defn -main []
  (println "ready to initialize")
  (let [g (gpio :P8 11 :low)]
    (try
      (println "Pin settings" ((pinout-spec*) [:P8 11]))
      (println "initialized successfully")
      (doseq [_ (range 100)]
        (println "Writing :on")
        (write! g :on)
        (Thread/sleep 1000)
        (println "Writing :off")
        (write! g :off)
        (Thread/sleep 1000))
      (finally (close! g)))))


