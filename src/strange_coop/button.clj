(ns strange-coop.button
  (:require [strange-coop.bbbpin :as bb]))

(defprotocol IButton
  (open? [this])
  (closed? [this]))

(defrecord NormallyOnButton [gpio-pin]
  IButton
  (closed? [_]
    (bb/off? gpio-pin))
  (open? [_]
    (bb/on? gpio-pin)))

(defrecord NormallyOffButton [gpio-pin]
  IButton
  (closed? [_]
    (bb/on? gpio-pin))
  (open? [_]
    (bb/off? gpio-pin)))

(defn button [pin-header pin-n direction]
  (assert (#{:normally-on :normally-off} direction))
  (let [gpio-pin (bb/gpio pin-header pin-n :in)]
    (case direction
      :normally-on (NormallyOnButton. gpio-pin)
      :normally-off (NormallyOffButton. gpio-pin))))

