# collision_resolution_protocol [![Build Status](https://travis-ci.org/niclabs/collision_resolution_protocol.svg?branch=master)](https://travis-ci.org/niclabs/collision_resolution_protocol)

Java program that simulates nodes participating on a DC-NET room and they send messages. If two or more nodes send message at the same time, a collision is produced and this program runs a collision resolution protocol in order to resolve it.

## Instructions

This program simulates the nodes as different threads running in the same machine (for now, the idea afterwards is simulate nodes that runs on a local network and then in a real Internet scenario).

### Run automatize tests

There are tests written as bash scripts that simulates a certain number of nodes sending messages to the rest of the room. The logs of what each node did is stored in the folder logs/.

* Worst Case: all nodes send a message (maximum collision)

    ```sh ./worst_case_test.sh <numberOfNodes>```

* Coin toss: a node flips a coin in order to send a message or not

    ```sh ./prob_one_half_test.sh <numberOfNodes>```
    
### Run nodes separately

* There's also the possibility that a single node can be run separately, allowing the chance to specify the message that each node sends to the room. In order to start the message sending, the total of <numberOfNodes> must be running in the same machine.

    ```./gradlew run -PappArgs=[<message>,<numberOfNodes]```

