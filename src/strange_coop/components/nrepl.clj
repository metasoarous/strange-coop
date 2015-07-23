(ns strange-coop.components.nrepl
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]
            [strange-coop.util :refer [log log-tr]]))


(defrecord NRepl [config]
  component/Lifecycle
  (start [component]
    (log "Starting nrepl")
    (let [port   (get-in config [:config :nrepl-port] 1818)
          server (nrepl-server/start-server :port port)]
      (assoc component :nrepl server)))

  (stop [component]
    (log "Stopping nrepl")
    (nrepl-server/stop-server (:nrepl component))
    (assoc component :nrepl nil)))


(defn nrepl []
  (map->NRepl {}))

