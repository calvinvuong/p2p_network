// Main driver class for all peer operations

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class p2p {

    static int neighborPort; // port num for neighbor connections
    static int transferPort; // port num for transfers
    static ServerSocket welcomeNeighborSocket;
    static ServerSocket welcomeTransferSocket;
    
    public static void main(String[] args) {
	readPorts(); // read port nums from config file
	// create welcome sockets
	try {
	    welcomeNeighborSocket = new ServerSocket(neighborPort);
	    welcomeTransferSocket = new ServerSocket(transferPort);
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	
	while (true) {
	    try {
		Socket neighborSocket = welcomeNeighborSocket.accept();
		System.out.println("New connection.");
		Thread t = new Thread( new NeighborThread(neighborSocket) );
		
		t.start();
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
	
	/*
	  System.out.println(neighborPort);
	  System.out.println(transferPort);
	*/
    }
    

    // Sets the port numbers for this peer based on config file.
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
}

// Handles operations relating to the socket between connected ppers
class NeighborThread implements Runnable {
    Socket connectionSocket;
    BufferedReader in; // in from neighbor
    DataOutputStream out; // out to neighbor
    
    public NeighborThread(Socket connection) {
	connectionSocket = connection;
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
