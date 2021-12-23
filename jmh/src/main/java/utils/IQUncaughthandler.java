package main.java.utils;

import java.lang.Thread.*;

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
	 * @see UncaughtExceptionHandler#uncaughtException(Thread, Throwable)
	 */
	@Override
	public void uncaughtException( Thread t, Throwable e) {
		System.out.println( "Error in Thread [" + t.getName() + "]");
		if (e != null) {
			e.printStackTrace( System.err);
		}
	}
}
