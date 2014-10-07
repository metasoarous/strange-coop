(ns chicken-coop.gpio
  (:require [chicken-coop.bbbpin :as bb :refer :all]
            [chicken-coop.util :refer :all]))


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
      (bb/write-bytes "/sys/class/gpio/export" pin-n)
      (try
        (Thread/sleep 1000) ; Actually takes a sec for the control files to init...
        ; Set the direction
        (bb/write-bytes (str "/sys/class/gpio/gpio" pin-n "/direction") (kw->str direction))
        ; In case anything goes wrong with that, try to close the gpio
        (catch Exception e
          (close! this)
          (throw e)))))
  (write! [_ value]
    {:pre [(#{:on :off} direction)]}
    (bb/write-bytes
      (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value")
      ({:on "1" :off "0"} value)))
  (read! [_]
    ({"1" :on "0" :off}
     (bb/read-bytes
       (str "/sys/class/gpio/gpio" (get-gpio-n header pin) "/value"))))
  (close! [_]
    (bb/write-bytes
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
    (setup-shutdown-hook! (fn [] (close! g)))
    (println "Pin settings" ((bb/pinout-spec*) [:P8 11]))
    (println "initialized successfully")
    (doseq [_ (range 100)]
      (println "Writing :on")
      (write! g :on)
      (Thread/sleep 1000)
      (println "Writing :off")
      (write! g :off)
      (Thread/sleep 1000))))


