MIDISpy
=======

a MIDI bridge for snooping/sniffing MIDI data in Java

This application allows you to spy on MIDI data exchanged between two applications or devices.

It works by building a bridge/relay for the data to display the message contents before transmitting it further.

The setup is very flexible as you can use different input and output ports for the two devices in question (but this also means you have to deal with Javas confusing way of showing them...).

I wrote this to analyze the SysEx message exchanged between a Akai LPK25 Keyboard and the proprietary software that is used to program the presets (that luckily runs in Wine).

To do something similar you setup MIDISpy to bridge/relay the keyboard via a virtual midi device (snd-virmidi). If the application then talks to the virtual device, the keyboard will receive all data and all answers are sent back to the application. In between all messages are shown as hex dumps.
