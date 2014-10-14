(ns chicken-coop.core
  (:require [clojure.java.io :as io]
            [chicken-coop.util :refer :all]
            [chicken-coop.bbbpin :as bb]
            [chicken-coop.gpio :as gpio]
            [chicken-coop.ain :as ain]))


(defn -main []
  (println "ready to initialize")
  (let [g (gpio/gpio :P8 11 :low)
        a (ain/ain 33)]
    (setup-shutdown-hook!
      (fn [] (gpio/close! g)))
    (println "Pin settings" ((bb/pinout-spec*) [:P8 11]))
    (println "initialized successfully")
    (doseq [_ (range 100)]
      (println "Writing :on")
      (gpio/write! g :on)
      (Thread/sleep 1000)
      (println "Writing :off")
      (gpio/write! g :off)
      (println "Reading ain:" (ain/read! a))
      (Thread/sleep 1000))))


