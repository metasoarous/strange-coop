(ns chicken-coop.core
  (:require [clojure.java.io :as io]
            ;[clojure.tools.logging :as log]
            [clojure.tools.trace :as tr]
            [chicken-coop.util :refer :all]
            [chicken-coop.bbbpin :as bb :refer :all]
            [chicken-coop.hbridge :as hb]))


(defprotocol IButton
  (open? [this])
  (closed? [this]))

(defrecord NormallyOnButton [gpio-pin]
  IButton
  (closed? [_]
    (off? gpio-pin))
  (open? [_]
    (on? gpio-pin)))

(defrecord NormallyOffButton [gpio-pin]
  IButton
  (closed? [_]
    (on? gpio-pin))
  (open? [_]
    (off? gpio-pin)))

(defn button [pin-header pin-n direction]
  (assert (#{:normally-on :normally-off} direction))
  (let [gpio-pin (gpio pin-header pin-n :in)]
    (case direction
      :normally-on (NormallyOnButton. gpio-pin)
      :normally-off (NormallyOffButton. gpio-pin))))


(defmacro wait-till [test & body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))


(defn close-door! [hb floor-btn]
  (log "Closing door")
  (hb/forward! hb)
  (wait-till (closed? floor-btn)
    (log "Stopping door")
    (Thread/sleep 500)
    (hb/stop! hb)))


(defn open-door! [hb roof-btn]
  (log "Opening door")
  (hb/reverse! hb)
  (wait-till (closed? roof-btn)
    (log "Stopping door")
    (hb/stop! hb)))


(defn time-sm [state day-fn! night-fn!]
  (let [dusk 0.12
        dawn 0.20]
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
    (closed? floor-btn) :night
    (closed? roof-btn) :day
    (> (safe-read! light-ain) 0.15) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (log "Initializing -main")
  (let [floor-btn (button :P8 11 :normally-off)
        roof-btn  (button :P8 12 :normally-on)
        light-ain (ain 33)
        temp-ain  (ain 35)
        mtr-ctrl  (hb/hbridge [16 17 18] :header :P8)
        timer     (time-sm
                    (log-tr "Initial time state:" (init-state! floor-btn roof-btn light-ain))
                    (partial open-door! mtr-ctrl roof-btn)
                    (partial close-door! mtr-ctrl floor-btn))]
    (loop [timer timer]
      (Thread/sleep 1000)
      (let [light-level (safe-read! light-ain)]
        (log "Current light-level:" light-level)
        (recur (trans-sm! timer light-level))))))


(defn play []
  (println "ready to initialize")
  (let [g  (button :P8 11 :normally-off)
        a1 (ain 33)
        a2 (ain 35)]
    (println "initialized successfully")
    (doseq [_ (range)]
      (try
        (println "Reading light" (safe-read! a1))
        (println "Reading temp" (safe-read! a2))
        (println "Btn closed?" (closed? g))
        (println "")
        (catch Exception e
          (println "Had an exception")
          (println e)))
      (Thread/sleep 1000))))


