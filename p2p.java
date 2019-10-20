// Main driver class for all peer operations

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class p2p {

    int neighborPort; // port num for neighbor connections
    int transferPort; // port num for transfers

    public static void main(String[] args) {
	p2p peer = new p2p();

    }
    
    // Constructor
    public p2p() {
	readPorts(); // read port nums from config file
	System.out.println(neighborPort);
	System.out.println(transferPort);
    }

    // Sets the port numbers for this peer based on config file.
    public void readPorts() {
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
