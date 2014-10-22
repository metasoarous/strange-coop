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

(defprotocol ReadablePin
  (read!
    [this]
    "Read a raw value from a GPIO or AIN pin. For AIN this will be a value between 0 and 1,
    and for GPIO either :on or :off"))

(defprotocol WriteablePin
  (write!
    [this val]
    "Write a vlaue to an io out pin. Exceptable values are :on and :off. Will fail if the
    pin is not an :out pin."))

(defprotocol InitablePin
  (init! [this] "Initialize a GPIO pin. Exports the pin for usage, via the filesystem API.")
  (close! [this] "Unexports a GPIO pin using filesystem API."))



;; GPIO specific stuff

(def gpio-path "/sys/class/gpio")


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


; XXX - I guess in all actuality, GPIO should only know about its fs-pin-n, which gets computed once in
; the gpio constructor and then used with the protocol implementations
(defrecord GPIO [header pin direction]
  InitablePin
  (init! [this]
    ; should set :pre checks here? XXX - In particular, should make sure we don't try to
    ; init a write pin where we already have a read pin
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
  (close! [_]
    (write-bytes
      (str "/sys/class/gpio/unexport")
      (get-gpio-n header pin)))
  WriteablePin
  (write! [_ value]
    {:pre [(#{:on :off} direction)]}
    (write-bytes
      (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value")
      ({:on "1" :off "0"} value)))
  ReadablePin
  (read! [_]
    ({"1" :on "0" :off}
     (read-bytes
       (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value")))))


(defn gpio [header pin direction]
  {:pre [(integer? pin) ; should make sure valid pin
         (#{:P8 :P9} header)
         (#{:in :out :high :low} direction)]}
  (let [g (GPIO. header pin direction)]
    (init! g)
    g))


(defn on? [gp]
  (= (:on (read! gp))))

(defn off? [gp]
  (= (:off (read! gp))))

(defn on! [pin]
  (write! pin :on))

(defn off! [pin]
  (write! pin :off))


(defmulti writes!
  "Write to multiple pins. If second arg is a collection, will write! nth item of second arg to nth pin in pins.
  If not a collection, second arg should be :on or :off"
  #(coll? (second %)))

(defmethod writes! true
  [pins bits]
  (doseq [[p b] (map vector pins bits)]
    (write! p b)))

(defmethod writes! false
  [pins bit]
  (doseq [p pins]
    (write! p bit)))



;; AIN specific stuff

(defn get-ain-from-pin [pin]
  (:name
    ((pinout-spec*) [:P9 pin])))


; AIN pins are all on :P9, so specifying header is useless
(defrecord AIN [pin]
  ReadablePin
  (read! [_]
    (->>
      (get-ain-from-pin pin)
      (str "/sys/devices/ocp.2/helper.14/")
      (read-bytes)
      (safe-int)
      (* (/ 1 1800.0)))))


(defn ain [pin]
  (AIN. pin))


