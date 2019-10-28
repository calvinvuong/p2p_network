# p2p_network

## Threads
The threads in the program do the following things:
* WelcomeThread
... Listens for a peer neighbor connection over a welcome socket.
... Starts a new thread (NeighborThread) to handle peer neighbor communications once a connection is accepted and adds that socket to a list of existing sockets.
* WelcomeTransferThread
... Listens for an ad-hoc peer connection for file transfer requests over a welcome socket.
... Starts a new thread (TransferServerThread) to handle sending the file to peer once a connection is accepted.
* ClientConnectThread
... Initiates contact with one neighboring peer in order to establish a socket for neighbor peer communications.
... Adds the socket to a list of existing sockets for the peer.
... Starts a new thread (NeighborThread) to handle peer neighbor communications.
* NeighborThread
... Starts a new HeartbeatThread upon creation.
... Listens for query, queryhit, and heartbeat messages from one specific neighboring peer.
... Disconnects from the neighbor and closes socket upon query timeout.
... Disconnects from the neighbor and closes socket upon a goodbye message.
... Relays received queries to *all* neighbor peers
... If received a query for which this peer has the file, drops the query and sends a queryhit message to its neighbor. 
... Relays received queryhits to the neighbor on the reverse path.
... If received a queryhit for the file *this* peer has requested, drops the queryhit and creates a new transferClientThread to initiate ad-hoc TCP file request.
* HeartbeatThread
... Periodically sends heartbeat messages to one specific neighboring peer.
... Terminates when the neighboring peer is dead.
* TransferServerThread
... Writes the requested file into the socket so that the other peer can receive the file.
* TransferClientThread
... Initiates contact with a peer that it knows has the desired file and creates the socket.
... Writes the file request to that socket.
... Reads the file from the socket and writes the file to the `/obtained` folder.