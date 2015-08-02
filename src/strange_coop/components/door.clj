(ns strange-coop.components.door
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [chan <!! >!! go go-loop >! <!]]
            [strange-coop.components.config :as config :refer [get-in-config]]
            [strange-coop.util :as util :refer [log log-tr notify]]
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


(defn- max-time-up? [max-time start-time]
  (> (- (System/currentTimeMillis) start-time) max-time))

(defmethod handle-message :open
  [component message]
  (let [{:keys [motor-control roof-button]} (:pins component)
        notify-chan (:notify (:channels component))
        ;; Various settings; should maybe be in config
        max-time-secs 30
        max-time-ms   (* max-time-secs 1000)
        start-time    (System/currentTimeMillis)]
    (notify component ::info "Opening door")
    (hb/reverse! motor-control)
    (loop []
      (cond
        (button/closed? roof-button)
          (>!! {:type ::info :message "Door opened."})
        (max-time-up? max-time-ms start-time)
          (>!! {:type ::error :message "Unable to open door."})
        :else (recur)))
    (hb/stop! motor-control)))


(defmethod handle-message :close
  [component message]
  (notify component ::info "Initiating close-door! sequence")
  (let [;; Various settings
        door-close-wait 500 ; time to wait after door closes for latches to lock
        n-retries       3
        max-time-secs   90
        max-time-ms     (* max-time-secs 1000)
        ;; Getting into the meat of it
        {:keys [motor-control floor-button roof-button]} (:pins component)
        lower-with-log  (fn []
                          (notify component ::info "Closing door")
                          (hb/forward! motor-control))
        start-time      (System/currentTimeMillis)]
    ;; Start lowering door
    (lower-with-log)
    (loop [tries 0]
      (cond
        ; Standard closing procedure
        (button/closed? floor-button)
        (do (notify component ::info "Door closed; Stopping door")
            (Thread/sleep door-close-wait))
        ; The final try of the above
        (and (button/closed? roof-button) (> tries n-retries))
        (do (notify component ::error "Hit roof with max number of retries. Attempting to close without worrying about btn.")
            (hb/reverse! motor-control)
            (Thread/sleep 8000)) ; exit
        ; The door hit the roof before the floor button triggered; Reverse and try again.
        (button/closed? roof-button)
        (do (notify component ::warning "Hit roof; reeling back in and trying again.")
            (hb/reverse! motor-control)
            (Thread/sleep 1000) ; make sure door lets go of button
            (util/wait-till (or (button/closed? roof-button)
                                (max-time-up? max-time-ms start-time))
              ;; should clean up this part of the logic so it's more consistent with the above clause
              ;; right now doesn't give it the 8 seconds... XXX
              (notify component ::warning "Reeling complete; putting in forward again.")
              (lower-with-log)
              (Thread/sleep 1000)) ; make sure does lets go of button
            (recur (inc tries)))
        ; After a certain amount of time, just give up
        (max-time-up? max-time-ms start-time)
        (notify component ::error "Maxed out on time; Shutting down.")
        ; Run the loop again
        :else               (recur tries)))
    ; Last thing, make sure to stop once out of loop
    (hb/stop! motor-control)))

