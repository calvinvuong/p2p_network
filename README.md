# p2p_network

## Implementation Specifics

## Command Implementation
### Connect
* Reads `config_neighbors.txt` to get a list of neighbor IP addresses and welcome ports.
* For each neighbor in the config file:
..* Does nothing if the connection to this neighbor already exists. This ensures there is only *one* TCP connection between any two peer neighbors.
..* If the connection does not yet exist, starts a ClientConnectThread to setup the TCP connection.
..* The ClientConnectThread handles adds the newly created socket connected to neighbor to list of existing sockets for the peer.

### Get
* Generates a unique query ID for the new query.
* Puts the query ID into table of received queries to prevent "cycles" where the host forwards a query that it originally sent.
* Writes the query message to each connected neighboring peer by looping through the list of sockets.

### Leave
* For each socket in the list of sockets to neighboring peers:
..* Writes a goodbye message into the socket, which tells neighbor peer to close the socket on his side.
..* Closes the socket on this peer's side.
* Clears the list of sockets and list of connected neighbor IP addresses.

### Exit
Does the same thing as the `Leave` command, but also breaks out of the main program loop in `p2p`, which terminates the peer.

## Main Program and Threads

The "main" program in class `p2p` does the following things:
..* Starts the welcome threads.
..* Listens for user input and calls the appropriate methods.

The threads in the program do the following things:
* WelcomeThread
..* Listens for a peer neighbor connection over a welcome socket.
..* Starts a new thread (NeighborThread) to handle peer neighbor communications once a connection is accepted and adds that socket to a list of existing sockets.
* WelcomeTransferThread
..* Listens for an ad-hoc peer connection for file transfer requests over a welcome socket.
..* Starts a new thread (TransferServerThread) to handle sending the file to peer once a connection is accepted.
* ClientConnectThread
..* Initiates contact with one neighboring peer in order to establish a socket for neighbor peer communications.
..* Adds the socket to a list of existing sockets for the peer.
..* Starts a new thread (NeighborThread) to handle peer neighbor communications.
* NeighborThread
..* Starts a new HeartbeatThread upon creation.
..* Listens for query, queryhit, and heartbeat messages from one specific neighboring peer.
..* Disconnects from the neighbor and closes socket upon query timeout.
..* Disconnects from the neighbor and closes socket upon a goodbye message.
..* Relays received queries to *all* neighbor peers
..* If received a query for which this peer has the file, drops the query and sends a queryhit message to its neighbor. 
..* Relays received queryhits to the neighbor on the reverse path.
..* If received a queryhit for the file *this* peer has requested, drops the queryhit and creates a new transferClientThread to initiate ad-hoc TCP file request.
* HeartbeatThread
..* Periodically sends heartbeat messages to one specific neighboring peer.
..* Terminates when the neighboring peer is dead.
* TransferServerThread
..* Writes the requested file into the socket so that the other peer can receive the file.
* TransferClientThread
..* Initiates contact with a peer that it knows has the desired file and creates the socket.
..* Writes the file request to that socket.
..* Reads the file from the socket and writes the file to the `/obtained` folder.