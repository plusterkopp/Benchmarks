package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;

public interface IWorkQueue {

	public abstract void startAllThreads(String id);

	public int stopWhenAllTaskFinished( String id);

	public void execute( Runnable t);

	public BlockingQueue<Runnable> createQueue();

	public abstract int getBatchCount();

	public abstract int getNumQueues();

	public abstract WorkAssignerThread newAssignerThread( Task[] tasks, long assignJobsPerSec);

	public abstract int getNumAssignerThreads();

	public abstract int getNumThreads();

}
