package de.icubic.mm.bench.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Socketbench {

	public final static int SOCKET_PORT = 13267; // you may change this
	public final static String SERVER = "127.0.0.1"; // localhost
	static final int	MaxNum = 1_000_000;
	
	static class SimpleFileServer {

		public void run() throws IOException {
			OutputStream os = null;
			ServerSocket servsock = null;
			Socket sock = null;
			int	bytesSent = 0;
			try {
				servsock = new ServerSocket(SOCKET_PORT);
				System.out.println("Waiting...");
				try {
					sock = servsock.accept();
					System.out.println("Accepted connection : " + sock);
					os = sock.getOutputStream();
					// System.out.println("Sending " + FILE_TO_SEND + "(" + mybytearray.length + "
					// bytes)");
					byte toSend = 0;
					long	then = System.nanoTime();
					for (int i = 0; i < MaxNum; i++) {
						os.write(toSend);
						bytesSent++;
						if (toSend == Byte.MAX_VALUE) {
							toSend = Byte.MIN_VALUE;
						} else {
							toSend++;
						}
					}
					os.flush();
					long	now = System.nanoTime();
					double	durMS = 1e-6 * ( now - then);
					System.out.println("Done sending " + bytesSent + " bytes in " + durMS + " ms" + " (" + ( bytesSent / ( durMS)) + " kbytes/s");
				} finally {
					if (os != null)
						os.close();
					if (sock != null)
						sock.close();
				}
			} finally {
				if (servsock != null)
					servsock.close();
			}
		}
	}

	static class SimpleFileClient {

		public void run() throws IOException {
			int bytesRead;
			int totalBytesRead = 0;
			Socket sock = null;
			try {
				sock = new Socket(SERVER, SOCKET_PORT);
				System.out.println("Connecting...");

				// receive file
				byte[] mybytearray = new byte[1];
				InputStream is = sock.getInputStream();
				long	then = System.nanoTime();
				bytesRead = is.read(mybytearray, 0, mybytearray.length);
				totalBytesRead += bytesRead;
				byte expected = 0;
				if ( mybytearray[ 0] != expected) {
					System.err.println( "expected " + expected + " for first byte, got " + mybytearray[ 0]);
				}

				do {
					if ( expected == Byte.MAX_VALUE) {
						expected = Byte.MIN_VALUE;
					} else {
						expected++;
					}
					bytesRead = is.read(mybytearray, 0, mybytearray.length);
					if ( bytesRead > 0) {
						totalBytesRead += bytesRead;
						if ( mybytearray[ 0] != expected) {
							System.err.println( "expected " + expected + " for byte #" + totalBytesRead + ", got " + mybytearray[ 0]);
						}
					}
					
				} while (bytesRead > -1);

				long	now = System.nanoTime();
				double	durMS = 1e-6 * ( now - then);
				System.out.println("transfer complete (" + totalBytesRead + " bytes read) in " + durMS + " ms");
			} finally {
				if (sock != null)
					sock.close();
			}
		}

	}

	public static void main(String[] args) throws IOException {
		Thread	serverThread = new Thread(() -> {
			try {
				SimpleFileServer server = new SimpleFileServer();
				server.run();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}, "Server");
		Thread	clientThread = new Thread(() -> {
			try {
				SimpleFileClient client = new SimpleFileClient();
				client.run();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}, "Client");
		serverThread.start();
		clientThread.start();
		try {
			serverThread.join();
			clientThread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
