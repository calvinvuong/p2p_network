// Calvin Vuong ccv7
// Sends Hearbeat message to neighbor
// Thread ends if alive is false
// Spawned by a NeighborThread

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

class HeartbeatThread implements Runnable {
    volatile AtomicBoolean alive;
    Socket connectionSocket;
    InetAddress neighborIP;
    DataOutputStream out; // out ot neighbor
    String message; // heartbeat message
    final int PULSE = 30; // seconds per heartbeat
    
    List<InetAddress> IPConnections;
    
    public HeartbeatThread(Socket connection, AtomicBoolean aliveFlag, DataOutputStream outToNeighbor, List<InetAddress> allConnections) {
	connectionSocket = connection;
	IPConnections = allConnections;
	alive = aliveFlag;
	out = outToNeighbor;
	neighborIP = connectionSocket.getInetAddress();

	String localIP = connectionSocket.getLocalAddress().getHostAddress();

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
	    // check if connection to neighbor was removed from list of connected peers
	    for ( int i = 0; i < IPConnections.size(); i++ ) {
		if ( IPConnections.get(i).equals(neighborIP) )
		    return true;
	    }
	    alive.set(false);
	    return false;
	}
    }
}
