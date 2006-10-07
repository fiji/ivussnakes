#!/bin/bash

cp Dijkstraheap.java source/
cp Vector2d.java source/
cp LiveWire_.java source/
cp ./ij/gui/ERoi.java ./source/ij/gui/

javac -cp ij.jar:tools.jar -d . ij/gui/ERoi.java Dijkstraheap.java Vector2d.java LiveWire_.java 

jar cf LiveWire_.jar Dijkstraheap.class LiveWire_.class LiveWire_\$1.class LiveWire_\$2.class LiveWire_\$3.class  LiveWire_\$4.class LiveWire_\$5.class PixelNode.class Vector2d.class ij/gui/ERoi.class ./source/* README.txt plugins.config

cp -f ./LiveWire_.jar /home/baggio/InCor/source/plugins
