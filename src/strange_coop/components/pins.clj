(ns strange-coop.components.pins
  (:require [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <! close!]]
            [com.stuartsierra.component :as component]
            [strange-coop.bbbpin :as bb]
            [strange-coop.components.config :as config]
            [strange-coop.button :as button]
            [strange-coop.util :as util :refer [log]]
            [strange-coop.hbridge :as hb]))


(def pin-keys [:floor-button :roof-button :light-sensor :temp-sensor :motor-control])

(defrecord Pins [config]
  component/Lifecycle
  (start [component]
    (log "Starting pins")
    (with-bindings {#'bb/*mock?* (config/get-in-config component [:mock])}
      (assoc component
             :floor-button  (button/button :P8 11 :normally-off)
             :roof-button   (button/button :P8 12 :normally-on)
             :light-sensor  (bb/ain 33)
             :temp-sensor   (bb/ain 35)
             :motor-control (hb/hbridge [16 17 18] :header :P8)
             :status-led    (bb/gpio :P8 14 :out))))

  (stop [component]
    (log "Stopping pins")
    (apply dissoc component pin-keys)))

(defn create-pins []
  (map->Pins {}))

