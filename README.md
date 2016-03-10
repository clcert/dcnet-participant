# collision_resolution_protocol [![Build Status](https://travis-ci.org/niclabs/collision_resolution_protocol.svg?branch=master)](https://travis-ci.org/niclabs/collision_resolution_protocol)

Java program that simulates nodes participating on a DC-NET room sending messages (one message per session). 

If two or more nodes send a message in the same session, a collision is produced and this program runs a collision resolution protocol in order to solve it.

## Instructions

This program simulates a node running in a machine connected via LAN to another machines that simulates other nodes, all participating in a room.

In order to this nodes to work and start communicating with each other, one machine within the LAN must run an instance of [directory_dcnet](https://github.com/niclabs/directory_dcnet) that will work to send the ip address and index of every node running the protocol to the entire room.
    
### Run nodes separately

* In order to start a session, all the machines (nodes) must run the following command:

    ```./gradlew run -PappArgs=[<message>,<directoryIP>]```

