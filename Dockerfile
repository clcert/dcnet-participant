FROM ubuntu:latest
MAINTAINER Camilo Gomez <camilo@niclabs.cl>

ENV MSG 10 DIRECTORY '172.17.0.2'

RUN apt-get update && apt-get install -y software-properties-common \
										 git && \
	add-apt-repository -y ppa:webupd8team/java && \
	echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections && \
	apt-get update && apt-get install -y oracle-java8-installer && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /var/cache/oracle-jdk8-installer && \
	git clone https://fbb260392f7ae2834a1b495a7bae104443badc3b:x-oauth-basic@github.com/niclabs/collision_resolution_protocol.git
RUN	cd collision_resolution_protocol/ && \
	git pull && \
    ./gradlew build

CMD cd collision_resolution_protocol/ && git pull > /dev/null 2>&1 && ./gradlew -q run -PappArgs="['$MSG','$DIRECTORY']"
