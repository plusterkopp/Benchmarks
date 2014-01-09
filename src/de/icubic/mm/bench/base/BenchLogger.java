package de.icubic.mm.bench.base;

import java.io.*;
import java.text.*;

public class BenchLogger {

	static PrintStream	outStream;
	static PrintStream	errStream;

	public static void sysout( String string) {
		sysout( string, null);
	}

	private static void sysout( String string, Throwable	t) {
		sysout( outStream, System.out, string, t);
	}

	public static void syserr( String string) {
		syserr( string, null);
	}

	public static void syserr( String string, Throwable	t) {
		sysout( errStream, System.err, string, t);
	}

	private static void sysout( PrintStream outStream, PrintStream sys, String string, Throwable t) {
		final String message = getMessage( string);
		if ( outStream != null) {
			outStream.println( message);
			if ( t != null) {
				t.printStackTrace( outStream);
			}
		}
		sys.println( message);
		if ( outStream == null && t != null) {
			t.printStackTrace( sys);
		}
	}

	private static String getMessage( String m) {
		long	now = BenchRunner.getNow();
		final String message = lnf.format( now) + ": " + m;
		return message;
	}

	public static void setLogName( String base) {
		if ( outStream != null) {
			outStream.close();
		}
		if ( errStream != null) {
			errStream.close();
		}
		outStream = createStream( base, ".out.log");
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

	public static NumberFormat lnf = DecimalFormat.getNumberInstance();

}
