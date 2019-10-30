// Calvin Vuong ccv7
// Main driver class for all peer operations
// Listens to user System.in input and calls appropriate actions
// Launches the welcome threads

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

	    // create thread to welcome neighbor peer connections
	    Thread welcomeThread = new Thread( new WelcomeThread(welcomeNeighborSocket, localIP, transferPort, IPConnections, sockets, sent, received) );
	    welcomeThread.start();

	    // create thread to welcome file transfer connections
	    Thread welcomeTransferThread = new Thread( new WelcomeTransferThread(welcomeTransferSocket) );
	    welcomeTransferThread.start();
	    
	    // store user input
	    BufferedReader userInputBuffer = new BufferedReader( new InputStreamReader(System.in) );
	    while (true) {
		String userInput = userInputBuffer.readLine();
		if (userInput.toLowerCase().equals("connect"))
		    connectNeighbors();
		else if (userInput.toLowerCase().equals("leave")) {
		    disconnectAllNeighbors();
		}
		else if (userInput.toLowerCase().equals("exit")) {
		    disconnectAllNeighbors();
		    break; // break out of while loop to exit program
		}
		else if (userInput.toLowerCase().startsWith("get")) {
		    String requestedFile = userInput.split(" ")[1];
		    sendQuery(requestedFile);
		}
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	System.out.println("Program terminated.");
	System.exit(0); // terminate this process (including all threads)
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
			Thread clientConnectThread = new Thread( new ClientConnectThread(neighborIP, neighborPort, localIP, transferPort, IPConnections, sockets, sent, received) );
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
