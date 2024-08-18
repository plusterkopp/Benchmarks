package misc;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClockWatch {

	static final long StartNS = System.nanoTime();
	static final long StartMS = System.currentTimeMillis();

	public static void main(String[] args) {
		ScheduledExecutorService ses = new ScheduledThreadPoolExecutor( 1);

		ses.scheduleAtFixedRate( () -> {
			long nowNS = System.nanoTime() - StartNS;
			long nowMS = System.currentTimeMillis() - StartMS;
			long diffNS = 1_000_000 * nowMS - nowNS;
			System.out.println( "currentTimeMillis is " + diffNS + " ns faster than nanoTime");
			},
			10, 10, TimeUnit.SECONDS);
	}
}
