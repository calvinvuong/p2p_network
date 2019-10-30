// Calvin Vuong ccv7
// Handles welcoming neighbor peer connections

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class WelcomeThread implements Runnable {
    InetAddress localIP;
    int transferPort;
    ServerSocket welcomeNeighborSocket;
    
    final int HEARTBEAT_TIMEOUT = 75; // number of seconds to wait for heartbeat / or any other message
    volatile List<InetAddress> IPConnections;
    volatile List<Socket> sockets;

    // maps a relayed query_id to the peer IP that forwarded the query to this peer
    volatile Map<String, InetAddress> sent;
    // maps a relayed query_id to the first time it arrived at this peer
    volatile Map<String, Long> received;

    public WelcomeThread(ServerSocket welcomeNeighbor, InetAddress hostIP, int tPort, List<InetAddress> neighborIPs, List<Socket> existingSockets, Map<String, InetAddress> sentList, Map<String, Long> receivedList) {
	localIP = hostIP;
	transferPort = tPort;
	welcomeNeighborSocket = welcomeNeighbor;
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
		// create sockets
		Socket neighborSocket = welcomeNeighborSocket.accept();
		neighborSocket.setSoTimeout(HEARTBEAT_TIMEOUT * 1000);
		
		System.out.println("New connection: " + neighborSocket.getInetAddress());
		IPConnections.add(neighborSocket.getInetAddress());
		sockets.add(neighborSocket);

		// launch thread ot handle communications via this socket
		Thread neighborThread = new Thread( new NeighborThread(neighborSocket, localIP, transferPort, IPConnections, sockets, sent, received) );
		neighborThread.start();
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}
