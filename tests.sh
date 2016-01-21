#!/bin/bash
#open Terminal and run command
for ((i=1; i<=$1; i++))
do
	#xfce4-terminal -e './gradlew run -PappArgs=['$RANDOM','$1']'
	./gradlew run -PappArgs=[$RANDOM,$1] > logs/log$i.txt &
done