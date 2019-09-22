package de.pkmd;

import java.text.*;
import java.util.concurrent.*;

public class JitterTest {

	public static void main(String[] args) {

		NumberFormat nf = NumberFormat.getIntegerInstance();
		nf.setGroupingUsed( true);
		ScheduledExecutorService ses = Executors.newScheduledThreadPool( 2);
		int countA[] = { 0};
		long thenA[] = { 0};
		long lastA[] = { 0};
		long intervalNS = 10_000_000;
		long delay = 10_000;
		ScheduledFuture f[] = { null};
		StringBuilder   sb = new StringBuilder();
		Runnable task = () -> {
			long d = f[ 0].getDelay( TimeUnit.NANOSECONDS);
			long now = System.nanoTime();
			long sinceThenNS = now - thenA[ 0];
			int count = countA[ 0];
			long sinceThenExpectedNS = delay + intervalNS * count;
			long diffNS = sinceThenNS - sinceThenExpectedNS;
			long sinceLastNS = now - lastA[0];
			sb.append( "round: " + count
					+ " late by: " + nf.format( diffNS)
					+ " since last: " + nf.format(sinceLastNS)
					+ " jitter since last: " + nf.format( sinceLastNS - intervalNS)
					+ " job delay: " + nf.format( d)
					+ "\n"
			);
			if ( sinceThenNS > 50_000_000_000L) {
				System.out.println( sb.toString());
				System.exit( 0);
			}
			countA[ 0]++;
			lastA[ 0] = now;
		};
		thenA[ 0] = System.nanoTime();
		lastA[ 0] = thenA[ 0];
		f[ 0] = ses.scheduleAtFixedRate( task, delay, intervalNS, TimeUnit.NANOSECONDS);
	}
}
