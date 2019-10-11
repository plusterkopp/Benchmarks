package perf;

import org.HdrHistogram.DoubleHistogram;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class ConsumerProducerDemo {

	static final long    JobCount = 10_000;

	private static class Item {
		final public long id;
		long    enterQueueNS;
		long    leaveQueueNS;
		long    finishJobNS;

		private Item(long id) {
			this.id = id;
		}

		void enterQueue() {
			enterQueueNS = System.nanoTime();
		}
		void leaveQueue() {
			leaveQueueNS = System.nanoTime();
		}
		void finishJob() {
			finishJobNS = System.nanoTime();
		}
	}

	private static class Producer {
		private final BlockingQueue<Item> queue;

		public Producer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			Thread t = new Thread( () -> start() , "Producer");
			t.start();
			System.out.println( "started " + t.getName());
		}

		public void start() {
			for ( long i = 0;  i < JobCount;  i++) {
				Item item = new Item( i);
				item.enterQueue();
				queue.offer( item);
				Wait( 1);
			}
		}
	}

	private static class Consumer {
		private final BlockingQueue<Item> queue;

		public Consumer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			Thread t = new Thread( () -> start() , "Consumer");
			t.start();
			System.out.println( "started " + t.getName());
		}

		public void start() {
			List<Item>  items = new ArrayList<Item>((int) JobCount);
			Item item;
			try {
				for ( int i = 0;  i < JobCount;  i++) {
					item = queue.take();
					item.leaveQueue();
					Wait(1);
					item.finishJob();
					items.add(item);
				}
			} catch (InterruptedException e) {
			}
			DoubleHistogram histInQueue = new DoubleHistogram( 4);
			DoubleHistogram histInJob = new DoubleHistogram( 4);
			DoubleHistogram histInTotal = new DoubleHistogram( 4);
			for ( Item it : items) {
				long inQueue = it.leaveQueueNS - it.enterQueueNS;
				histInQueue.recordValue( inQueue);
				long inJob = it.finishJobNS - it.leaveQueueNS;
				histInJob.recordValue( inJob);
				long total = it.finishJobNS - it.enterQueueNS;
				histInTotal.recordValue( total);
			}
			printHistogram( histInQueue, "in Queue");
			printHistogram( histInJob, "in Job");
			printHistogram( histInTotal, "total");
		}

		private void printHistogram( DoubleHistogram hist, String name) {
			NumberFormat nf = DecimalFormat.getIntegerInstance();
			System.out.print( name + " " + nf.format( hist.getTotalCount()) + " entries, avg = " + nf.format( hist.getMean()) + " percentiles: ");
			for ( int perc = 0;  perc <= 100;  perc += 10) {
				System.out.print( perc + ": " + nf.format( hist.getValueAtPercentile( perc)) + "  ");
			}
			System.out.println();
		}
	}

	public static void main(String[] args) {
		BlockingQueue<Item> q = new LinkedTransferQueue<>();
		Producer  producer = new Producer( q);
		Consumer  consumer = new Consumer( q);

		consumer.run();
		producer.run();
	}

	static void Wait(int ms) {
		try {
			Thread.sleep( ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
