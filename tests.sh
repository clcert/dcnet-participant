#!/bin/bash
#open Terminal and run command
for ((i=1; i<=$1; i++))
do
	./gradlew run -PappArgs=[$RANDOM,$1] > logs/log$i.txt &
done