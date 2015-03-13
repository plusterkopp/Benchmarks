package de.icubic.mm.bench.base;

import java.io.*;
import java.text.*;

public class BenchLogger {

	static PrintStream	outStream;
	static PrintStream	infoStream;
	static PrintStream	errStream;

	public static void sysout( String string) {
		sysout( string, null);
	}

	private static void sysout( String string, Throwable	t) {
		sysout( string, t, outStream, infoStream, System.out);
	}

	public static void syserr( String string) {
		syserr( string, null);
	}

	public static void syserr( String string, Throwable	t) {
		sysout( string, t, errStream, infoStream, System.err);
	}

	public static void sysinfo( String string) {
		sysout( string, null, infoStream, System.out);
	}

	private static void sysout( String string, Throwable t, PrintStream... outStreams) {
		sysout( BenchRunner.getNow(), string, t, outStreams);
	}

	private static String getMessage( long now, String m) {
		final String message = LNF.format( now) + ": " + m;
		return message;
	}

	public static void setLogName( String base) {
		if ( outStream != null) {
			outStream.close();
		}
		if ( infoStream != null) {
			infoStream.close();
		}
		if ( errStream != null) {
			errStream.close();
		}
		outStream = createStream( base, ".out.log");
		infoStream = createStream( base, ".info.log");
		errStream = createStream( base, ".err.log");
	}

	private static PrintStream createStream( String base, String suffix) {
		PrintStream stream = null;
		String name = base + suffix;
		File	f = new File( name);
		try {
			int index = 1;
			while ( f.exists()) {
				name = base + "-" + index + suffix;
				f = new File( name);
				index++;
			}
			stream = new PrintStream( f);
		} catch ( FileNotFoundException fnfe) {
			System.err.println( fnfe.getLocalizedMessage() + " for " + f.getAbsolutePath());
		}
		return stream;
	}

	public static NumberFormat LNF = DecimalFormat.getNumberInstance();

	public static void syserr( long nanoTime, String string) {
		syserr( nanoTime, string, null);
	}

	private static void syserr( long nanoTime, String string, Throwable t) {
		sysout( nanoTime, string, t, errStream, infoStream, System.err);
	}

	private static void sysout( long nanoTime, String string, Throwable t, PrintStream ...streams) {
		final String message = getMessage( nanoTime, string);
		for ( PrintStream outStream : streams) {
			if ( outStream != null) {
				outStream.println( message);
				if ( t != null) {
					t.printStackTrace( outStream);
				}
			}
		}
	}

}
