#!/bin/bash
for ((i=1; i<=$1; i++))
do
	coin=$RANDOM
	let "coin %= 3"
	if [ $coin -eq 0 ]; then
  		./gradlew run -PappArgs=[0,$1] > logs/log$i.txt &
	else
		./gradlew run -PappArgs=[$RANDOM,$1] > logs/log$i.txt &
	fi
	pids[$i]=$!
done

for pid in ${pids[*]}
do
	wait $pid
done