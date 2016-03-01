#!/bin/bash
for ((i=1; i<=$1; i++))
do
	./gradlew run -PappArgs=[$RANDOM,$1] > logs/log$i.txt &
	pids[$i]=$!
done

for pid in ${pids[*]}
do
	wait $pid
done