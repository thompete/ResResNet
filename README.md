# Resource Reservation System

### Description

This is a system consisting of network nodes that hold certain amount of resources.
Clients can connect to the network and reserve resources. The network is implemented as a
full mesh network communicating over the TCP protocol.

### How to run

Network node:

`java NetworkNode -ident <id> -tcpport <port> [-gateway <gateway address>:<gateway port>] <resource>:<amount> [<resource>:<amount>...]`

Client:

`java NetworkClient -ident <id> -gateway <gateway address>:<gateway port> {<resource>:<amount> [<resource>:<amount>...]|terminate}`

Example:

`java NetworkNode -ident 0 -tcpport 9000 A:2 B:1`

`java NetworkNode -ident 1 -gateway localhost:9000 -tcpport 9001 B:2 C:1`

`java NetworkNode -ident 2 -gateway localhost:9001 -tcpport 9002 D:3`

`java NetworkClient -ident 0 -gateway localhost:9001 B:3 D:1`

`java NetworkClient -ident 1 -gateway localhost:9000 A:2 B:1`

`java NetworkClient -ident 0 -gateway localhost:9002 C:1`

`java NetworkClient -ident 1 -gateway localhost:9000 D:2`

`java NetworkClient -ident 2 -gateway localhost:9001 terminate`


### Todo

- implement resource deallocation