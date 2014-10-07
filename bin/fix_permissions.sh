#!/usr/bin/env sh
# 
# courtesy of https://github.com/cnobile2012/RobotControl/tree/master/contrib

your_user=$USER
addgroup gpio # use groupadd if you're on debian, I believe
su -c "usermod -a -G gpio $your_user" # add this group to your user
cp etc/80-gpio.rules /etc/udev/rules.d/
cp etc/fix_udev_gpio.sh /bin/
chmod +x /bin/fix_udev_gpio.sh

# Reboot 
echo "You will have to reboot for settings to take place (sudo reboot)"

