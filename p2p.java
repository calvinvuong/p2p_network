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
    
    public static void main(String[] args) {
	System.out.println("Starting peer...");
	readPorts(); // read port nums from config file
	try {
	    localIP = InetAddress.getLocalHost();

	    // create welcome sockets
	    welcomeNeighborSocket = new ServerSocket(neighborPort);
	    welcomeTransferSocket = new ServerSocket(transferPort);
	
	    // create therad to welcome connections
	    Thread welcomeThread = new Thread( new WelcomeThread(welcomeNeighborSocket, welcomeTransferSocket, IPConnections, sockets) );
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
		
	    }
	
	    /*
	      System.out.println(neighborPort);
	      System.out.println(transferPort);
	    */
	}
	catch (Exception e) {
	    e.printStackTrace();
	}

    }
    

    // Sets the port numbers for this peer based on config file.
    // First port in config file is neighbor port
    public static void readPorts() {
	try {
	    File peerConfig = new File("config_peer.txt");
	    Scanner scan = new Scanner(peerConfig);
	    neighborPort = Integer.parseInt(scan.nextLine());
	    transferPort = Integer.parseInt(scan.nextLine());
	}
	catch (FileNotFoundException e) {
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
			Thread clientConnectThread = new Thread( new ClientConnectThread(neighborIP, neighborPort, IPConnections, sockets) );
			clientConnectThread.start();
		    }
		} // close synchronized
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
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
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;
    
    public WelcomeThread(ServerSocket welcomeNeighbor, ServerSocket welcomeTransfer, List<InetAddress> neighborIPs, List<Socket> existingSockets) {
	welcomeNeighborSocket = welcomeNeighbor;
	welcomeTransferSocket = welcomeTransfer;
	IPConnections = neighborIPs;
	sockets = existingSockets;
    }

    @Override
    public void run() {
	while (true) {
	    try {
		System.out.println("...listening");
		Socket neighborSocket = welcomeNeighborSocket.accept();
		System.out.println("New connection: " + neighborSocket.getInetAddress());
		IPConnections.add(neighborSocket.getInetAddress());
		sockets.add(neighborSocket);
		
		Thread neighborThread = new Thread( new NeighborThread(neighborSocket, IPConnections, sockets) );
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
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    public ClientConnectThread(InetAddress serverIP, int serverPort, List<InetAddress> neighborIPs, List<Socket> existingSockets) {
	neighborIP = serverIP;
	neighborPort = serverPort;
	IPConnections = neighborIPs;
	sockets = existingSockets;
    }
    
    @Override
    public void run() {
	try {
	    System.out.println("Attempting connect to: " + neighborIP);
	    Socket connectionSocket = new Socket(neighborIP, neighborPort);
	    System.out.println("Successful connection to: " + neighborIP);
	    IPConnections.add(neighborIP);
	    sockets.add(connectionSocket);

	    Thread neighborThread = new Thread( new NeighborThread(connectionSocket, IPConnections, sockets) );
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
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;
    
    public NeighborThread(Socket connection, List<InetAddress> neighborIPs, List<Socket> existingSockets) {
	alive = new AtomicBoolean(true);
	connectionSocket = connection;
	neighborIP = connectionSocket.getInetAddress();
	IPConnections = neighborIPs;
	sockets = existingSockets;
	
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
	Thread heartbeat = new Thread( new HeartbeatThread(connectionSocket, alive, out, IPConnections) );
	heartbeat.start();

	while (alive.get()) { // run loop while connection is alive
	    try {
		String incoming = in.readLine();
		if ( incoming.startsWith("H") ) {
		    System.out.println("Received heartbeat from: " + incoming);
		    // reset timer
		}
		else if ( incoming.startsWith("G") ) { // neighbor has left
		    // get IP of peer that sent goodbye
		    String peerIP = incoming.split(":")[1];
		    System.out.println(peerIP + " wants to disconnect.");
		    disconnectNeighbor(peerIP);
		    System.out.println(peerIP + " disconnected.");
		}
	    }
	    catch (SocketException e) { // indicates socket closed
		//System.out.println(alive.get()); // diagnostic
		alive.set(false);
		System.out.println("Connection to " + neighborIP + " closed.");
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    // Terminates connection between local host and one peer
    // Does not send goodbye message
    public void disconnectNeighbor(String peerIPString) {
	synchronized(IPConnections) {
	    try {
		InetAddress peerIP = InetAddress.getByName(peerIPString);
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
	    catch (UnknownHostException e) {
		e.printStackTrace();
	    }
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
    long time;
    String message; // heartbeat message
    List<InetAddress> IPConnections;
    
    public HeartbeatThread(Socket connection, AtomicBoolean aliveFlag, DataOutputStream outToNeighbor, List<InetAddress> allConnections) {
	connectionSocket = connection;
	IPConnections = allConnections;
	alive = aliveFlag;
	out = outToNeighbor;
	time = System.currentTimeMillis();
	neighborIP = connectionSocket.getInetAddress();
	try {
	    String localIP = InetAddress.getLocalHost().getHostAddress();
	    message = "H:" + localIP + "\n";
	}
	catch (UnknownHostException e) {
	    e.printStackTrace();
	}
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
		Thread.sleep(5*1000); // sleep for 2 seconds
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

	

    
	
