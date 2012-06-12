#!/bin/sh
# Figure out location of all the source files.
find src -name *.java > tmp/sourcefiles
# Compile.
javac -d build/classes -sourcepath src @tmp/sourcefiles
# Clean up temporary / old files.
rm tmp/sourcefiles
# Jar up the classes.
cd build/classes
jar cmf ../manifest.txt ../../Minigame.jar .
cd ../..
#java -jar <PROJECTNAME>.jar