/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.base;

import java.util.concurrent.*;

/**
 * <p>@author ralf
 *
 */
public interface IBenchRunner extends Runnable {

	/**
	 * setzt die gewünschte Laufzeit des Tests. Dazu kommt noch die Laufzeit für
	 * die Kalibrierungsläufe.
	 *
	 * @param tu
	 * @param units
	 */
	void setRuntime( TimeUnit tu, long units);

	/**
	 * setzt den zu testenden Benchmark
	 * @param runner
	 */
	void setBenchRunner( IBenchRunnable runner);

	/**
	 * erst sinnvoll, nachdem run() aufgerufen wurde
	 * @return Anzahl der Durchläufe innerhalb der gewünschten Laufzeit
	 */
	long getRuns();

	/**
	 * @return Durchläufe je Sekunde
	 */
	double getRunsPerSecond();

	/**
	 * @return tatsächliche Laufzeit in Sekunden
	 */
	double getRunSeconds();

	/**
	 * formatierte Ausgabe
	 */
	void printResults();

	/**
	 * wieviele leere Schleifen schaffen wir pro Sekunde (Vorgabe: unendlich viele)
	 * @param eRuns
	 */
	void setEmptyLoops( double eRuns);

	/**
	 * @return Name des letzten IBenchRunnables
	 */
	String getName();

}
