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


(defn close-door! [hb floor-btn]
  (hb/forward! hb)
  (wait-till (closed? floor-btn)
    (hb/stop! hb)))


(defn open-door! [hb roof-btn]
  (hb/reverse! hb)
  (wait-till (closed? roof-btn)
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
  [floor-btn roof-btn light-ain]
  (cond
    (closed? floor-btn) :night
    (closed? roof-btn) :day
    (> (read! light-ain) 0.35) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (let [floor-btn (gpio :P8 11 :in)
        roof-btn  (gpio :P8 12 :in)
        light-ain (ain 33)
        temp-ain  (ain 35)
        mtr-ctrl  (hb/hbridge [16 17 18] :header :P8)
        timer     (time-sm
                    (init-state! floor-btn roof-btn light-ain)
                    (partial close-door! mtr-ctrl roof-btn)
                    (partial open-door! mtr-ctrl roof-btn))]
    (setup-shutdown-hook!
      (fn [] (doseq [p (concat
                         [floor-btn
                          roof-btn]
                         (for [x [:power :-pin :+pin]]
                               (mtr-ctrl x)))]
               (close! p))))
    (loop [time-sm time-sm]
      (recur (trans-sm! timer (read! light-ain))))))


(defn play []
  (println "ready to initialize")
  (let [g  (gpio :P8 11 :out)
        a1 (ain 33)
        a2 (ain 35)]
    (setup-shutdown-hook!
      (fn [] (close! g)))
    (println "Pin settings" ((pinout-spec*) [:P8 11]))
    (println "initialized successfully")
    (doseq [_ (range)]
      (try
        (println "Reading light" (read! a1))
        (println "Reading temp" (read! a2))
        (println "Reading btn" (read! g))
        (println "")
        (catch Exception e
          (println "Had an exception")
          (println e)))
      (Thread/sleep 1000))))


