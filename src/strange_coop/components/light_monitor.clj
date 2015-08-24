(ns strange-coop.components.light-monitor
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :as util :refer [log log-tr notify]]
            [strange-coop.bbbpin :as bb]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]))


(defn init-state!
  [floor-button roof-button light-sensor]
  (log "Initializing state")
  (cond
    (button/closed? floor-button) :night
    (button/closed? roof-button) :day
    (> (bb/safe-read! light-sensor) 0.15) :day
    :else :night))


(defn time-sm [component state day-fn! night-fn!]
  (let [dusk 0.03
        dawn 0.13]
    {:state state
     :trans
        {:day
            (fn [brightness]
              (if (< brightness dusk)
                ; state side effects
                (do
                  (notify component ::info "Switching from day to night and running evening routine")
                  (night-fn!)
                  :night)
                ; Don't change anything
                :day))
         :night
            (fn [brightness]
              (if (> brightness dawn)
                ; state side effects
                (do
                  (notify component ::info "Switching from night to day and running morning routine")
                  (day-fn!)
                  :day)
                ; Don't change anything
                :night))}}))


(defn trans-sm! [sm m]
  (assoc sm :state
    (((:trans sm) (:state sm)) m)))

(defrecord LightMonitor [config channels pins kill-chan]
  component/Lifecycle
  (start [component]
    (let [{:keys [floor-button roof-button light-sensor]} pins
          kill-chan (chan)
          ;; should hook up to config
          polling-interval 1000]
      (go-loop [light-state-machine
                (time-sm
                  component
                  (init-state! floor-button roof-button light-sensor)
                  (partial >!! (:door channels) :open)
                  (partial >!! (:door channels) :close))]
        (let [[message _] (async/alts! [kill-chan (async/timeout polling-interval)])]
          (if-not (= message :kill)
            (let [light-level (bb/safe-read! light-sensor)]
              (notify component ::measurement {:light-level light-level})
              (recur (trans-sm! light-state-machine light-level)))
            (notify component ::info "LightMonitor recieved kill signal; ending polling loop.")))))
    (assoc component :kill-chan kill-chan))

  (stop [component]
    (>!! kill-chan :kill)
    (async/close! kill-chan)
    (dissoc component :kill-chan)))


(defn create-light-monitor
  []
  (map->LightMonitor {}))

