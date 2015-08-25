(ns strange-coop.core
  (:require [com.stuartsierra.component :as component]
            [strange-coop.util :as util]
            [strange-coop.components [config :as config :refer [create-config]]
                                     [channels :refer [create-channels]]
                                     [door :refer [create-door]]
                                     [light-monitor :refer [create-light-monitor]]
                                     [nrepl :refer [create-nrepl]]
                                     [pins :refer [create-pins]]
                                     [satellite :refer [create-satellite]]]))


(defonce system nil)

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
  (util/log "Initializing -main")
  (start {}))


