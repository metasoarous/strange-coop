(ns strange-coop.components.channels
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :refer [log log-tr]]))


(defrecord Channels [config channels]
  component/Lifecycle
  (start [component]
    (log "Initializing channels")
    (let [buffer-size     (config/get-in-config component [:channels :buffer-size])
          notif-chan      (chan (async/sliding-buffer buffer-size))
          notif-mult      (async/mult notif-chan)
          log-chan        (chan (async/sliding-buffer buffer-size))
          sat-notif-chan  (chan (async/sliding-buffer buffer-size))
          status-led-chan (chan (async/sliding-buffer buffer-size))
          door-chan       (chan)]
      (doseq [c [log-chan sat-notif-chan status-led-chan]]
        (async/tap notif-mult c))
      (assoc component
             :channels
             {:notify            notif-chan
              :log               log-chan
              :satellite-notify  sat-notif-chan
              :status-led-notify status-led-chan
              :door              door-chan})))

  (stop [component]
    (log "Dropping channels")
    (doseq [[k c] (:channels component)]
      (async/close! c))
    (assoc component :channels nil)))


(defn new-channels
  []
  (map->Channels {}))


