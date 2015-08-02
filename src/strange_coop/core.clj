(ns strange-coop.core
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [clojure.tools.trace :as tr]
            [strange-coop.util :refer [log log-tr]]
            [strange-coop.bbbpin :as bb :refer :all]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]))




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


(def system nil)

(def create-system [config-overrides]
  (component/system-map
    :config   (create-config config-overrides)
    :pins     (component/using (create-pins) [:config])
    :channels (component/using (create-channels) [:config])
    :nrepl    (component/using (create-nrepl) [:config])))

(defn start
  ([config-overrides]
   (when-not system
     (alter-var-root #'system (constantly (create-system config-overrides))))
   ;; Should we add the shutdown hook here?
   (alter-var-root #'system component/start))
  ([] (start {})))


(defn stop
  []
  (alter-var-root #'system component/stop))

(defn restart
  "Restart (stop then start) the system with whatever configurations were running in the previous
  system, except for overrides as specified by the optional config-overrides arg (works by calling
  deep-merge on the current config or {} if none with config-overrides, and passing that as
  config-overrides to the config component)."
  ([config-overrides]
   (let [current-config (or (:config (:config system)) {})
         new-config-overrides (config/deep-merge current-config config-overrides)]
     (stop)
     (start new-config-overrides)))
  ([] (restart {})))


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


