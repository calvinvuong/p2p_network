// Calvin Vuong ccv7
// Writes the file request to socket and reads the file from socket

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class TransferClientThread implements Runnable {
    InetAddress transferIP;
    int transferPort;
    String fileName;

    Socket transferSocket;
    BufferedInputStream in;
    DataOutputStream out;
    
    public TransferClientThread(InetAddress serverIP, int serverPort, String file) {
	transferIP = serverIP;
	transferPort = serverPort;
	fileName = file;
	try {
	    transferSocket = new Socket(transferIP, transferPort);
	    in = new BufferedInputStream(transferSocket.getInputStream());
	    out = new DataOutputStream(transferSocket.getOutputStream());
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    @Override
    public void run() {
	// write request to server peer
	try {
	    out.writeBytes("T:" + fileName + "\n");
	    // read file from server peer
	    BufferedOutputStream receivedFile = new BufferedOutputStream(new FileOutputStream("obtained/" + fileName));
	    byte[] fileBuffer = new byte[2048];
	    int bytesRead = in.read(fileBuffer, 0, fileBuffer.length); // read socket contents into buffer
	    while ( bytesRead > 0 ) {
		// write to file the buffer contents
		receivedFile.write(fileBuffer, 0, bytesRead);
		bytesRead = in.read(fileBuffer, 0, fileBuffer.length);
	    }
	    receivedFile.close();
	    transferSocket.close();
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
	System.out.println("Successfully received file: " + fileName + " from: " + transferIP);
    }

}
