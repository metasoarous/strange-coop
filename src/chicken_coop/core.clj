(ns chicken-coop.core
  (:require [clojure.java.io :as io]
            [chicken-coop.util :refer :all]
            [chicken-coop.bbbpin :as bb :refer :all]
            [chicken-coop.hbridge :as hb]))


; Helper functions so I can keep track of whether doors are closed or open
(def closed? off?)
(def open? on?)


(defmacro wait-till [test body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))


(defn close-door! [hb floor-io]
  (hb/forward! hb)
  (wait-till (closed? floor-io)
    (hb/stop! hb)))


(defn open-door! [hb roof-io]
  (hb/reverse! hb)
  (wait-till (closed? roof-io)
    (hb/stop! hb)))


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
    (> (read! light-ain) 0.35) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (let [_ 33
        floor-io (gpio _ _ _)
        roof-io (gpio _ _ _)
        light-ain (ain _)
        hb (hb/hbridge [:P8 _] [:P8 _] [:P8 _] [:P8 _])
        timer (time-sm
                (init-state! floor-io roof-io light-ain)
                (partial close-door! hb roof-io)
                (partial open-door! hb roof-io))]
    (loop [time-sm time-sm]
      (recur (trans-sm! timer (read! light-ain))))))


(defn play []
  (println "ready to initialize")
  (let [g (gpio :P8 11 :low)
        a (ain 33)]
    (setup-shutdown-hook!
      (fn [] (close! g)))
    (println "Pin settings" ((pinout-spec*) [:P8 11]))
    (println "initialized successfully")
    (doseq [_ (range 100)]
      (println "Writing :on")
      (write! g :on)
      (Thread/sleep 1000)
      (println "Writing :off")
      (write! g :off)
      (println "Reading ain:" (read! a))
      (Thread/sleep 1000))))


