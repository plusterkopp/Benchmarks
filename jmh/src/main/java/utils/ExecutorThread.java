/*
 * Created on 11.02.2005
 */
package utils;

import java.util.concurrent.Executor;

/**
 * Dieser Klasse merkt sich ihren Executor, um ihn dann sp�ter wieder abfragen zu k�nnen,
 * um neue Jobs an den gleichen Executor �bergeben zu k�nnen
 *
 * @author hpagenhardt
 */
public class ExecutorThread extends main.java.utils.IQuoteThread {
	final Executor executor;

	/**
	 * @param executor
	 */
	public ExecutorThread(Executor executor) {
		super();
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param target
	 */
	public ExecutorThread(Executor executor, Runnable target) {
		super(target);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param target
	 * @param name
	 */
	public ExecutorThread(Executor executor, Runnable target, String name) {
		super(target, name);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param name
	 */
	public ExecutorThread(Executor executor, String name) {
		super(name);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param group
	 * @param target
	 */
	public ExecutorThread(Executor executor, ThreadGroup group, Runnable target) {
		super(group, target);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param group
	 * @param target
	 * @param name
	 */
	public ExecutorThread(Executor executor, ThreadGroup group, Runnable target, String name) {
		super(group, target, name);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param group
	 * @param target
	 * @param name
	 * @param stackSize
	 */
	public ExecutorThread(Executor executor, ThreadGroup group, Runnable target, String name,
			long stackSize) {
		super(group, target, name, stackSize);
		this.executor = executor;
	}

	/**
	 * @param executor
	 * @param group
	 * @param name
	 */
	public ExecutorThread(Executor executor, ThreadGroup group, String name) {
		super(group, name);
		this.executor = executor;
	}

	/**
	 * @return Returns the executor.
	 */
	public Executor getExecutor() {
		return executor;
	}
}
