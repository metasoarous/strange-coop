(ns strange-coop.core
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [clojure.tools.trace :as tr]
            [strange-coop.util :refer [log log-tr]]
            [strange-coop.bbbpin :as bb :refer :all]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]
            [strange-coop.components [config :as config :refer [create-config]]
                                     [channels :refer [create-channels]]
                                     [door :refer [create-door]]
                                     [light-monitor :refer [create-light-monitor]]
                                     [nrepl :refer [create-nrepl]]
                                     [pins :refer [create-pins]]
                                     [satellite :refer [create-satellite]]]))


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

      ;(let [status-patterns {:running  [1500 3000] ; nice steady pulse
                             ;:warnings [1000 1000]
                             ;:errors   [100 50 100 750]}
            ;status-led (gpio :P8 14 :out)]
        ;(loop []
          ;(blink-led status-led (status-patterns @status))
          ;(recur))))

(def system nil)

(defn create-system [config-overrides]
  (component/system-map
    :config        (create-config config-overrides)
    :channels      (component/using (create-channels)      [:config])
    :pins          (component/using (create-pins)          [:config])
    :door          (component/using (create-door)          [:config :channels :pins])
    :light-monitor (component/using (create-light-monitor) [:config :channels :pins])
    :satellite     (component/using (create-satellite)     [:config :channels])
    :nrepl         (component/using (create-nrepl)         [:config])))

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
  (start {}))


