(ns chicken-coop.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.tools.trace :as tr]
            [chicken-coop.util :refer :all]
            [chicken-coop.bbbpin :as bb :refer :all]
            [chicken-coop.hbridge :as hb]))


; Helper functions so I can keep track of whether doors are closed or open
(defn read-logger [p] (log/info "Pin state is:" (read! p)) p)
(def closed? (comp off? read-logger))
(def open? (comp on? read-logger))


(defmacro wait-till [test & body]
  `(loop []
     (if ~test
       (do
         ~@body)
       (recur))))


(defn close-door! [hb floor-btn]
  (log/info "Closing door")
  (hb/forward! hb)
  (wait-till (closed? floor-btn)
    (log/info "Stopping door")
    (hb/stop! hb)))


(defn open-door! [hb roof-btn]
  (log/info "Opening door")
  (hb/reverse! hb)
  (wait-till (closed? roof-btn)
    (log/info "Stopping door")
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
                  (log/info "Switching from day to night and running morning routine")
                  (night-fn!)
                  :night)
                ; Don't change anything
                :day))
         :night
            (fn [brightness]
              (if (> brightness dawn)
                ; state side effects
                (do
                  (log/info "Switching from night to day and running morning routine")
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
                   (log/error ~message ":" e#)
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
  (log/info "Initializing state")
  (cond
    (closed? floor-btn) :night
    (closed? roof-btn) :day
    (> (safe-read! light-ain) 0.15) :day
    :else :night))


(defn check [] (println "compiled!"))


(defn -main []
  (log/info "Initializing -main")
  (let [floor-btn (gpio :P8 11 :in)
        roof-btn  (gpio :P8 12 :in)
        light-ain (ain 33)
        temp-ain  (ain 35)
        mtr-ctrl  (hb/hbridge [16 17 18] :header :P8)
        timer     (time-sm
                    (init-state! floor-btn roof-btn light-ain)
                    (partial close-door! mtr-ctrl floor-btn)
                    (partial open-door! mtr-ctrl roof-btn))]
    (setup-shutdown-hook!
      (fn []
        (log/info "Running shutdown hook")
        (doseq [p (tr/trace :SHUTDOWN_PINS
                    (concat
                      [floor-btn
                       roof-btn]
                      (for [x [:power :-pin :+pin]]
                            (mtr-ctrl x))))]
          (close! p))))
    (loop [time-sm time-sm]
      (Thread/sleep 1000)
      (let [light-level (safe-read! light-ain)]
        (log/info "Current light-level:" light-level)
        (recur (trans-sm! timer light-level))))))


(defn play []
  (println "ready to initialize")
  (let [g  (gpio :P8 12 :in)
        a1 (ain 33)
        a2 (ain 35)]
    (setup-shutdown-hook!
      (fn [] (close! g)))
    (println "Pin settings" ((pinout-spec*) [:P8 11]))
    (println "initialized successfully")
    (doseq [_ (range)]
      (try
        (println "Reading light" (safe-read! a1))
        (println "Reading temp" (safe-read! a2))
        (println "Reading btn" (read! g))
        (println "")
        (catch Exception e
          (println "Had an exception")
          (println e)))
      (Thread/sleep 1000))))


