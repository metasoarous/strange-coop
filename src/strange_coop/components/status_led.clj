(ns strange-coop.components.status-led
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :as util :refer [log log-tr notify]]
            [strange-coop.bbbpin :as bb]))


(defn blink-led
  "Given an led GPIO pin, blink for the given pattern, specified as a single ms value - or vector of such values -
  to wait between led switching"
  [led-pin pattern]
  (if (number? pattern)
    (recur led-pin [pattern])
    (doseq [t pattern]
      (bb/toggle! led-pin)
      (Thread/sleep t))))

(defn initialize-status-monitoring!
  [{:keys [channels status] :as component}]
  (go-loop []
    (let [message (<! (:status-led-notify channels))
          m-type (:type message)
          new-status (case [@status (util/dequalify-keyword m-type)]
                       [:running :warning] :warnings
                       [:running :error] :errors
                       [:warning :error] :errors
                       nil)]
      (when new-status
        (swap! status (constantly new-status)))
      (when message
        (recur)))))

(defn initialize-status-led-loop!
  [{:keys [config pins status] :as component}]
  (go-loop []
    (when-let [status-pattern (get-in config [:status-led :status-patterns @status])]
      (doseq [t status-pattern]
        (bb/toggle! (:status-led pins))
        (<! (async/timeout t)))
      (recur))))

(defrecord StatusLed [config channels pins status]
  component/Lifecycle
  (start [component]
    (log "Starting status led")
    (let [status (atom :running)
          component (assoc component :status status)]
      (initialize-status-monitoring! component)
      (initialize-status-led-loop! component)
      ;; Return component with status atom
      component))
               
  (stop [component]
    (log "Stopping status led")
    ;; This will trigger a kill on the status led loop, and make sure led is off
    (reset! status nil)
    (bb/off! (:status-led pins))))

;(let [status-patterns {:running  [1500 3000] ; nice steady pulse
                       ;:warnings [1000 1000]
                       ;:errors   [100 50 100 750]}

(defn create-status-led []
  (map->StatusLed {}))

