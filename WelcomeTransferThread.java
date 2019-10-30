// Calvin Vuong ccv7
// Handles welcoming file transfer connections
// Creates a new thread to handle the file transfer when connection accepted

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class WelcomeTransferThread implements Runnable {
    ServerSocket welcomeTransferSocket;

    public WelcomeTransferThread(ServerSocket welcomeTransfer) {
	welcomeTransferSocket = welcomeTransfer;
    }

    @Override
    public void run() {
	while (true) {
	    try {
		Socket transferSocket = welcomeTransferSocket.accept();
		// launch thread to handle file transfer
		Thread transferServerThread = new Thread( new TransferServerThread(transferSocket) );
		transferServerThread.start();
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}
