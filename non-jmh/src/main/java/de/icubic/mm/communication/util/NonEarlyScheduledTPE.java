package de.icubic.mm.communication.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class NonEarlyScheduledTPE extends ScheduledThreadPoolExecutor {

    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong();

    public static final LongAdder EarlyCounter = new LongAdder();
    public static final LongAdder EarlyCumulated = new LongAdder();
    public static final LongAdder LatencyCumulated = new LongAdder();
    public static final LongAdder BothCumulated = new LongAdder();

    public static abstract class ScheduledRunnable implements Runnable {
    	volatile long	scheduledFor;
    	volatile long lastRunAt;
		public long getScheduledFor() {
			return scheduledFor;
		}
		public long getLastRunAt() {
			return lastRunAt;
		}

	    public abstract void run();
    }

	private class NonEarlyFutureTask<V>
		extends FutureTask<V>
		implements RunnableScheduledFuture<V>
	{

		/** Sequence number to break ties FIFO */
		private final long sequenceNumber;

		/** The nanoTime-based time when the task is enabled to execute. */
		private volatile long time;

		/**
		 * Period for repeating tasks, in nanoseconds.
		 * A positive value indicates fixed-rate execution.
		 * A negative value indicates fixed-delay execution.
		 * A value of 0 indicates a non-repeating (one-shot) task.
		 */
		private final long period;

		/** The actual task to be re-enqueued by reExecutePeriodic */
		RunnableScheduledFuture<V> outerTask = this;

		/**
		 * Index into delay queue, to support faster cancellation.
		 */
		int heapIndex;

		ScheduledRunnable scheduledRunable;

		/**
		 * Creates a one-shot action with given nanoTime-based trigger time.
		 */
		NonEarlyFutureTask(Runnable r, V result, long triggerTime, 	long sequenceNumber)
		{
			super(r, result);
			time = triggerTime;
			period = 0;
			this.sequenceNumber = sequenceNumber;
			if ( r instanceof ScheduledRunnable) {
				scheduledRunable = ( ScheduledRunnable) r;
			} else {
				scheduledRunable = null;
			}
		}

		/**
		 * Creates a periodic action with given nanoTime-based initial
		 * trigger time and period.
		 */
		NonEarlyFutureTask(Runnable r, V result, long triggerTime, long period, long sequenceNumber)
		{
			super(r, result);
			time = triggerTime;
			this.period = period;
			this.sequenceNumber = sequenceNumber;
			if ( r instanceof ScheduledRunnable) {
				scheduledRunable = ( ScheduledRunnable) r;
			} else {
				scheduledRunable = null;
			}
		}

		/**
		 * Creates a one-shot action with given nanoTime-based trigger time.
		 */
		NonEarlyFutureTask(Callable<V> callable, long triggerTime, long sequenceNumber)
		{
			super(callable);
			time = triggerTime;
			period = 0;
			this.sequenceNumber = sequenceNumber;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(time - System.nanoTime(), NANOSECONDS);
		}

		@Override
		public int compareTo(Delayed other) {
			if (other == this) { // compare zero if same object
				return 0;
			}
			if (other instanceof NonEarlyFutureTask) {
				NonEarlyFutureTask<?> x = (NonEarlyFutureTask<?>)other;
				long diff = time - x.time;
				if (diff < 0) {
					return -1;
				}
				if (diff > 0) {
					return 1;
				}
				if (sequenceNumber < x.sequenceNumber) {
					return -1;
				} else {
					return 1;
				}
			}
			long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
			return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
		}

		/**
		 * Returns {@code true} if this is a periodic (not a one-shot) action.
		 *
		 * @return {@code true} if periodic
		 */
		@Override
		public boolean isPeriodic() {
			return period != 0;
		}

		/**
		 * Sets the next time to run for a periodic task.
		 */
		private void setNextRunTime() {
			long p = period;
			if (p > 0) { // fixed-rate
				time += p;
			} else { // fixed delay
				time = triggerTime1(-p);
			}
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			// The racy read of heapIndex below is benign:
			// if heapIndex < 0, then OOTA guarantees that we have surely
			// been removed; else we recheck under lock in remove()
			boolean cancelled = super.cancel(mayInterruptIfRunning);
			if (cancelled && removeOnCancel && heapIndex >= 0) {
				remove(this);
			}
			return cancelled;
		}

		/**
		 * Overrides FutureTask version so as to reset/requeue if periodic.
		 */
		@Override
		public void run() {
	    	if ( rescheduleIfEarly( this)) {
	    		return;
	    	}
			if ( ! canRunInCurrentRunState1( this)) {
				cancel( false);
			} else if ( ! isPeriodic()) {
				super.run();
			} else if ( super.runAndReset()) {
				setNextRunTime();
				reExecutePeriodic1( outerTask);
			}
		}
	}

	/**
	 * True if ScheduledFutureTask.cancel should remove from queue.
	 */
    volatile boolean removeOnCancel;

	public NonEarlyScheduledTPE( int corePoolSize, ThreadFactory threadFactory) {
		super( corePoolSize, threadFactory);
	}

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtNanos(Runnable command, long nanos) {
        if (command == null) {
            throw new NullPointerException();
        }
        final long seq = sequencer.getAndIncrement();
		final NonEarlyFutureTask<Void> task = new NonEarlyFutureTask<Void>( command, null, nanos, seq);
		RunnableScheduledFuture<Void> t = decorateTask( command, task);
        delayedExecute(t);
        return t;
    }

	public ScheduledFuture<?> scheduleAtFixedRate(
		Runnable command, long initialDelay, long period, TimeUnit unit)
	{
		if ( command == null || unit == null) {
			throw new NullPointerException();
		}
		if ( period <= 0L) {
			throw new IllegalArgumentException();
		}
		final long triggerTime = triggerTime1( initialDelay, unit);
		final long seq = sequencer.getAndIncrement();
		final long periodNS = unit.toNanos( period);

		NonEarlyFutureTask<Void> sft = new NonEarlyFutureTask<Void>(
			command, null, triggerTime,
			periodNS, seq);

		RunnableScheduledFuture<Void> t = decorateTask( command, sft);
		sft.outerTask = t;
		delayedExecute( t);
		return t;
	}

    /**
     * Requeues a periodic task unless current run state precludes it.
     * Same idea as delayedExecute except drops task rather than rejecting.
     *
     * @param task the task
     */
    void reExecutePeriodic1(RunnableScheduledFuture<?> task) {
    	if ( rescheduleIfEarly( task)) {
    		return;
    	}
		if ( canRunInCurrentRunState1( task)) {
			super.getQueue().add( task);
			if ( canRunInCurrentRunState1( task) || ! remove( task)) {
				prestartCoreThread();
				return;
			}
		}
		task.cancel( false);
	}

    private boolean rescheduleIfEarly( RunnableScheduledFuture<?> task) {
    	if ( ! ( task instanceof NonEarlyFutureTask)) {
    		return false;
    	}
    	NonEarlyFutureTask neft = ( NonEarlyFutureTask) task;
    	long now = System.nanoTime();
    	long latency = now - neft.time;
    	if ( neft.scheduledRunable != null) {
    		neft.scheduledRunable.scheduledFor = neft.time;
    		neft.scheduledRunable.lastRunAt = now;
    	}
//    	if ( true) {
//    		return false;
//    	}
    	BothCumulated.add( latency);
    	if ( latency >= 0) {
    		LatencyCumulated.add( latency);
//    		return false;
    	} else {
    		EarlyCounter.increment();
    		EarlyCumulated.add( -latency);
    	}
    	return false;
//    	scheduleAtNanos( neft.outerTask, neft.time);
//		return true;
	}

	/**
    * Returns true if can run a task given current run state and
    * run-after-shutdown parameters.
    */
   boolean canRunInCurrentRunState1(RunnableScheduledFuture<?> task) {
    	// kann hier nicht noch auf isStopped testen wie in ScheduledThreadPoolExecutor, weil isStopped verrammelt ist
       if ( ! isShutdown()) {
           return true;
       }
       if ( task.isPeriodic()) {
    	   boolean continuePeriodic = getContinueExistingPeriodicTasksAfterShutdownPolicy();
    	   return continuePeriodic;
       }
       boolean executeDelayed = getExecuteExistingDelayedTasksAfterShutdownPolicy();
       final long delayNS = task.getDelay(NANOSECONDS);
       boolean expired = delayNS <= 0;
       return executeDelayed || expired;
//       return task.isPeriodic()
//           ? getContinueExistingPeriodicTasksAfterShutdownPolicy()
//           : (getExecuteExistingDelayedTasksAfterShutdownPolicy()
//              || task.getDelay(NANOSECONDS) <= 0);
   }

   /**
    * Returns the nanoTime-based trigger time of a delayed action.
    */
	private long triggerTime1( long delay, TimeUnit unit) {
		final long delayNS = unit.toNanos( ( delay < 0) ? 0 : delay);
		return triggerTime1( delayNS);
	}

	/**
	 * Returns the nanoTime-based trigger time of a delayed action.
	 */
	long triggerTime1( long delay) {
		return System.nanoTime() +
			( ( delay < ( Long.MAX_VALUE >> 1)) ? delay : overflowFree( delay));
	}

   /**
    * Constrains the values of all delays in the queue to be within
    * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
    * This may occur if a task is eligible to be dequeued, but has
    * not yet been, while some other task is added with a delay of
    * Long.MAX_VALUE.
    */
	private long overflowFree( long delay) {
		Delayed head = ( Delayed) super.getQueue().peek();
		if ( head != null) {
			long headDelay = head.getDelay( NANOSECONDS);
			if ( headDelay < 0 && ( delay - headDelay < 0)) {
				delay = Long.MAX_VALUE + headDelay;
			}
		}
		return delay;
	}

   /**
    * Main execution method for delayed or periodic tasks.  If pool
    * is shut down, rejects the task. Otherwise adds task to queue
    * and starts a thread, if necessary, to run it.  (We cannot
    * prestart the thread to run the task because the task (probably)
    * shouldn't be run yet.)  If the pool is shut down while the task
    * is being added, cancel and remove it if required by state and
    * run-after-shutdown parameters.
    *
    * @param task the task
    */
	private void delayedExecute( RunnableScheduledFuture<?> task) {
		if ( isShutdown()) {
			RejectedExecutionHandler handler = getRejectedExecutionHandler();
			handler.rejectedExecution( task, this);
		} else {
			super.getQueue().add( task);
			if ( ! canRunInCurrentRunState1( task) && remove( task)) {
				task.cancel( false);
			} else {
				prestartCoreThread();
			}
		}
	}


}
