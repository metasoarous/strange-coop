(ns chicken-coop.core
  (:require [clojure.java.io :as io]
            [chicken-coop.util :refer :all]
            [chicken-coop.bbbpin :as bb]
            [chicken-coop.gpio :as gpio]
            [chicken-coop.ain :as ain]))


(defn on? [gp]
  (= (:on (gpio/read! gp))))


(defn off? [gp]
  (= (:off (gpio/read! gp))))

; Helper functions so I can keep track of whether doors are closed or open
(def closed? off?)
(def open? on?)


(defprotocol HBridgeable
  (forward! [this])
  (reverse! [this])
  (stop! [this]))


(defn writes! [pins bit]
  (doseq [p pins]
    (gpio/write! p bit)))


(defrecord HBridge [+for -for +rev -rev]
  HBridgeable
  (forward! [_]
    ; Reverse circuit off
    (writes! [+rev -rev] :off)
    ; Wait a tad to prevent short
    (Thread/sleep 10)
    ; Turn forward circuit on
    (writes! [+for -for] :on))
  (reverse! [_]
    ; similarly as forward! ...
    (writes! [+for -for] :off)
    (Thread/sleep 10)
    (writes! [+rev -rev] :on))
  (stop! [_]
    (writes! [+for -for +rev -rev] :off)))


(defn h-bridge [[+for-pin -for-pin +rev-pin -rev-pin :as pins]]
  (apply #(HBridge. %1 %2 %3 %4)
         (map #(gpio/gpio (first %) (second %) :out) pins)))


(defmacro wait-till [test body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))


(defn close-door! [hb floor-io]
  (forward! hb)
  (wait-till (closed? floor-io)
    (stop! hb)))


(defn open-door! [hb roof-io]
  (reverse! hb)
  (wait-till (closed? roof-io)
    (stop! hb)))


(defn time-sm [state day-fn! night-fn!]
  (let [dusk 0.3
        dawn 0.4]
    {:state state
     :trans
        {:day
            (fn [brightness]
              (if (< brightness dusk)
                (do
                  ; state side effects
                  (night-fn!)
                  :night)
                :day))
         :night
            (fn [brightness]
              (if (> brightness dawn)
                (do
                  ; state side effects
                  (day-fn!)
                  :day)
                :night))}}))


(defn trans-sm! [sm m]
  (assoc sm :state
    (((:trans sm) (:state sm)) m)))


(defn init-state!
  [floor-io roof-io light-ain]
  (cond
    (closed? floor-io) :night
    (closed? roof-io) :day
    (> (ain/read! light-ain) 0.35) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (let [_ 33
        floor-io (gpio/gpio _ _ _)
        roof-io (gpio/gpio _ _ _)
        light-ain (ain/ain _)
        hb (h-bridge [:P8 _] [:P8 _] [:P8 _] [:P8 _])
        timer (time-sm
                (init-state! floor-io roof-io light-ain)
                (partial close-door! hb roof-io)
                (partial open-door! hb roof-io))]
    (loop [time-sm time-sm]
      (recur (trans-sm! timer (ain/read! light-ain))))))


(defn play []
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


