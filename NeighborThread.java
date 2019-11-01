// Calvin Vuong ccv7
// Handles operations relating to the an already-established socket between connected peers
// Listens for heartbeats, queries, and responses and relays them

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class NeighborThread implements Runnable {
    volatile AtomicBoolean alive; // determines if this thread is still alive
    // taken from main processes that starts this thread
    InetAddress localIP;
    int transferPort;
    Socket connectionSocket;
    InetAddress neighborIP; 
    BufferedReader in; // in from neighbor
    DataOutputStream out; // out to neighbor
    long lastHeartbeat; // time in millsec of last heartbeat
    final int HEARTBEAT_TIMEOUT = 75; // number of seconds to wait for heartbeat / or any other message
    final int RECEIVED_TIMEOUT = 15; // number of seconds until a received query is considered old/out of network
    
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id or response_id to the first time it arrived at this peer
    volatile Map<String, Long> received;
    
    
    public NeighborThread(Socket connection, InetAddress hostIP, int tPort, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	alive = new AtomicBoolean(true);
	localIP = hostIP;
	transferPort = tPort;
	connectionSocket = connection;
	neighborIP = connectionSocket.getInetAddress();
	IPConnections = neighborIPs;
	sockets = existingSockets;
	sent = sentList;
	received = receivedList;
	
	try {
	    in = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
	    out = new DataOutputStream(connectionSocket.getOutputStream());
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }

    @Override
    public void run() {
	System.out.println("Connected to: " + neighborIP);
	lastHeartbeat = System.currentTimeMillis();
	Thread heartbeat = new Thread( new HeartbeatThread(connectionSocket, alive, out, IPConnections) );
	heartbeat.start();

	while (alive.get()) { // run loop while connection is alive
	    try {
		// check if heartbeat timer expired
		if ( System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_TIMEOUT * 1000 ) {
		    System.out.println("Heartbeat timeout on " + neighborIP);
		    disconnectNeighbor(neighborIP);
		    System.out.println(neighborIP + " disconnected.");
		}
		String incoming = in.readLine();
		// received heartbeat message
		if ( incoming != null && incoming.startsWith("H") ) {
		    System.out.println("Received heartbeat from: " + incoming);
		    lastHeartbeat = System.currentTimeMillis();
		}
		// neighbor has left, sent goodbye message
		else if ( incoming != null && incoming.startsWith("G") ) { 
		    // get IP of peer that sent goodbye
		    String peerIP = incoming.split(":")[1];
		    System.out.println(peerIP + " wants to disconnect.");
		    disconnectNeighbor(InetAddress.getByName(peerIP));
		    System.out.println(peerIP + " disconnected.");
		}
		// received query message from neighbor
		else if ( incoming != null && incoming.startsWith("Q") ) {
		    if ( ! duplicateQuery(incoming) ) {
			if ( containsFile(incoming) ) 
			    queryHit(incoming);
			else
			    relayQuery(incoming);
		    }
		}
		// received a queryhit message from neighbor
		else if ( incoming != null && incoming.startsWith("R") ) {
		    if ( ! duplicateQueryHit(incoming) ) {
			if ( queryHitDestination(incoming) )
			    setupTransfer(incoming);
			else
			    relayQueryHit(incoming);
		    }
		}
	    }
	    catch (SocketTimeoutException e) { // did not receive heartbeat for a while
		System.out.println("Heartbeat timeout on " + neighborIP);
	    }
	    
	    catch (SocketException e) { // indicates socket closed
		//System.out.println(alive.get()); // diagnostic
		alive.set(false);
		System.out.println("Connection to " + neighborIP + " closed.");
		//System.out.println(IPConnections);
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    // Sends the query to all neighbors connected to this peer
    public void relayQuery(String query) {
	String queryId = query.split(":|;")[1];
	// update sent HashMap
	sent.put(queryId, neighborIP);
	synchronized(IPConnections) {
	    // relay query to all neighbors...
	    for ( int i = 0; i < sockets.size(); i++ ) {
		// ...except for neighbor who sent it
		if ( ! sockets.get(i).getInetAddress().equals(neighborIP) ) {
		    try {
			DataOutputStream out = new DataOutputStream(sockets.get(i).getOutputStream());
			out.writeBytes(query + "\n");
		    }
		    catch (IOException e) {
			e.printStackTrace();
		    }
		}
	    }
	}
    }

    // Checks if a query of the same ID has already been received at this peer recently
    // Returns true if query is a duplicate
    // Adds query_id to HashMap of received queries
    public boolean duplicateQuery(String query) {
	String queryId = "Q" + query.split(":|;")[1];
	if ( sent.containsKey(query.split(":|;")[1]) && received.get(query.split(":|;")[1]) == null)  // prevent a query from looping back to peer that originally sent it
	    return true;
	// new query, or received another query with the same id a long time ago
	else if ( ! received.containsKey(queryId) || System.currentTimeMillis() - received.get(queryId) > RECEIVED_TIMEOUT * 1000 ) { 
	    received.put(queryId, System.currentTimeMillis());
	    return false;
	}
	return true;
    }

    // Checks if a queryhit of the same ID has already been received at this peer recently
    // Adds queryHitId to Hashmap of received queryhits
    public boolean duplicateQueryHit(String query) {
	String queryHitId = "R" + query.split(":|;")[1];
	// new query, or received another query with the same id a long time ago
	if ( ! received.containsKey(queryHitId) || System.currentTimeMillis() - received.get(queryHitId) > RECEIVED_TIMEOUT * 1000 ) { 
	    received.put(queryHitId, System.currentTimeMillis());
	    return false;
	}
	return true;
    }

    
    // Returns true if this peer contains the file specified in the query incoming
    // Returns false otherwise
    public boolean containsFile(String query) {
	String fileName = query.split(":|;")[2];
	System.out.println("Received file request for: " + fileName);
	try {
	    File configSharing = new File("config_sharing.txt");
	    Scanner scan = new Scanner(configSharing);
	    while (scan.hasNextLine()) {
		String name = scan.nextLine();
		if ( name.equals(fileName) ) {
		    System.out.println("File " + name + " found.");
		    return true;
		}
	    }
	}
	catch (FileNotFoundException e) {
	    e.printStackTrace();
	}
	System.out.println("File " + fileName + " not found. Relaying query forward.");
	return false;
    }

    // Composes and sends queryhit to peer who sent this query
    public void queryHit(String query) {
	String queryId = query.split(":|;")[1];
	String fileName = query.split(":|;")[2];
	String hitMessage = "R:" + queryId + ";" + localIP.getHostAddress() + ":" + transferPort + ";" + fileName + "\n";
	// put responseId in received
	received.put("R" + queryId, System.currentTimeMillis());
	try {
	    out.writeBytes(hitMessage);
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    // Returns true if the queryhit has reached its destination (i.e. the peer that originally sent the request)
    public boolean queryHitDestination(String queryHit) {
	String id = queryHit.split(":|;")[1];
	if ( sent.containsKey(id) && sent.get(id) == null )
	    return true;
	return false;
    }

    // Sends the QueryHit to the immediate peer that forwarded the query
    public void relayQueryHit(String queryHit) {
	String queryHitId = queryHit.split(":|;")[1];
	// received hashmap has already been updated in duplicateQueryHit()
	// get IP of peer who sent the corresponding query
	InetAddress relayIP = sent.get(queryHitId);
	// find socket to relayIP
	synchronized(IPConnections) {
	    for ( int i = 0; i < sockets.size(); i++ ) {
		if ( sockets.get(i).getInetAddress().equals(relayIP) ) {
		    try {
			DataOutputStream out = new DataOutputStream(sockets.get(i).getOutputStream());
			out.writeBytes(queryHit + "\n"); // send queryhit message
		    }
		    catch (IOException e) {
			e.printStackTrace();
		    }
		}
	    } // close sync
	}
    }

    // Launches a thread to request file from peer
    public void setupTransfer(String queryHit) {
	// server refers the the peer that has the file
	try {
	    InetAddress serverIP = InetAddress.getByName(queryHit.split(":|;")[2]);
	    int serverPort = Integer.parseInt(queryHit.split(":|;")[3]);
	    String fileName = queryHit.split(":|;")[4];

	    // start thread to initiate request for file transfer
	    Thread transferClientThread = new Thread( new TransferClientThread(serverIP, serverPort, fileName) );
	    System.out.println("Requesting file: " + fileName + " from: " + serverIP);
	    transferClientThread.start();
	}
	catch (UnknownHostException e) {
	    e.printStackTrace();
	}
					  
    }
    
    // Terminates connection between local host and one peer
    // Does not send goodbye message
    public void disconnectNeighbor(InetAddress peerIP) {
	synchronized(IPConnections) {
	    //InetAddress peerIP = InetAddress.getByName(peerIPString);
	    // remove this peer from list of connection IP addresses
	    for ( int i = 0; i < IPConnections.size(); i++ ) {
		if ( IPConnections.get(i).equals(peerIP) ) {
		    IPConnections.remove(i);
		    break;
		}
	    }
	    // close socket to this peer, and remove from list of sockets
	    for ( int i = 0; i < sockets.size(); i++ ) {
		if ( sockets.get(i).getInetAddress().equals(peerIP) ) {
		    try {
			sockets.get(i).close();
			sockets.remove(i);
		    }
		    catch (IOException e) {
			e.printStackTrace();
		    }
		    break;
		}
	    } // end for loop
	    alive.set(false);
	} // end sync.
    }
    
}
