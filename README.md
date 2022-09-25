# Resource Reservation System

### Description

This is a system consisting of network nodes that hold certain amount of resources.
Clients can connect to the network and reserve resources. The network is implemented as a
full mesh network communicating over the TCP protocol.

Every node has a list of all other nodes in the network. When a new node is added to the
network, the list is updated in all nodes. It happens like this: the new node asks its
gateway for its list of nodes (GET_NETWORK request). Next it sends information about itself
to all the nodes from the received list, then it adds itself to its own list of nodes.

When a node receives from a client a request for resource allocation, the node at first checks,
whether it can handle it on its own. If cannot, it reserves as many resources as possible,
and then it passes the request to some other unchecked node in the network (ALLOCATE request).
It happens "recursively". After collecting responses from other nodes, the contact node sends
response to the client.

When a node receives from a client flag "TERMINATE", it closes itself and requests the same
from all the other nodes (CLOSE request).

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