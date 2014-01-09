package de.icubic.mm.communication.util;

import java.lang.Thread.UncaughtExceptionHandler;

import de.icubic.mm.bench.base.*;

/******************************************************************************
 * Alle Threads im IQuote Server sollten diesen Handler implementieren und koennen somit auf unerwartete Exceptions
 * mit zb. einem Beenden des Servers reagieren. Eigentlich sollte eine Instance dieses Handlers fuer alle zb am Model
 * bereitgestellt werden.
 *
 * @author rschott
 *
 */
public class IQUncaughthandler implements UncaughtExceptionHandler {
	/**************************************************************************
	 * Dieser Handler gibt alle unerwartetn Ereignisse an das Loggfile ab und beendet den Server, wenn bestimmte arten
	 * von Exception auftreten dazu zaehlen die QutOfMemoryExceptions welche ab hier auf jeden Fall zu einem Beenden des
	 * Servers fuehrt.
	 *
	 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
	 */
	@Override
	public void uncaughtException( Thread t, Throwable e) {
		BenchLogger.syserr( "Error in Thread [" + t.getName() + "]", e);
	}
}
