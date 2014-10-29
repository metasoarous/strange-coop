# chicken-coop

A Bonejure demo/pilot project!

I have four beautiful chickens, and have to wake up rather early every morning to let them out of their small coop.
This project is for an automatic, light sensor based automatic door shutter.
It requires a beaglebone black, and setup of a lein/clojure environment.

## Hardware

On the hardware side, I'm using
* an old 9V drill motor to raise/lower the door
* 3 relays (5V coil) for an H-bridge circuit (or a prebuilt H-bridge circuit, as long as it supports the specs)
* 3 transistors for triggering the relays from the 3V GPIO output voltage (switches on the sys 5V power)
* 2 buttons for detecting when the door is open/shut
* photoresistor and resistor for sensing light
* large, 6V battery
* Low dropout voltage regulator supporting 1.5A at 5V out
* 2 capacitors for voltage smoothing

Optionally (some in future)
* thermocouple, for monitoring temperature
* wifi, for notifications, etc
* camera, for monitoring and viz to count chicken laying

## Usage

TODO: Will be posting more information eventually on how to set up the physical and digital bits.

## Questions

* Should we require users of code to manually run an `activate-ain!` function in order to use ain pins? Or
  leave this in the fix script to run on BB reboot?
* What should go under `bonejure.core` versus other namespaces like `bonejure.gpio` or `bonejure.ain`?
* Should we rewrite the gpio pin code to use the mode7 column for extra robustness?
* Use future on init! so that we can init a whole bunch of pins at once and not have major lag.

## License

Copyright © 2014 Christopher Small

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

