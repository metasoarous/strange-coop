Note: this branch points to the code (roughly) as it stood when presented at ClojureWest.
As I work on a rewrite and additional features, I'll be keeping this branch here for posterity.

# A Strange Coop

I have four beautiful chickens, but I live in Seattle and there are lots of evil raccoons that like to eat
chickens.
I used to have to lock them in their coop every night, and let them out early every morning.
This was starting to kill my night life and beauty sleep, so I built a system to automate the task.

I built this system using a BeagleBone Black running this Clojure program to read from a light sensor and
control a motor to raise lower the door.
I'll be giving a presentation on this work at [ClojureWest 2015](http://clojurewest.org), and will link to the video here.
I'll also likely write up a blog post or two explaining the system in soewhat greater depth, with instructions
on how to get this set up yourself if you're so inclined.

Also featured in my talk is the introduction of a new Clojure API for hardware programming, called
[pin-ctrl](https://github.com/clj-bots/pin-ctrl).
I'll be eventually migrating this project to that API, and adding additional features, such as network
notifications.


## Features

* `./bin/fix_permissions.sh` - Sets up udev rules in `etc` such that the user has permissions to write to GPIO pins and read from AIN pins.
  Also actives AIN functionality on boot, though I wonder if we shouldn't force users to do this themselves.
* Sysfs based GPIO and AIN implementations, using shared protocols for `ReadablePin`, etc.
* Some abstraction code for working with H-bridges.
* Automatic `close!`ing of GPIO pins on shutdown hook (requires running with `lein trampoline` however).
* Specification of pin by physical header/pin pair (like `P8 11`) intead of sysfs pin #.
  (Should probably factor this out a bit though, so the GPIO implementation only knows about the sysfs pin, and the constructor does the work of translation)


## Hardware

On the hardware side, I'm using
* an old 9V drill motor to raise/lower the door
* 3 relays (5V coil) for an H-bridge circuit (or a prebuilt H-bridge circuit, as long as it supports the specs)
* 3 transistors for triggering the relays from the 3V GPIO output voltage (switches on the sys 5V power)
* 2 buttons for detecting when the door is open/shut
* photoresistor and resistor for sensing light
* large, 6V battery

Deprecated (wanted to use a battery to power the board, but ran out of voltage too quickly):
* Low dropout voltage regulator supporting 1.5A at 5V out
* 2 capacitors for voltage smoothing

Optionally:
* thermocouple, for monitoring temperature
* wifi/network, for notifications, etc (future)


## License

Copyright © 2015 Christopher Small

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

