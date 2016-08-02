#!/bin/bash

function usage() {
	echo "Usage: $0 [-m \"<message>\"] [-d <directory_ip>] [-c <cheater_mode>]";
	exit 2
}

while getopts ":m:d:c:" o;
do
	case "${o}" in
		m)
			message=${OPTARG}
			;;
		d)
			directory_ip=${OPTARG}
			;;
		c)
			cheater_mode=${OPTARG}
			;;
		*)
			usage
			;;
	esac
done

if [ -z "${directory_ip}" ] || [ -z "${cheater_mode}" ]
then
	usage
fi 

java -jar "$(pwd)/build/libs/collision_resolution_protocol-all-1.0-SNAPSHOT.jar" "${message}" ${directory_ip} ${cheater_mode}