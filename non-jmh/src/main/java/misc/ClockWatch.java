package misc;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class ClockWatch {

	static final long StartNS;
	static final long StartMS;
	static {
		long lastCTM = System.currentTimeMillis();
		long ctmRounds = 0;
		while ( lastCTM == System.currentTimeMillis()) {
			ctmRounds++;
		}
		StartNS = System.nanoTime();
		StartMS = System.currentTimeMillis();
	}

	public static void main(String[] args) {
		NumberFormat nf = NumberFormat.getNumberInstance( Locale.US);
		nf.setMaximumFractionDigits( 3);
		nf.setGroupingUsed( true);

		DateFormat df = new SimpleDateFormat( "HH:mm:ss");
		df.setTimeZone( TimeZone.getTimeZone( "GMT"));
		ScheduledExecutorService ses = new ScheduledThreadPoolExecutor( 1);

		Runnable compareJob = () -> {
			long nowNS = System.nanoTime() - StartNS;
			long nowMS = System.currentTimeMillis() - StartMS;
			long tookNS = ( System.nanoTime() - StartNS) - nowNS;
			long diffNS = 1_000_000 * nowMS - nowNS;
			double driftPercent = 100.0 * diffNS / nowNS;
			System.out.println( df.format( new Date( nowMS))
				+ " currentTimeMillis is "
				+ ( diffNS < 0 ? "" : " ")
				+ nf.format( 1e-6 * diffNS)
				+ " ms faster than nanoTime, took "
				+ nf.format( tookNS) + " ns, drift "
				+ nf.format( driftPercent) + " % since start"
			);
		};
		ses.scheduleAtFixedRate( compareJob, 1, 3, TimeUnit.SECONDS);
	}
}
