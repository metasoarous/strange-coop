(ns strange-coop.core
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [clojure.tools.trace :as tr]
            [strange-coop.util :refer [log log-tr]]
            [strange-coop.bbbpin :as bb :refer :all]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]))


(defmacro wait-till [test & body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))


(defn blink-led
  "Given an led GPIO pin, blink for the given pattern, specified as a single ms value - or vector of such values -
  to wait between led switching"
  [led-pin pattern]
  (if (number? pattern)
    (recur led-pin [pattern])
    (doseq [t pattern]
      (bb/toggle! led-pin)
      (Thread/sleep t))))


(defonce status (atom :running))

(defn update-status!
  [new-status]
  (cond
    (and (= @status :warnings) (= new-status :running))
      (log "WARNING: won't reduce status to :running from warning")
    (= @status :errors)
      (log "WARNING: can't change status once :errors")
    :else
      (do
        (log "Changing status to" new-status)
        (reset! status new-status))))


(defn time-sm [state day-fn! night-fn!]
  (let [dusk 0.03
        dawn 0.13]
    {:state state
     :trans
        {:day
            (fn [brightness]
              (if (< brightness dusk)
                ; state side effects
                (do
                  (log "Switching from day to night and running evening routine")
                  (night-fn!)
                  :night)
                ; Don't change anything
                :day))
         :night
            (fn [brightness]
              (if (> brightness dawn)
                ; state side effects
                (do
                  (log "Switching from night to day and running morning routine")
                  (day-fn!)
                  :day)
                ; Don't change anything
                :night))}}))


(defn trans-sm! [sm m]
  (assoc sm :state
    (((:trans sm) (:state sm)) m)))


(defmacro try-times
  [i message & body]
  `(loop [i# ~i]
     (let [result#
             (when (> i# 0)
               (try
                 ~@body
                 (catch Exception e#
                   (println ~message ":" e#)
                   ::failed)))]
       (if (= result# ::failed)
         (recur (dec i#))
         result#))))


(defn safe-read!
  [a]
  (try-times 50 "Failed to read pin"
    (read! a)))


(defn init-state!
  [floor-btn roof-btn light-ain]
  (log "Initializing state")
  (cond
    (button/closed? floor-btn) :night
    (button/closed? roof-btn) :day
    (> (safe-read! light-ain) 0.15) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (log "Initializing -main")
  (let [floor-btn (button/button :P8 11 :normally-off)
        roof-btn  (button/button :P8 12 :normally-on)
        light-ain (ain 33)
        temp-ain  (ain 35)
        mtr-ctrl  (hb/hbridge [16 17 18] :header :P8)
        timer     (time-sm
                    (log-tr "Initial time state:" (init-state! floor-btn roof-btn light-ain))
                    (partial open-door! mtr-ctrl floor-btn roof-btn)
                    (partial close-door! mtr-ctrl floor-btn roof-btn))]

    (future
      (let [status-patterns {:running  [1500 3000] ; nice steady pulse
                             :warnings [1000 1000]
                             :errors   [100 50 100 750]}
            status-led (gpio :P8 14 :out)]
        (loop []
          (blink-led status-led (status-patterns @status))
          (recur))))

    (loop [timer timer]
      (Thread/sleep 1000)
      (let [light-level (safe-read! light-ain)]
        (log "Current levels:: light:" light-level "temp:" (safe-read! temp-ain))
        (recur (trans-sm! timer light-level))))))


(defn play []
  (println "ready to initialize")
  (let [g  (button/button :P8 11 :normally-off)
        a1 (ain 33)
        a2 (ain 35)]
    (println "initialized successfully")
    (doseq [_ (range)]
      (try
        (println "Reading light" (safe-read! a1))
        (println "Reading temp" (safe-read! a2))
        (println "Btn closed?" (button/closed? g))
        (println "")
        (catch Exception e
          (println "Had an exception")
          (println e)))
      (Thread/sleep 1000))))


