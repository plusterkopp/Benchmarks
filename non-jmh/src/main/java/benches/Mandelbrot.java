package benches;

//by Erik Wrenholt

import de.icubic.mm.bench.base.*;

import java.util.*;

class Mandelbrot{
	private static final int SIZE = 500;
	static int BAILOUT = 16;
	static int MAX_ITERATIONS = 1000;

	private static int iterate( double x, double y) {
		double cr = y - 0.5f;
		double ci = x;
		double zi = 0.0f;
		double zr = 0.0f;
		int i = 0;
		while ( true) {
			i++;
			double temp = zr * zi;
			double zr2 = zr * zr;
			double zi2 = zi * zi;
			zr = zr2 - zi2 + cr;
			zi = temp + temp + ci;
			if ( zi2 + zr2 > BAILOUT)
				return i;
			if ( i > MAX_ITERATIONS)
				return 0;
		}
	}

	public static void run2() {
		int x, y;
		StringBuilder sb = new StringBuilder( 2000);
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			sb.append( "\n");
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				if ( iterate( x / ( double) SIZE, y / ( double) SIZE) == 0)
					sb.append( "*");
				else
					sb.append( " ");
			}
		}
		// System.err.print( sb.toString());
	}

	public static void run() {
		Date d1 = new Date( /* echte Zeit */);
		for ( int i = 0; i < 5; i++)
			run2();
		Date d2 = new Date( /* echte Zeit */);
		long diff = d2.getTime() - d1.getTime();
		BenchLogger.sysout( "\nJava Elapsed " + diff / 1000.0f);
	}

	public static void main( String args[]) {
		run();
		run();
		run();
	}
}