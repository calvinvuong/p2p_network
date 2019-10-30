// Calvin Vuong ccv7
// Handles operations to establish a connection with the other peer who is listening

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class ClientConnectThread implements Runnable {
    InetAddress localIP;
    int transferPort;
    InetAddress neighborIP;
    int neighborPort;
    final int HEARTBEAT_TIMEOUT = 75; // number of seconds to wait for heartbeat / or any other message
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id to the first time it arrived at this peer
    volatile Map<String, Long> received;

    public ClientConnectThread(InetAddress serverIP, int serverPort, InetAddress hostIP, int hostTransferPort, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	localIP = hostIP;
	transferPort = hostTransferPort;
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
	    // create connection socket
	    Socket connectionSocket = new Socket(neighborIP, neighborPort);
	    connectionSocket.setSoTimeout(HEARTBEAT_TIMEOUT * 1000);
	    
	    System.out.println("Successful connection to: " + neighborIP);
	    // add socket to list of sockets this peer has
	    IPConnections.add(neighborIP);
	    sockets.add(connectionSocket);

	    // start thread to handle neighboring peer to peer communications
	    Thread neighborThread = new Thread( new NeighborThread(connectionSocket, localIP, transferPort, IPConnections, sockets, sent, received) );
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
