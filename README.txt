SoapQuest is hereby released under the Apache 2.0 License: http://www.apache.org/licenses/LICENSE-2.0.html

SOAPQUEST 1045
"You can't use a rabbit on a rabbit."

SoapQuest 1024 is a small game I decided to write after reading "20 Open World Games" on Gamasutra and coming across "Adventure".

It's a very simple tile-based game written in 1024 lines of Java. You can wander around the place, picking things up and using them on other things by walking into them. Day and night happen, and at night you need light sources to see properly.

Play around. Try to make: fire, soap, dinner, a house... Civilisation!

You can save the game too, and go back to it later. If you don't like what you've wrought, just delete your save.csv file.

Yet it's fully data-driven, based on a simple CSV format, and has things like wandering treants and a day/night cycle with light calculations.

I'm releasing this as open source in the hope someone will find joy in toying with it, and maybe extending it to something grander. Networking sounds like quite a fun thing to do to it.

The game is meant to be very easy to change. Have a look at the items.csv/behaviours.csv/transformations.csv/map.csv files, which contain the game rules. Also have a look at the included source code, which is under the BSD licence and hosted on Google Code at http://code.google.com/p/soapquest/, so do contribute.

Play around with the game rules. For example, go into behaviours.csv and add the following line:
interact, rabbit, tree, rabbit, rabbit, 0
This means that every time a rabbit touches a tree, it turns it into another rabbit.

Or:
light, tree, 3

Which adds a faint glow to all trees. Or try negative numbers for light. Or use:
seek, rabbit, axe
To make rabbit unnaturally attracted to axes!

You can add new item types too, by adding lines to items.csv. Can you make an Evil Glowing Wand Of Turning Everything Into A Pile Of Ash?