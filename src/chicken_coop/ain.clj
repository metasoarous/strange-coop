(ns chicken-coop.ain
  (:require [chicken-coop.bbbpin :as bb :refer :all]
            [chicken-coop.gpio :as gpio]
            [chicken-coop.util :refer :all]))


(defn get-ain-from-pin [pin]
  (:name
    ((pinout-spec*) [:P9 pin])))


(defprotocol IAIN
  (read-raw! [this])
  (read! [this]))


(defrecord AIN [pin]
  IAIN
  (read-raw! [_]
    (->>
      (get-ain-from-pin pin)
      (str "/sys/devices/ocp.2/helper.14/")
      (read-bytes)
      (safe-int)))
  (read! [this]
    (/ (read-raw! this) 1800.0)))


(defn ain [pin]
  (AIN. pin))


