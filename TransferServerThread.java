// Calvin Vuong ccv7
// Reads the file request and writes the file to socket

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class TransferServerThread implements Runnable {
    Socket transferSocket;
    BufferedReader in;
    DataOutputStream out;
    InetAddress client;
    
    public TransferServerThread(Socket transfer) {
	transferSocket = transfer;
	client = transferSocket.getInetAddress();
	try {
	    in = new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));
	    out = new DataOutputStream(transferSocket.getOutputStream());
	}
	catch (IOException e) {
	    e.printStackTrace();
	}

    }

    @Override
    public void run() {
	// read request
	String request = "";
	try {
	    request = in.readLine();
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
	String fileName = request.split(":")[1];
	System.out.println("Received file request for: " + fileName + " from peer: " + client);
	// get file
	if ( ! containsFile(fileName) ) { // file not found
	    System.out.println("The requested file is not at this host.");
	    try {
		out.writeBytes(null);
		transferSocket.close();
	    }
	    catch (IOException e) {
		e.printStackTrace();
	    }
	}
	else { // file found
	    try {
		File serveFile = new File("shared/" + fileName);
		BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(serveFile));
		byte[] fileBuffer = new byte[2048];
		int bytesRead = fileIn.read(fileBuffer, 0, fileBuffer.length); // read file contents in fileBuffer
		while (bytesRead > 0) {
		    out.write(fileBuffer, 0, bytesRead); // write file bytes into socket
		    bytesRead = fileIn.read(fileBuffer, 0, fileBuffer.length); // read more bytes from file
		}
		fileIn.close();
		transferSocket.close();
	    }
	    catch (IOException e) {
		e.printStackTrace();
	    }
	    System.out.println("File transfer: " + fileName + " completed to " + client);
	}
    }

    // return true if requested file is listed in config_sharing.txt
    // returns false otherwise
    private boolean containsFile(String fileName) {
	try {
	    File configSharing = new File("config_sharing.txt");
	    Scanner scan = new Scanner(configSharing);
	    while (scan.hasNextLine()) {
		String name = scan.nextLine();
		if ( name.equals(fileName) )
		    return true;
	    }
	}
	catch (FileNotFoundException e) {
	    e.printStackTrace();
	}
	return false;
    }
}
