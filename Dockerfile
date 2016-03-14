FROM ubuntu:latest
MAINTAINER Camilo Gomez <camilo@niclabs.cl>

ENV MSG 10 DIRECTORY '172.17.0.2' NONPROB 'true'

RUN apt-get update && apt-get install -y software-properties-common \
										 git && \
	add-apt-repository -y ppa:webupd8team/java && \
	echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections && \
	apt-get update && apt-get install -y oracle-java8-installer && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /var/cache/oracle-jdk8-installer && \
	git clone https://github.com/niclabs/collision_resolution_protocol.git
RUN	cd collision_resolution_protocol/ && \
	git pull && \
    git checkout lantest && \
	./gradlew build

CMD cd collision_resolution_protocol/ && git pull && ./gradlew run -PappArgs="[$MSG,'$DIRECTORY','$NONPROB']"
#CMD ["collision_resolution_protocol/gradlew","run","-PappArgs=[$MSG,$N,$INDEX]"]