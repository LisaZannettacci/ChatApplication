#!/bin/bash

javac -d classes -classpath .:classes src/Info_itf.java
javac -d classes -classpath .:classes src/InfoImpl.java
javac -d classes -classpath .:classes src/Hello.java
cd classes
jar cvf ../lib/Hello.jar Hello.class Info_itf.class
cd ..
javac -d classes -classpath .:classes src/HelloImpl.java
cd classes
jar cvf ../lib/HelloImpl.jar HelloImpl.class
cd ..
javac -d classes -cp .:classes:lib/Hello.jar:lib/HelloImpl.jar src/HelloServer.java
javac -d classes -cp .:classes:lib/Hello.jar src/HelloClient.java

export CLASSPATH=$CLASSPATH:$(pwd)/classes

pkill rmiregistry
sleep 1

CLASSPATH=lib/Hello.jar rmiregistry 6090 &