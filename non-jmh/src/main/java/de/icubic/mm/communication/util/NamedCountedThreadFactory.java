package de.icubic.mm.communication.util;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedCountedThreadFactory implements ThreadFactory {
	final AtomicInteger	threadNumber = new AtomicInteger( 0);
	final String baseName;
	Executor	executor;
	private UncaughtExceptionHandler uncaughtExceptionHandler;

	public NamedCountedThreadFactory( String baseName, Executor executor, UncaughtExceptionHandler uncaughtExceptionHandler) {
		this.baseName = baseName;
		this.executor = executor;
		this.uncaughtExceptionHandler = uncaughtExceptionHandler;
	}

	public NamedCountedThreadFactory( String baseName, UncaughtExceptionHandler uncaughtExceptionHandler) {
		this.baseName = baseName;
		this.uncaughtExceptionHandler = uncaughtExceptionHandler;
	}

	@Override
	public Thread newThread( Runnable r) {
		Executor ex;
		synchronized ( this) {
			ex = executor;
		}
		Thread t;
		if ( ex != null) {
			t = new ExecutorThread( ex, r, baseName + "-" + threadNumber.incrementAndGet());
		} else {
			t = new IQuoteThread( r, baseName + "-" + threadNumber.incrementAndGet());
		}
		if ( uncaughtExceptionHandler != null) {
			t.setUncaughtExceptionHandler( uncaughtExceptionHandler);
		}
		return t;
	}

	public synchronized void setExecutor( Executor ex) {
		executor = ex;
	}

}
