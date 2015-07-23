(ns strange-coop.components.satellite
  (:require [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <! close!]]
            [org.httpkit.client :as http]
            [strange-coop.components.config :as config]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]))

(defn basic-auth
  [satellite]
  (mapv (fn [k] (config/get-in-config satellite) [:satellite k]) [:username :password]))

(defn monitor-url
  ([statellite]
   (config/get-in-config satellite [:monitor-url]))
  ([satellite path]
   (str (monitor-url satellite) path)))

(defn handle-message
  [satellite {:keys [] :as message}]
  (http/post (monitor-url satellite "event")
             {:query-params message
              :basic-auth (basic-auth satellite)}))

(defn initiate-emit-loop!
  [satellite]
  (go-loop []
    (when-let [message (<! (get-in-config satellite [:channels :satellite-notify]))]
      (handle-message satellite message)
      (recur))))


(defn handle-incoming-message
  [satellite message]
  (

(defn check-messages
  [satellite]
  ;; Need to decide on the satellite api
  (let [message-url (str (get-in-config satellite [:satellite :url]) "messages")
        ;; Mocky.io
        ;message-url "http://www.mocky.io/v2/55af3083bf6e05f92366fb35"
        options  {:timeout 5000
                  :basic-auth (basic-auth satellite)
                  :query-params {:since @(:last-check satellite)}}]
    (go-loop []
      (http/get message-url
                options
                (fn [{:keys [status headers body error]}] ;; asynchronous response handling
                  (if error
                    ;; Not sure what to do here
                    (do (>!! (get-in satellite [:channels :log])
                             {:type ::check-messages-error
                              :message "Failed, exception in check-message"
                              :error error})
                        (recur))
                    (do (<!! (:recv-chan satellite) (json/parse-string body true))
                        )))))


(json/parse-string (:body @(http/get "http://www.mocky.io/v2/55af3083bf6e05f92366fb35")) true)

(defn inititate-recv-loop!
  [satellite]
  (let [polling-interval 5000]
    (async/thread
      (
    (go-loop []
      (

(defrecord Satellite [config channels last-check recv-chan]
  component/Lifecycle
  (start [component]
    (let [component (assoc component :last-check (atom 0))]
      (initiate-emit-loop! component)
      (initiate-recv-loop! component)
      component))

  (stop [component]
    ;; emit loop will kill automatically because the input channel will close! on channel lifecycle
    ;; Need a kill siwtch for recv still
    (assoc component
           :last-check nil)))


(defn new-satellite []
  (map->Satellite {}))


