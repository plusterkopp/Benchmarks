/**************************************************************************
 * COMMENT: rschott 29.11.2007 10:44:26 <BR>
 *		o
 *
 */
package de.icubic.mm.communication.util;

/**************************************************************************
 * COMMENT: rschott 29.11.2007 10:25:06 <BR>
 *		o	Eventuell bringt dieser Versuch ein wenig Einheitlichkeit rein
 *		o	Jeder der am Server einen Thread haben moechte sollte ihn aus dieser
 *			Klasse erzeugen und somit sicherstellen, dass auch die noetigen
 *			Vorbedingungen eingehalten werden
 *		o	Erste Vorbedingung ist eine Initialisierung mittels Uncaughthandler
 *			welcher auf VirtualMAchineErrors mit Exit reagiert
 *			Der normale DefaultHandler gibt nur eine Ausgabe in den Err Trace ab
 *		o	Wenn sich alle hier ihren Thread abholen kann man das auch erweitern und
 *			bestimmte Trace uns Debug Threads zur Laufzeit erzeugen
 *
 *		o	Alle Defaultkonstruktoren werden ueberlagert um den Thread mit einem Defaultuncoughthandler
 *			zu initialisieren
 *		o	TODO: rschott - 29.11.2007 betsimmte SystemThreads wie RMI TCP usw werden noch nicht
 *			mit dem defaultUncaughtHandler versehen und koennen weiterhin im Err landen und
 *			nicht den Server beenden. Der Ansatz hier ist das der Server irgendwann in einen
 *			Thread bereat der den handler besitzt und diesen dann auch sauber beendet
 *			- Alternative koennte der IQOwerflowWatcher permanent die laufenden Threads untersuchen und
 *			wenn noetig den handler registrieren - aber das Spar ich mir erstmal noch
 *
 *
 * @return Thread mit defaultUncaughthandler zum beenden bei unerwarteten Ereignissen.
 */
public class IQuoteThread extends Thread // implements ISingles
{
	/**************************************************************************
	 * COMMENT: rschott 28.11.2007 16:52:17 <BR>
	 *		o	Dieser handler sollte an allen Thread die der Server erzeugt
	 *			angeheftet werden damit er sich sauber beenden kann fals irgendwas
	 *			Unerwartetes passiert
	 *		o	zb Outofmemory Exception
	 *		o	public damit jeder Thread diesen Handler verwenden kann
	 *
	 */
	public static final IQUncaughthandler defaultUncaughtHandler = new IQUncaughthandler();

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:50:21 <BR>
	 *		o
	 *
	 */
	public IQuoteThread()
	{
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param target
	 * @param name
	 */
	public IQuoteThread(Runnable target, String name)
	{
		super(target, name);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param target
	 */
	public IQuoteThread(Runnable target)
	{
		super(target);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param name
	 */
	public IQuoteThread(String name)
	{
		super(name);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param group
	 * @param target
	 * @param name
	 * @param stackSize
	 */
	public IQuoteThread(ThreadGroup group, Runnable target, String name, long stackSize)
	{
		super(group, target, name, stackSize);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param group
	 * @param target
	 * @param name
	 */
	public IQuoteThread(ThreadGroup group, Runnable target, String name)
	{
		super(group, target, name);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param group
	 * @param target
	 */
	public IQuoteThread(ThreadGroup group, Runnable target)
	{
		super(group, target);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}

	/**************************************************************************
	 *	COMMENT: rschott 29.11.2007 10:54:09 <BR>
	 *		o
	 *
	 * @param group
	 * @param name
	 */
	public IQuoteThread(ThreadGroup group, String name)
	{
		super(group, name);
		setDefaultUncaughtExceptionHandler(defaultUncaughtHandler);
	}


}
