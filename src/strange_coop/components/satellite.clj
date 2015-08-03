(ns strange-coop.components.satellite
  (:require [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <! close!]]
            [strange-coop.components.config :as config]
            [clojure.edn :as edn]
            [gniazdo.core :as ws]
            [com.stuartsierra.component :as component]))

(defn initiate-emit-loop!
  [satellite]
  (go-loop []
    (when-let [message (<! (get-in satellite [:channels :satellite-notify]))]
      (ws/send-msg (:socket satellite) (str message))
      (recur))))

(defmulti handle-incoming-message
  "Handles an incoming message. Warning: satellite passed here is in closer before the socket was added to
  the event satellite object. So no implementations of this multimethods should try to use (:socket channel)."
  (fn [satellite message] (:type message)))

(defmethod handle-incoming-message :command
  [satellite message]
  (>!! (-> satellite :channels :door) (:message message)))
  
(defrecord Satellite [config channels socket]
  component/Lifecycle
  (start [component]
    (let [log-chan (:log channels)
          socket (ws/connect
                   (str "wss://" (get-in config [:satellite :url]) "/socket")
                   :on-receive (comp (partial handle-incoming-message component) edn/read-string)
                   :on-connect (fn [_] (>!! log-chan {:type ::info :message "Satellite initialized"}))
                   ;; Should add reconnect logic...
                   :on-error (fn [e] (>!! log-chan {:type ::error :message "Satellite error" :error e}))
                   :on-close (fn [status-code message] (>!! log-chan {:type ::warning
                                                                    :message (str "Satellite connection closed: " message)
                                                                    :status-code status-code})))
          component (assoc component :socket socket)]
      (initiate-emit-loop! component)
      component))

  (stop [component]
    (ws/close socket)
    ;; Note that emit loop will close when channels do
    (assoc component :socket nil)))

(defn create-satellite []
  (map->Satellite {}))


