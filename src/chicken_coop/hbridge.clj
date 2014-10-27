(ns chicken-coop.hbridge
  (:require [chicken-coop.bbbpin :as bb]))


(defprotocol HBridgeable
  (forward! [this])
  (reverse! [this])
  (stop! [this]))


(defmacro with-sleeps
  "Adds calls of (Thread/sleep n-ms) inbetween each of the forms in body."
  [n-ms & body]
  (let [sleep-form `(Thread/sleep ~n-ms)
        first-form (first body)
        rest-forms (map (fn [form] `(do ~sleep-form ~form)) (rest body))]
    `(do
       ~first-form
       ~@rest-forms)))


(defrecord HBridge4Pin [+for -for +rev -rev]
  HBridgeable
  (forward! [this]
    ; Wait a tad between calls to prevent short
    ; XXX - should make this wait configurable via assoc opt; or could use a binding
    (with-sleeps (or (:ms-wait this) 30)
      ; Reverse circuit off
      (bb/writes! [+rev -rev] :off)
      ; Turn forward circuit on
      (bb/writes! [+for -for] :on)))
  (reverse! [this]
    (with-sleeps (or (:ms-wait this) 30)
      ; similarly as forward! ...
      (bb/writes! [+for -for] :off)
      (bb/writes! [+rev -rev] :on)))
  (stop! [_]
    (bb/writes! [+for -for +rev -rev] :off)))


(defrecord HBridge3Pin [power +pin -pin]
  HBridgeable
  (forward! [this]
    (with-sleeps (or (:ms-wait this) 20)
      (bb/write! power :off)
      (bb/writes! [+pin -pin] :off)
      (bb/write! power :on)))
  (reverse! [this]
    (with-sleeps (or (:ms-wait this) 20)
      (bb/write! power :off)
      (bb/writes! [+pin -pin] :on)
      (bb/write! power :on)))
  (stop! [this]
    (with-sleeps (or (:ms-wait this) 20)
      (bb/write! power :off)
      (bb/writes! [+pin -pin] :off))))


(defrecord HBridge2Pin [power pin]
  HBridgeable
  (forward! [this]
    (with-sleeps (or (:ms-wait this) 10)
      (bb/write! power :off)
      (bb/write! pin :off)
      (bb/write! power :on)))
  (reverse! [this]
    (with-sleeps (or (:ms-wait this) 10)
      (bb/write! power :off)
      (bb/write! pin :on)
      (bb/write! power :on)))
  (stop! [this]
    (with-sleeps (or (:ms-wait this) 10)
      (bb/write! power :off)
      (bb/write! pin :off))))


(defn hbridge
  "Create an H-Bridge record, dispatching on the number of pin specs passed in as `pins`.:

  * 4 - The most typical setup, using 4 simple on/off SPST relays. Order of `pins` is:
        +forward +reverse -forwrad -reverse

  * 3 - Using 2 SPDT relays for polarity, and one relay for power
        power +pin -pin

  * 2 - Using a single DPDT relay for polarity, and one relay for power
        power pin
  
  Each pin spec (power, pin, +forward, etc) should either ve a vector (eg. [:P8 11]), or just a pin int, as
  long as :header is specified in opts hash. Option `ms-wait` specifies how much time to leave between switches
  to avoid shortages."
  [pins & {:keys [ms-wait header] :as opts}]
  (let [constr ({4 ->HBridge4Pin
                 3 ->HBridge3Pin
                 2 ->HBridge2Pin} (count pins))]
    (-> constr
        (apply (map
                 (fn [pin-spec]
                   (let [[header pin] (if (coll? pin-spec)
                                        pin-spec
                                        [header pin-spec])]
                     (bb/gpio header pin :out)))
                 pins))
        (assoc :ms-wait ms-wait))))


