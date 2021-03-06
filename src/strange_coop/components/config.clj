(ns strange-coop.components.config
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [strange-coop.util :as util :refer [log]]))

(defn deep-merge
  "Like merge, but merges maps recursively."
  [& maps]
  (if (every? #(or (map? %) (nil? %)) maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn deep-merge-in
  [m path & maps]
  (apply update-in m path deep-merge maps))

(def defaults
  {:nrepl-port 9999
   :satellite {:url "http://localhost:3000/"
               :credentials {:username "bobjones" :password "xyz"}}
   :channels {:buffer-size 100}
   :day-state-machine {:day-threshold   0.13
                       :night-threshold 0.03}
   :pins  {:motor-hb     {:pins   [16 17 18]
                          :header :P8}
           :light-sensor [:P9 33]
           :temp-sensor  [:P9 35]
           :floor-button [:P8 11 :normally-off]
           :roof-button  [:P8 12 :normally-on]}
   :status-led {:status-patterns {:running  [1500 3000]
                                  :warnings [1000 1000]
                                  :errors   [100 50 100 750]}}})

(defn ->long [x]
  (Long/parseLong x))

(def rules
  "Mapping of env keys to parsing options"
  {:nrepl-port         {:parse ->long}
   :satellite-url      {:path [:satellite :url]}
   :satellite-username {:path [:satellite :username]}
   :satellite-password {:path [:satellite :password]}})

(defn get-environ-config [rules env]
  (reduce
    (fn [config [name {:keys [parse path]}]]
      (if-let [env-var-val (get env name)]
        (assoc-in config (or path [name]) ((or parse identity) env-var-val))
        config))
    {}
    rules))

(defrecord Config [overrides config]
  component/Lifecycle
  (start [component]
    (log "Starting config component")
    (deep-merge-in component [:config]
                   defaults
                   (get-environ-config rules env)
                   overrides))
  (stop [component]
    (log "Stopping config component")
    (assoc component :config nil)))
 
(defn create-config
  [overrides]
  (map->Config {:overrides overrides}))

(defn get-in-config
  "Little helper for making getting config variables less awkward"
  ([component path]
   (get-in-config component path nil))
  ([component path default]
   (get-in component (into [:config :config] path) default)))


