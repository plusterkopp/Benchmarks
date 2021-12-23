package de.icubic.mm.server.tests.performance.workerqueue;

import net.openhft.affinity.impl.LayoutEntities.*;

import java.util.concurrent.*;

public interface IWorkQueue {

	public  void startAllThreads(String id) throws InterruptedException;

	public int stopWhenAllTaskFinished( String id);

	public void execute( Runnable t);

	public BlockingQueue<Runnable> createQueue();

	public  int getBatchCount();

	public  int getNumQueues();

	public  WorkAssignerThread newAssignerThread( Task[] tasks, long assignJobsPerSec);

	public  int getNumAssignerThreads();

	public  int getNumThreads();

	public  void waitForWorkersCreated() throws InterruptedException;

	public  LayoutEntity getLayoutEntityFor( int threadIndex);

	public void createQueue(int threadIndex);

}
