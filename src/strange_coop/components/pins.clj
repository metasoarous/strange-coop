(ns strange-coop.components.notifier
  (:require [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <! close!]]
            [com.stuartsierra.component :as component]
            [strange-coop.bbbpin :as bb]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]))


(def pin-keys [:floor-button :roof-button :light-sensor :temp-sensor :motor-control])

(defrecord Pins [config]
  component/Lifecycle
  (start [component]
    (assoc component
           :floor-button  (button/button :P8 11 :normally-off)
           :roof-button   (button/button :P8 12 :normally-on)
           :light-sensor  (bb/ain 33)
           :temp-sensor   (bb/ain 35)
           :motor-control (hb/hbridge [16 17 18] :header :P8)))
  (stop [component]
    (apply dissoc component pin-keys)))

(defn create-pins []
  (map->Pins {}))

