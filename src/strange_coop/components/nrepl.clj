(ns strange-coop.components.nrepl
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]
            [strange-coop.util :refer [log log-tr]]))


(defrecord NRepl [config]
  component/Lifecycle
  (start [component]
    (log "Starting nrepl")
    (try
      (let [port   (get-in config [:config :nrepl-port] 1818)
            server (nrepl-server/start-server :port port)]
        (assoc component :nrepl server))
      (catch Exception e
        (log "ERROR: Failed to start nrepl")
        (.printStackTrace e)
        component)))

  (stop [component]
    (log "Stopping nrepl")
    (if-let [nrepl (:nrepl component)]
      (try
        (nrepl-server/stop-server (:nrepl component))
        (catch Exception e
          (log "ERROR: Failed to stop nrepl")
          (.printStackTrace e)))
      (log "(no-op; no nrepl running...)"))
    (assoc component :nrepl nil)))


(defn create-nrepl []
  (map->NRepl {}))

