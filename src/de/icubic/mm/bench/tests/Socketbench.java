package de.icubic.mm.bench.tests;

import java.io.*;
import java.net.*;
import java.text.*;

public class Socketbench {

	public final static int SOCKET_PORT = 13267; // you may change this
	public final static String SERVER = "127.0.0.1"; // localhost
	static final int	MaxNum = 1_000_000_000;
	static final int	WriteBufSize = 1 << 16;

	static final NumberFormat NF = DecimalFormat.getIntegerInstance();
	static {
		NF.setGroupingUsed( true);
	}

	static private byte incCycle( byte value) {
		if (value == Byte.MAX_VALUE) {
			return Byte.MIN_VALUE;
		}
		return ( byte) ( value + 1);

	}

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
					byte[] writeBuf = new byte[ WriteBufSize];
					long	then = System.nanoTime();
					int i = 0;
					while ( i < MaxNum) {
						int j = 0;
						while ( i < MaxNum && j < writeBuf.length) {
							writeBuf[ j] = toSend;
							toSend = incCycle( toSend);
							 j++;
							 i++;
						}
						os.write( writeBuf, 0, j);
						bytesSent += j;
					}
					os.flush();
					long	now = System.nanoTime();
					double	durMS = 1e-6 * ( now - then);
					System.out.println("Done sending " + NF.format( bytesSent) + " bytes in " + NF.format( durMS) + " ms" + " (" + NF.format( bytesSent / ( durMS)) + " kbytes/s");
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
				byte[] mybytearray = new byte[ WriteBufSize];
				InputStream is = sock.getInputStream();
				long	then = System.nanoTime();
//				bytesRead = is.read(mybytearray, 0, mybytearray.length);
//				totalBytesRead += bytesRead;
				byte expected = 0;
//				if ( mybytearray[ 0] != expected) {
//					System.err.println( "expected " + expected + " for first byte, got " + mybytearray[ 0]);
//				}

				do {
					bytesRead = is.read(mybytearray, 0, mybytearray.length);
					if ( bytesRead > 0) {
						totalBytesRead += bytesRead;
						for ( int i = 0;  i < bytesRead; i++) {
							if ( mybytearray[ i] != expected) {
								System.err.println( "expected " + expected + " for byte #" + totalBytesRead + ", got " + mybytearray[ 0]);
							}
							expected = incCycle( expected);
						}
					}
				} while (bytesRead > -1);

				long	now = System.nanoTime();
				double	durMS = 1e-6 * ( now - then);
				System.out.println("transfer complete (" + NF.format( totalBytesRead) + " bytes read) in " + NF.format( durMS) + " ms");
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
				e.printStackTrace();
			}
		}, "Server");
		Thread	clientThread = new Thread(() -> {
			try {
				SimpleFileClient client = new SimpleFileClient();
				client.run();
			} catch (IOException e) {
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
