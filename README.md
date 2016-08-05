# collision_resolution_protocol [![Build Status](https://travis-ci.org/niclabs/collision_resolution_protocol.svg?branch=master)](https://travis-ci.org/niclabs/collision_resolution_protocol)

Java program that simulates nodes participating on a DC-NET room sending messages (one message per session). 

If two or more nodes send a message in the same session, a collision is produced and this program runs a collision resolution protocol in order to solve it.

## System Requirements

* [Java 8](http://www.oracle.com/technetwork/java/index.html)

## Instructions

This program simulates a node running in a machine connected via LAN to another machines that simulates other nodes, all participating in a room.

In order to this nodes to work and start communicating with each other, one machine within the LAN must run an instance of [directory_dcnet](https://github.com/niclabs/directory_dcnet) that will work to send the ip address and index of every node running the protocol to the entire room.
    
### Run nodes separately

* In order to start a session, all the machines (nodes) must run the following command:

    ```./participant.sh -m <"message"> -d <directoryIP> -c <cheaterMode>```

### Using Docker

* Also you can use [docker](https://www.docker.com/) in order to run a node, using the following commands: (first build and create the image, and then running this image)

    ```docker build -t dcNetNode .```
    
    ```docker run --env MSG=<message> --env DIRECTORY=<directoryIP> dcNetNode```
