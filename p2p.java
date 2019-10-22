// Main driver class for all peer operations

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.*;
import java.util.concurrent.atomic.*;

public class p2p {

    static InetAddress localIP; // IP address of this peer
    static int neighborPort; // port num for neighbor connections
    static int transferPort; // port num for transfers
    static ServerSocket welcomeNeighborSocket;
    static ServerSocket welcomeTransferSocket;
    static volatile List<InetAddress> IPConnections = Collections.synchronizedList(new ArrayList<InetAddress>()); // list of IP addresses this peer currently has direct connection to
    static volatile List<Socket> sockets = Collections.synchronizedList(new ArrayList<Socket>()); // a list of existing sockets to neighboring peers

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    // InetAddress is null if this peer originally sent query
    static volatile Map<String, InetAddress> sent = Collections.synchronizedMap(new HashMap<String, InetAddress>());
    // maps a relayed query_id to the first time it arrived at this peer
    static volatile Map<String, Long> received = Collections.synchronizedMap(new HashMap<String, Long>());

    static int queryIdBase; // query ids for this host starts at this number
    static int queryNum = 0;
    
    public static void main(String[] args) {
	System.out.println("Starting peer...");
	readPorts(); // read port nums from config file
	try {
	    // create welcome sockets
	    welcomeNeighborSocket = new ServerSocket(neighborPort);
	    welcomeTransferSocket = new ServerSocket(transferPort);

	    // create therad to welcome connections
	    Thread welcomeThread = new Thread( new WelcomeThread(welcomeNeighborSocket, welcomeTransferSocket, IPConnections, sockets, sent, received) );
	    welcomeThread.start();

	    // store user input
	    BufferedReader userInputBuffer = new BufferedReader( new InputStreamReader(System.in) );
	    while (true) {
		String userInput = userInputBuffer.readLine();
		if (userInput.equals("Connect"))
		    connectNeighbors();
		else if (userInput.equals("Leave")) {
		    disconnectAllNeighbors();
		}
		else if (userInput.startsWith("Get")) {
		    String requestedFile = userInput.split(" ")[1];
		    sendQuery(requestedFile);
		}
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}

    }
    

    // Sets the IP port numbers for this peer based on config file.
    // First port in config file is neighbor port
    // Second port is the transfer port
    // Also sets the query base
    public static void readPorts() {
	try {
	    File peerConfig = new File("config_peer.txt");
	    Scanner scan = new Scanner(peerConfig);
	    localIP = InetAddress.getByName(scan.nextLine());
	    neighborPort = Integer.parseInt(scan.nextLine());
	    transferPort = Integer.parseInt(scan.nextLine());

	    String IPString = localIP.getHostAddress();
	    queryIdBase = Integer.parseInt(IPString.substring(IPString.length() - 1)) * 100000000;
	}
	catch (FileNotFoundException e) {
	    e.printStackTrace();
	}
	catch (UnknownHostException e) {
	    e.printStackTrace();
	}
    }

    // Creates a socket to every neighbor in the overlay network
    // Creates a thread to handle each socket operation
    // Adds newly created sockets to list
    public static void connectNeighbors() {
	try {
	    File neighborConfig = new File("config_neighbors.txt");
	    Scanner scan = new Scanner(neighborConfig);
	    while (scan.hasNextLine()) {
		String line = scan.nextLine();
		String[] IPPortTuple = line.split(" ");
		InetAddress neighborIP = InetAddress.getByName(IPPortTuple[0]);
		int neighborPort = Integer.parseInt(IPPortTuple[1]);
		
		// check if there is already an established connection to the IP
		synchronized(IPConnections) {
		    if ( IPConnections.contains(neighborIP) ) 
			System.out.println("Connection to :" + neighborIP + " already exists.");
		    else {
			// give to ClientConnectThread to initiate socket connection
			Thread clientConnectThread = new Thread( new ClientConnectThread(neighborIP, neighborPort, IPConnections, sockets, sent, received) );
			clientConnectThread.start();
		    }
		} // close synchronized
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }

    // Sends query to all connected neighbors
    public static void sendQuery(String requestedFile) {
	int queryId = queryIdBase + queryNum;
	String queryMessage = "Q:" + queryId + ";" + requestedFile + "\n";

	sent.put(queryId + "", null);
	// write query to sockets of all neighbors
	synchronized(IPConnections) {
	    for ( int i = 0; i < sockets.size(); i++ ) {
		try {
		    DataOutputStream out = new DataOutputStream(sockets.get(i).getOutputStream());
		    out.writeBytes(queryMessage);
		}
		catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	} // close sync

	queryNum = (queryNum + 1) % 100000000;
    }

    
    public static void disconnectAllNeighbors() {
	try {
	    synchronized(IPConnections) {
		IPConnections.clear();
		for ( int i = 0; i < sockets.size(); i++ )  {
		    // send goodbye message
		    String goodbyeMessage = "G:" + localIP.getHostAddress() + "\n";
		    DataOutputStream out = new DataOutputStream(sockets.get(i).getOutputStream());
		    out.writeBytes(goodbyeMessage);
		    sockets.get(i).close(); // close socket
		}
		sockets.clear();
	    }
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }
	
}

// Handles operations relating to welcome sockets
class WelcomeThread implements Runnable {
    ServerSocket welcomeNeighborSocket;
    ServerSocket welcomeTransferSocket;
    final int HEARTBEAT_TIMEOUT = 12; // number of seconds to wait for heartbeat / or any other message
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id to the first time it arrived at this peer
    volatile Map<String, Long> received;

    public WelcomeThread(ServerSocket welcomeNeighbor, ServerSocket welcomeTransfer, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	welcomeNeighborSocket = welcomeNeighbor;
	welcomeTransferSocket = welcomeTransfer;
	IPConnections = neighborIPs;
	sockets = existingSockets;
	sent = sentList;
	received = receivedList;
    }

    @Override
    public void run() {
	while (true) {
	    try {
		System.out.println("...listening");
		Socket neighborSocket = welcomeNeighborSocket.accept();
		neighborSocket.setSoTimeout(HEARTBEAT_TIMEOUT * 1000);
		
		System.out.println("New connection: " + neighborSocket.getInetAddress());
		IPConnections.add(neighborSocket.getInetAddress());
		sockets.add(neighborSocket);
		
		Thread neighborThread = new Thread( new NeighborThread(neighborSocket, IPConnections, sockets, sent, received) );
		neighborThread.start();
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}

// Handles operations to establish a "client" connection
class ClientConnectThread implements Runnable {
    InetAddress neighborIP;
    int neighborPort;
    final int HEARTBEAT_TIMEOUT = 12; // number of seconds to wait for heartbeat / or any other message
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id to the first time it arrived at this peer
    volatile Map<String, Long> received;

    public ClientConnectThread(InetAddress serverIP, int serverPort, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	neighborIP = serverIP;
	neighborPort = serverPort;
	IPConnections = neighborIPs;
	sockets = existingSockets;
	sent = sentList;
	received = receivedList;
    }
    
    @Override
    public void run() {
	try {
	    System.out.println("Attempting connect to: " + neighborIP);
	    Socket connectionSocket = new Socket(neighborIP, neighborPort);
	    connectionSocket.setSoTimeout(HEARTBEAT_TIMEOUT * 1000);
	    
	    System.out.println("Successful connection to: " + neighborIP);
	    IPConnections.add(neighborIP);
	    sockets.add(connectionSocket);

	    Thread neighborThread = new Thread( new NeighborThread(connectionSocket, IPConnections, sockets, sent, received) );
	    neighborThread.start();
	}
	catch (ConnectException e) {
	    System.out.println("Connection to: " + neighborIP + " failed. Make sure that peer is running.");
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }
}

// Handles operations relating to the an already-established socket between connected ppers
// Listens for heartbeats, queries, and responses
class NeighborThread implements Runnable {
    volatile AtomicBoolean alive; // determines if this thread is still alive
    Socket connectionSocket;
    InetAddress neighborIP; 
    BufferedReader in; // in from neighbor
    DataOutputStream out; // out to neighbor
    long lastHeartbeat; // time in millsec of last heartbeat
    final int HEARTBEAT_TIMEOUT = 12; // number of seconds to wait for heartbeat / or any other message
    final int RECEIVED_TIMEOUT = 15; // number of seconds until a received query is considered old/out of network
    
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id to the first time it arrived at this peer
    volatile Map<String, Long> received;
    
    
    public NeighborThread(Socket connection, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	alive = new AtomicBoolean(true);
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
	    }
	    catch (SocketTimeoutException e) { // did not receive heartbeat for a while
		System.out.println("Heartbeat Timeout");
	    }
	    /*
	      catch (SocketException e) { // indicates socket closed
	      //System.out.println(alive.get()); // diagnostic
	      alive.set(false);
	      System.out.println("Connection to " + neighborIP + " closed.");
	      System.out.println(IPConnections);
	      }
	    */
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
    // Adds query_id to HashMap of received queries
    public boolean duplicateQuery(String query) {
	String queryId = query.split(":|;")[1];
	// new query, or received another query with the same id a long time ago
	if ( ! received.containsKey(queryId) || System.currentTimeMillis() - received.get(queryId) > RECEIVED_TIMEOUT * 1000 ) { 
	    received.put(queryId, System.currentTimeMillis());
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

    public void queryHit(String query) {
	return;
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
	    }
	    //
	    alive.set(false);
	}
    }
    
}

// Sends Hearbeat message to neighbor
// Thread ends if alive is false
// Spawned by a NeighborThread
class HeartbeatThread implements Runnable {
    volatile AtomicBoolean alive;
    Socket connectionSocket;
    InetAddress neighborIP;
    DataOutputStream out; // out ot neighbor
    String message; // heartbeat message
    final int PULSE = 5; // seconds per heartbeat
    
    List<InetAddress> IPConnections;
    
    public HeartbeatThread(Socket connection, AtomicBoolean aliveFlag, DataOutputStream outToNeighbor, List<InetAddress> allConnections) {
	connectionSocket = connection;
	IPConnections = allConnections;
	alive = aliveFlag;
	out = outToNeighbor;
	neighborIP = connectionSocket.getInetAddress();

	String localIP = connectionSocket.getLocalAddress().getHostAddress();
	System.out.println(localIP);
	message = "H:" + localIP + "\n";

    }

    @Override
    public void run() {
	checkAlive();
	while ( alive.get() ) { // connection still alive
	    if ( ! checkAlive() ) // connection is dead; kill thread
		return;
	    try {
		out.writeBytes(message);
		System.out.println("Heartbeat sent to: " + neighborIP);
		Thread.sleep(PULSE*1000); // sleep for x seconds
	    }
	    catch (SocketException e) { // peer has disconnected
		continue; // wait till heartbeat timeout to close
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    // returns true if this connection is still alive, false otherwise
    private boolean checkAlive() {
	synchronized(IPConnections) {
	    for ( int i = 0; i < IPConnections.size(); i++ ) {
		if ( IPConnections.get(i).equals(neighborIP) )
		    return true;
	    }
	    alive.set(false);
	    return false;
	}
    }
}

	

    
	
