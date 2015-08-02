(ns strange-coop.components.door
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :refer [log log-tr]]
            [strange-coop.bbbpin :as bb]
            [strange-coop.button :as button]
            [strange-coop.hbridge :as hb]))


(defmulti handle-message
  (fn [component message] message))

;; Is it better to have an explicit kill-chan here? Or to just let closing of the input door chan kill the go
;; loop?
(defrecord Door [config pins channels]
  component/Lifecycle
  (start [component]
    (go-loop []
      (when-let [message (<! (:door channels))]
        (handle-message component message)
        (recur)))
    component)

  (stop [component]
    ;; We could still kill the chan here, but is this the right place to control that?
    component))

