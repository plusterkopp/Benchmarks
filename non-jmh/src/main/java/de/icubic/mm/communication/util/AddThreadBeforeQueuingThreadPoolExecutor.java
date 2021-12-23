/*
 * Created on 04.03.2005
 */
package de.icubic.mm.communication.util;

import de.icubic.mm.bench.base.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Diese Klasse �ndert das Verhalten von ThreadPoolExecutor dahingehend, dass neue Thread
 * kreiert werden, auch wenn die WorkQueue noch nicht voll ist. der
 * <code>creationThreshold</code>-Parameter steuert dieses Verhalten.
 *
 * @author hpagenhardt
 */
public class AddThreadBeforeQueuingThreadPoolExecutor extends ThreadPoolExecutor {

	/**
	 * erzeugt Executor mit benannten Threads, weitere Voreinstellungen: CoreSize 0, 1 Minute Timeout, creationThreshold 0
	 * @param size	Größe des Pools
	 * @param poolName	Name des Pools: Threadnamen bekommen die Threadnummer angeh�ngt
	 * @param queue
	 * @return neuer TPE
	 */
	public static AddThreadBeforeQueuingThreadPoolExecutor getExecutor( int size, final String poolName, BlockingQueue<Runnable> queue) {
		final AddThreadBeforeQueuingThreadPoolExecutor	tpe =
				new AddThreadBeforeQueuingThreadPoolExecutor( 0,  size, 1, TimeUnit.MINUTES, queue, 0);
		final AtomicInteger	threadNumber = new AtomicInteger( 0);
		ThreadFactory	tf = new ThreadFactory() {
			@Override
			public Thread newThread( Runnable r) {
				Thread t = new ExecutorThread( tpe, r, poolName + "-" + threadNumber.incrementAndGet());
				return t;
			}
		};
		tpe.setThreadFactory( tf);
		return tpe;
	}

	/**
	 * wenn nach einem {@link#execute(Runnable)} Aufruf mehr als <code>creationThreshold</code>
	 * Tasks in der WorkQueue warten und noch nicht die maximale Anzahl Worker arbeiten, dann wird
	 * ein neuer Worker erzeugt
	 */
	int creationThreshold;

	/**
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long,
	 *      java.util.concurrent.TimeUnit, java.util.concurrent.BlockingQueue<java.lang.Runnable>)
	 */
	public AddThreadBeforeQueuingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
			int creationThreshold) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		this.creationThreshold = creationThreshold;
	}

	/**
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long,
	 *      java.util.concurrent.TimeUnit, java.util.concurrent.BlockingQueue<java.lang.Runnable>,
	 *      java.util.concurrent.ThreadFactory)
	 */
	public AddThreadBeforeQueuingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
			int creationThreshold, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		this.creationThreshold = creationThreshold;
	}

	/**
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long,
	 *      java.util.concurrent.TimeUnit, java.util.concurrent.BlockingQueue<java.lang.Runnable>,
	 *      java.util.concurrent.RejectedExecutionHandler)
	 */
	public AddThreadBeforeQueuingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
			int creationThreshold, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
		this.creationThreshold = creationThreshold;
	}

	/**
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long,
	 *      java.util.concurrent.TimeUnit, java.util.concurrent.BlockingQueue<java.lang.Runnable>,
	 *      java.util.concurrent.ThreadFactory,
	 *      java.util.concurrent.RejectedExecutionHandler)
	 */
	public AddThreadBeforeQueuingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
			 int creationThreshold, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		this.creationThreshold = creationThreshold;
	}

	/**************************************************************************
	 * COMMENT: rschott 04.01.2007 15:25:48 <BR>
	 *		o
	 *
	 * @param creationThreshold
	 */
	public void setCreationThreshold( int creationThreshold) {
		this.creationThreshold = creationThreshold;
	}

	/**************************************************************************
	 * @return
	 */
	public int getCreationThreshold() {
		return this.creationThreshold;
	}

	/**************************************************************************
	 * packt den Job in die Warteschlange und erh�ht die Anzahl der Worker um 1, wenn noch Platz ist
	 * @see java.util.concurrent.Executor#execute(java.lang.Runnable)
	 * @param command
	 */
	@Override
	public void execute(Runnable command) {
		super.execute(command);
		final int poolSize = getPoolSize();
		if (poolSize < getMaximumPoolSize()) {
			if (getQueue().size() > creationThreshold) {
				synchronized (this) {
					setCorePoolSize(poolSize + 1);
					setCorePoolSize(poolSize);
				}
			}
		}
	}

	/**************************************************************************
	 * @see java.util.concurrent.ThreadPoolExecutor#afterExecute(java.lang.Runnable, java.lang.Throwable)
	 * @param r
	 * @param t
	 */
	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		super.afterExecute(r, t);
		if (t != null) {
			BenchLogger.syserr("Executor Runnable Error[" + r.toString() + "]", t);
		}
	}

	/**
	 * Beendet mich mit {@link #shutdown()} und wartet dann mit {@link #awaitTermination(long, TimeUnit)} auf die
	 * komplette Abarbeitung. Fall diese l�nger dauert als im Intervall angegeben, wird nach jedem Intervall in
	 * {@link IQLog#logTest} die �bergebene Nachricht mit der restlichen Jobanzahl geloggt. Werden vor Ende des ersten Intervalls
	 * alle Jobs abgearbeitet, wird nichts geloggt.
	 *
	 * @param time	Anzahl der TimeUnits
	 * @param unit	{@link TimeUnit}
	 * @param message	Nachricht zum Loggen
	 */
	public void shutdownAndWait( int time, TimeUnit unit, String message) {
		shutdown();
		boolean terminated = false;
		do {
			try {
				awaitTermination( time, unit);
				terminated = isTerminated();
				if ( ! terminated && ! Utils.isStringNullOrEmpty( message)) {
					BenchLogger.sysinfo( message + " " + getQueue().size() + " jobs left");
				}
			} catch( InterruptedException ie) {
				Thread dummy = getThreadFactory().newThread( null);
				BenchLogger.sysinfo( "waiting for TPE " + dummy.getName() + " interrupted");
			}
		} while ( ! terminated);
	}

}
