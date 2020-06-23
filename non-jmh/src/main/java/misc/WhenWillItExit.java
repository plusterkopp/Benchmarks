package misc;

public class WhenWillItExit {
	public static void main(String[] argc) throws InterruptedException {
		Thread t = new Thread(() -> {
			long l = 0;
			for (int i = 0; i < Integer.MAX_VALUE; i++) {
				for (int j = 0; j < Integer.MAX_VALUE; j++) {
					if ((j & 1) == 1)
						l++;
				}
			}
			System.out.println("How Odd:" + l);
		});
		t.setDaemon(true);
		long then = System.currentTimeMillis();
		t.start();
		Thread.sleep(5000);
		long durMS = System.currentTimeMillis() - then;
		System.out.println("Finished in:" + durMS + " ms");
	}
}