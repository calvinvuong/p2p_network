// Main driver class for all peer operations

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.*;
import java.util.concurrent.atomic.*;

public class p2p {

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
		if (userInput.equals("Connect")) {
		    connectNeighbors();
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
// Listens for Heartbeats
class NeighborThread implements Runnable {
    volatile AtomicBoolean alive; // determines if this thread is still alive
    Socket connectionSocket;
    BufferedReader in; // in from neighbor
    DataOutputStream out; // out to neighbor
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;
    
    public NeighborThread(Socket connection, List<InetAddress> neighborIPs, List<Socket> existingSockets) {
	alive = new AtomicBoolean(true);
	connectionSocket = connection;
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
	System.out.println("hi");
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
    
    public HeartbeatThread(Socket connection, AtomicBoolean aliveFlag, DataOutputStream outToNeighbor) {
	connectionSocket = connection;
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
	while ( alive.get() ) { // connection still alive
	    try {
		out.writeBytes(message);
		System.out.println("Heartbeat sent to: " + neighborIP);
		Thread.sleep(2*1000); // sleep for 2 seconds
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}

	

    
	
