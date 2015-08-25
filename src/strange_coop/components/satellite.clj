(ns strange-coop.components.satellite
  (:require [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <! close!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :as util :refer [log]]
            [clojure.edn :as edn]
            [gniazdo.core :as ws]
            [base64-clj.core :as base64]
            [com.stuartsierra.component :as component]))


(defn basic-auth
  [component]
  (let [{:keys [username password]} (get-in-config component [:satellite :credentials])]
    (str "Basic " (base64/encode (str username ":" password)))))

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


(defn create-socket! [component]
  (let [log-chan (-> component :channels :log)]
    (ws/connect
      (str "wss://" (get-in-config component [:satellite :url]) "/socket")
      :headers {"Authorization" (basic-auth component)}
      :on-receive (comp (partial handle-incoming-message component) edn/read-string)
      :on-connect (fn [_] (>!! log-chan {:type ::info :message "Satellite initialized"}))
      ;; Should add reconnect logic...
      :on-error (fn [e] (>!! log-chan {:type ::error :message "Satellite error" :error e}))
      :on-close (fn [status-code message] (>!! log-chan {:type ::warning
                                                       :message (str "Satellite connection closed: " message)
                                                       :status-code status-code})))))
  
(defrecord Satellite [config channels socket]
  component/Lifecycle
  (start [component]
    (log "Starting satellite socket")
    (try
      (let [socket (create-socket! component)
            component (assoc component :socket socket)]
        (initiate-emit-loop! component)
        component)
      (catch Exception e
        (log "ERROR: Satellite socket failed to launch")
        (.printStackTrace e)
        component)))

  (stop [component]
    (log "Stopping satellite")
    (if socket
      (try
        (ws/close socket)
        (catch Exception e
          (log "ERROR: Failed to stop satellite socket")
          (.printStackTrace e)))
      (log "(no-op; no satellite running)"))
    ;; Note that emit loop will close when channels do
    (assoc component :socket nil)))

(defn create-satellite []
  (map->Satellite {}))


