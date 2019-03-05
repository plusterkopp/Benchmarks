package de.icubic.mm.server.utils;

import de.icubic.mm.bench.base.BenchLogger;

import java.math.*;
import java.text.NumberFormat;
import java.util.*;

/**
 * von der Schnittstelle �bermittelte Informationen �ber relevante
 * Nachkommastellen, kleinste Preis�nderungen, kleinstes handelbares und
 * quotierbares Volumen, sowie die kleinste Volumen�nderung
 *
 * @author hkoenig, rhelbing (die Sachen, die NICHT funktionieren)
 */
public class QuotePrecision {

	public static enum PrintMode {
		BD {
			@Override
			public String formatDoubleRounded( double value, int digits) {
				final double firstDigitPlace = Math.log10(Math.abs(value));
				final int newScale;
				if (firstDigitPlace < 0) {
					newScale = 14 - (int)firstDigitPlace;
				} else {
					newScale = 13 - (int)firstDigitPlace;
				}
				String result = new BigDecimal( value).setScale( newScale, RoundingMode.HALF_UP).setScale( digits, RoundingMode.HALF_UP).toPlainString();
				return result;
			}

			@Override
			public String formatSingleRounded( double value, int digits) {
				String result = new BigDecimal( value).setScale( digits, RoundingMode.HALF_UP).toPlainString();
				return result;
			}

			@Override
			public double round( double value, int decimalplaces) {
				final double firstDigitPlace = Math.log10(Math.abs(value));
				final int newScale;
				if (firstDigitPlace < 0) {
					newScale = 14 - (int)firstDigitPlace;
				} else {
					newScale = 13 - (int)firstDigitPlace;
				}
				return new BigDecimal(value).setScale(newScale, RoundingMode.HALF_UP).setScale(decimalplaces,
					RoundingMode.HALF_UP).doubleValue();
			}
		},
		DS {
			@Override
			public String formatDoubleRounded( double value, int digits) {
				if ( outOfRange( digits)) {
					return BD.formatDoubleRounded( value, digits);
				}
				try {
					StringBuilder	sb = new StringBuilder( 100);
					DoubleToString.appendFormatted( sb, value, digits, '.',	DoubleToString.PRINT_NOTHING, 30, '-', DoubleToString.PRINT_NOTHING);
					String s = sb.toString();
					return s;
				} catch ( ArithmeticException ae) {
					BenchLogger.syserr( "can not format " + value + " with " + digits + " digits", ae);
					return BD.formatDoubleRounded( value, digits);
				}
			}

			private boolean outOfRange( int digits) {
				if ( digits < 0 || digits > 15)
					return true;
				return false;
			}

			@Override
			public String formatSingleRounded( double value, int digits) {
				return formatDoubleRounded( value, digits);
			}

			@Override
			public double round( double value, int digits) {
				String s = formatDoubleRounded( value, digits);
				try {
					return Double.parseDouble( s);
				} catch ( NumberFormatException nfe) {
					return Double.NaN;
				}
			}
		};

		public abstract String formatDoubleRounded( double value, int digits);
		public abstract String formatSingleRounded( double value, int digits);
		public abstract double round( double value, int decimalplaces);
	}

	/**
	 * wird in den statischen Methoden benutzt
	 */
	public static PrintMode DefaultPrintMode = PrintMode.BD;

	static EnumMap<PrintMode, QuotePrecision>	instances = new EnumMap<PrintMode, QuotePrecision>( PrintMode.class);
	public static final QuotePrecision BDInstance = new QuotePrecision( PrintMode.BD);
	public static final QuotePrecision DSInstance = new QuotePrecision( PrintMode.DS);
	static {
		instances.put( PrintMode.BD, BDInstance);
		instances.put( PrintMode.DS, DSInstance);
	}

	public static int DEFAULTSIGNIFICANTDIGITS = 8;

	public static double DEFAULTMINPRICEINCREMENT = 1.0E-8;

	/**
	 * kleinste Preis�nderung (Genauigkeit der Quotes/Orders), wird zuerst
	 * angewendet
	 */
	double minPriceIncrement = DEFAULTMINPRICEINCREMENT;

	/**
	 * Anzahl der Nachkommastellen des Preises, die an die Schnittstelle
	 * �bertragen werden<br>
	 * sollte immer ausreichen, um eine �nderung um
	 * <code>minPriceIncrement</code> sichtbar zu machen.
	 */
	int significantDigits = DEFAULTSIGNIFICANTDIGITS;

	/**
	 * 1/minPriceIncrement, zur Optimierung der Division
	 */
	double inverseMinPriceIncrement = 1.0 / DEFAULTMINPRICEINCREMENT;

	/**
	 * kleinstes handelbares Volumen
	 */
	double minTradableSize;

	/**
	 * kleinstes quotierbares Volumen
	 */
	double minQuotableSize;

	/**
	 * kleinste m�gliche Volumen�nderung
	 */
	double minSizeIncrement;

	/**
	 * true, wenn die letzte erlaubte Stelle noch in Halbschritte unterteilt
	 * werden kann, also zB eine Genauigkeit von 2.5 Stellen gew�nscht wird.
	 * Damit k�nnte dann eine Rendite wie 2.125 quotiert werden.<br>
	 * mu� persistiert werden
	 */
	boolean halfStep = false;

	/**
	 * Dividend, durch den die 10^-magnitude geteilt werden mu�, um
	 * <code>minPriceIncrement</code> zu erhalten. Bei glatten Zehnerpotenzen
	 * ist der Dividend 1 und <code>significantDigits</code> ist gleich
	 * <code>magnitude</code>. <br>
	 * Der Dividend darf nur Zweier- oder F�nferpotenzen enthalten, jedoch nicht
	 * durch zehn teilbar sein. Daraus folgt, da� er entweder Zweier- oder
	 * F�nferpotenzen enth�lt.<br>
	 * es gilt immer: dividend=dividendBase^maxPow25
	 */
	private int dividend = 1;

	/**
	 * Position der letzten Stelle nach dem Komma, an der jede beliebige Ziffer
	 * stehen kann.
	 */
	private int magnitude = 1;

	/**
	 * Anzahl der Stellen, die ben�tigt wird, um 1/dividend darzustellen. Ist
	 * gleich dem Maximum der Exponenten von 2 und 5 im Dividenden. Ist zB 2,
	 * wenn Dividend 4 ist, d.h. um eine Angabe in Viertelschritte zu teilen,
	 * braucht man zwei Nachkommastellen mehr.<br>
	 * es gilt immer: dividend=dividendBase^maxPow25
	 */
	private int maxPow25;

	/**
	 * die derzeitige Potenz der Basis des Dividenden. Solange dieser Wert
	 * zwischen 0 und Maxpow25 liegt, f�hrt eine Ver�nderung von
	 * significantDigits zum Hoch- bzw. Runterz�hlen des tats�chlichen
	 * Dividenden, d.h. realDividend=dividendBase^currentPow. Es wird also durch
	 * dividendBase geteilt/malgenommen.<br>
	 * Negative Werte zeigen an, da� durch 10 geteilt/multipliziert wird
	 */
	private int currentPow;

	/**
	 * Basis des Dividenden, kann nur 2 oder 5 sein, es gilt immer:
	 * dividend=dividendBase^maxPow25
	 */
	private int dividendBase;

	private PrintMode printMode = DefaultPrintMode;

	protected static final Map<Integer, QuotePrecision> qpMap = new HashMap<Integer, QuotePrecision>();

	/**
	 *
	 */
	public QuotePrecision() {
		this( PrintMode.BD);
	}

	/**
	 *
	 */
	public QuotePrecision( PrintMode mode) {
		super();
		setGranularity( DEFAULTMINPRICEINCREMENT);
		setMode( mode);
	}

	private void setMode( PrintMode mode) {
		printMode  = mode;
	}

	/**
	 * setzt das MinPriceIncement und dessen Reziprok. Berechnet auch den
	 * Divisor, die Anzahl der signifikanten Stellen und die Gr��enordnung fest.
	 *
	 * @param granularity
	 */
	public void setGranularity( double granularity) {
		setMinPriceIncrement( granularity);
		recompute();
	}

	public double getGranularity() {
		return getMinPriceIncrement();
	}

		/**
	 * nimmt das gerade gesetzte minPriceIncrement und den halfStep und
	 * berechnet alle abh�ngigen Gr��en neu: magnitude, dividend, dividendBase,
	 * maxPow25, currentPow, significantDigits<br>
	 * mu� die Randbedingungen f�r den Dividenden garantieren.<br>
	 * Wir gehen immer davon aus, da� inverseMinPriceIncrement gr��er als
	 * minPriceIncrement ist. Falls nicht, wird beides auf 1 gesetzt.
	 */
	private void recompute() {
		if ( minPriceIncrement > inverseMinPriceIncrement) {
//			IQLog.logQuoting.debug( "MPI: " + minPriceIncrement + "/" + inverseMinPriceIncrement);
		}
		long resolution = Math.round( inverseMinPriceIncrement);
		// dividiere alle Zehnerpotenzen aus resolution raus
		magnitude = 0;
		while ( resolution > 0 && ( resolution % 10 == 0)) {
			resolution /= 10;
			magnitude++;
		}
		// Sonderfall: zu kleine Aufl�sung, setze auf 1 und h�r auf
		if ( resolution == 0) {
			setGranularity( 1);
			return;
		}
		// eigentlich sollten wir damit den Dividenden schon haben
		dividend = ( int) resolution;
		// wir m�ssen davon ausgehen, da� divInt keine Primfaktoren au�er 2 und 5 hat
		int pow2 = getExponent( dividend, 2);
		int pow5 = getExponent( dividend, 5);
		// genaugenommen, kann es sogar nur EINEN geben
		if ( pow2 > 0) {
			dividendBase = 2;
		} else if ( pow5 > 0) {
			dividendBase = 5;
		} else {
			dividendBase = 1;
		}

		// falls nicht, tun wir so, als w�re nichts gewesen
		boolean unclean = hasOtherFactors( dividend, pow2, pow5);
		if ( unclean) {
			setGranularity( DEFAULTMINPRICEINCREMENT);
		} else {
			maxPow25 = Math.max( pow2, pow5);
			// wenn Halbschritt an sein soll (geht nur, wenn pow2 1 ist), dann
			// nimm sigDigits um eins zur�ck, dann mache, da� maxPow um eins
			// verrignert wird, damit man sp�ter halfStep noch setzen kann
			if ( pow2 == 1) {
				halfStep = true;
			} else {
				halfStep = false;
			}
			// die Anzahl der interessanten Stellen ist die Gr��enordnung plus
			// die h�chste Potenz aus 5 und 2 im Dividenden
			significantDigits = magnitude + maxPow25;
		}
		currentPow = maxPow25;
	}

	/**
	 * schaut, ob wir noch andere Primfaktoren als 2 und 5 haben
	 *
	 * @param num zu testende Zahl
	 * @param pow2 Potenz, zu der 2 enthalten ist
	 * @param pow5 Potenz, zu der 5 enthalten ist
	 * @return <code>true</code> wenn andere Primfaktoren als 2 und 5 in
	 *         <code>num</code> enthalten sind
	 */
	private boolean hasOtherFactors( int num, int pow2, int pow5) {
		int fac2 = 1;
		int fac5 = 1;

		while ( pow2 > 0) {
			pow2--;
			fac2 *= 2;
		}
		while ( pow5 > 0) {
			pow5--;
			fac5 *= 5;
		}
		// dividiere Zahl durch Produkt der Zweier- und F�nferpotenzen
		int r = num / ( fac5 * fac2);
		// dabei darf nichts �brig bleiben
		boolean result = ( r > 1);

		return result;
	}

	/**
	 * ermittelt die h�chste Potenz des Faktors in einer Zahl
	 *
	 * @param num
	 * @param factor
	 * @return wie oft man <code>num</code> ohne Rest durch
	 *         <code>factor</code> teilen kann
	 */
	private int getExponent( int num, int factor) {
		int exponent = 0;
		while ( num % factor == 0) {
			exponent++;
			num /= factor;
		}
		return exponent;
	}

	/**
	 * @return Returns the inverseMinPriceIncrement.
	 */
	private double getInverseMinPriceIncrement() {
		return inverseMinPriceIncrement;
	}

	/**
	 * @return Returns the minQuotableSize.
	 */
	public double getMinQuotableSize() {
		return minQuotableSize;
	}

	/**
	 * @param minQuotableSize The minQuotableSize to set.
	 */
	public void setMinQuotableSize( double minQuotableSize) {
		this.minQuotableSize = minQuotableSize;
	}

	/**
	 * @return Returns the minSizeIncrement.
	 */
	public double getMinSizeIncrement() {
		return minSizeIncrement;
	}

	/**
	 * @param minSizeIncrement The minSizeIncrement to set.
	 */
	public void setMinSizeIncrement( double minSizeIncrement) {
		this.minSizeIncrement = minSizeIncrement;
	}

	/**
	 * @return Returns the minPriceIncrement.
	 */
	public double getMinPriceIncrement() {
		return minPriceIncrement;
	}

	/**
	 * @param minTick The minTick to set.
	 */
	public void setMinPriceIncrement( double minTick) {
		if ( minTick < DEFAULTMINPRICEINCREMENT) {
			this.minPriceIncrement = DEFAULTMINPRICEINCREMENT;
			inverseMinPriceIncrement = 1.0 / DEFAULTMINPRICEINCREMENT;
		} else {
			this.minPriceIncrement = minTick;
			inverseMinPriceIncrement = 1.0 / minTick;
		}
	}

	/**
	 * @return Returns the minTradableSize.
	 */
	public double getMinTradableSize() {
		return minTradableSize;
	}

	/**
	 * @param minTradableSize The minTradableSize to set.
	 */
	public void setMinTradableSize( double minTradableSize) {
		this.minTradableSize = minTradableSize;
	}

	/**
	 * @return Returns the significantDigits.
	 */
	public int getSignificantDigits() {
		return significantDigits;
	}

	/**
	 * @param significantDigits The significantDigits to set.
	 */
	public void setSignificantDigits( int significantDigits) {
		if ( significantDigits > DEFAULTSIGNIFICANTDIGITS) {
			significantDigits = DEFAULTSIGNIFICANTDIGITS;
		}
		adjustSignificantDigits( this.significantDigits - significantDigits);
		this.significantDigits = significantDigits;
	}

	/**
	 * bei diff > 0 wird die Genauigkeit kleiner
	 *
	 * @param diff
	 */
	private void adjustSignificantDigits( int diff) {
		while ( diff > 0) {
			decreaseSignificantDigits();
			diff--;
		}
		while ( diff < 0) {
			increaseSignificantDigits();
			diff++;
		}
		// aktualisiere Halfstep, falls wir keine Zehnerpotenz springen mu�ten
		halfStep = ( dividendBase == 2) && ( currentPow == 1);
	}

	private void decreaseSignificantDigits() {
		// mache nichts, wenn wir keine sig Stellen mehr haben
		if ( significantDigits <= 0)
			return;
		// Aufl�sung verringern, zun�chst nur den Dividenden
		if ( maxPow25 > 0 && currentPow > 0) {
			minPriceIncrement *= dividendBase;
			inverseMinPriceIncrement /= dividendBase;
			significantDigits--;
		} else {
			minPriceIncrement *= 10;
			inverseMinPriceIncrement /= 10;
			magnitude--;
			significantDigits--;
		}
		currentPow--;
	}

	private void increaseSignificantDigits() {
		// Aufl�sung erh�hen, zun�chst nur den Dividenden
		if ( maxPow25 > 0 && currentPow >= 0 && currentPow < maxPow25) {
			minPriceIncrement /= dividendBase;
			inverseMinPriceIncrement *= dividendBase;
			significantDigits++;
		} else {
			minPriceIncrement /= 10;
			inverseMinPriceIncrement *= 10;
			magnitude++;
			significantDigits++;
		}
		currentPow++;
	}

	/**
	 * @return liefert halfStep.
	 */
	public boolean isHalfStep() {
		return halfStep;
	}

	/**
	 * wenn true, wird au�erdem dividend, minPriceIncrement und inverse angepa�t
	 *
	 * @param halfStep neue(r,s) halfStep
	 */
	public void setHalfStep( boolean halfStep) {
		if ( this.halfStep == halfStep) {
			return;
		}
		// schalte den Dividenden zwischen 1 und 2 um, je nach Halfstep, aber
		// nur, wenn wir glatte Zehnerpotenzen ohne Unterteilung haben. Die
		// wiederum haben wir, wenn wir nicht mehr signifikante Stellen haben
		// als die letzte nicht unterteilte Stelle.
		if ( halfStep && dividend == 1 && minPriceIncrement < 0.5) {
			this.halfStep = halfStep;
			dividend = 2;
			minPriceIncrement *= 5;
			inverseMinPriceIncrement /= 5;
		} else if ( ! halfStep && dividend == 2) {
			this.halfStep = halfStep;
			dividend = 1;
			minPriceIncrement /= 5;
			inverseMinPriceIncrement *= 5;
		}
	}

	/**
	 * Ist in nf nicht thread-safe!
	 *
	 * @param value
	 * @param nf sollte nur innerhalb synchronisierter Kontexte benutzt werden
	 * @return
	 */
	public String format( double value, NumberFormat nf) {
		int digits = getSignificantDigits();
		double real = value * getInverseMinPriceIncrement();
		double integer = Math.round( real);
		double rounded = integer * getMinPriceIncrement();
		nf.setMaximumFractionDigits( digits);
		nf.setMinimumFractionDigits( 1);
		return nf.format( rounded);
	}

	/**
	 * rundet einen �bergebenen Wert mit den aktuellen Rundungsregeln vor.
	 * Formatiert ihn daf�r zuerst, und baut aus dem formatierten String eine
	 * neue Zahl.<br>
	 * Wird (ua) ben�tigt, um eine vom H�ndler eingegebene Spanne zu runden, so
	 * da� die daraus berechneten Spreads bzw Bid/Ask-Seiten mit den
	 * Rundungsregeln konsistent sind.
	 *
	 * @param value
	 * @return gerundeter Wert
	 */
	public double round(final double value) {
		return round0(value, getSignificantDigits());
	}

	/**
	 * kann benutzt werden, um sicherzustellen, da� eine QP eine genauere
	 * Pr�zision zul��t als ich.
	 *
	 * @param other
	 * @return true wenn other mehr Stellen etc erlaubt als ich
	 */
	public boolean hasHigherPrecisionThan( QuotePrecision other) {
		// die erste Rundung ist anhand von minPriceIncrement, also testen wir das zuerst
		if ( this.minPriceIncrement != other.minPriceIncrement) {
			return this.minPriceIncrement < other.minPriceIncrement;
		}
		// den Rest solten wir nicht mehr brauchen, weil alle drei Felder
		// miteinander abgeglichen werden
		if ( this.significantDigits != other.significantDigits) {
			return this.significantDigits > other.significantDigits;
		}
		return false;
	}

	/**
	 * Erzeugt eine Kopie der QuotePrecision
	 *
	 * @return Kopie de QuotePrecision
	 */
	public QuotePrecision copy() {
		QuotePrecision quotePrecision = new QuotePrecision();
		// ihre Granularit�t auf mein MPI setzen, das erledigt den Rest von selbst
		quotePrecision.setGranularity( getMinPriceIncrement());
		quotePrecision.setMinTradableSize( getMinTradableSize());
		quotePrecision.setMinQuotableSize( getMinQuotableSize());
		quotePrecision.setMinSizeIncrement( getMinSizeIncrement());
		return quotePrecision;
	}

	public int getDividend() {
		return dividend;
	}

	public int getMagnitude() {
		return magnitude;
	}

	/**************************************************************************
	 * COMMENT: rschott 15.01.2007 16:35:53 <BR>
	 *		o	bastelt einen String zusammen der die gerundete inverseMinPriceIncrement
	 *			, die significantDigits und wenn er halfStep ist H enthaelt
	 *		o 	wird ueber einen Stringbuilder zusammengebastelt
	 *
	 * @see java.lang.Object#toString()
	 * @return
	 */
	@Override
	public String toString() {
		StringBuilder buffy = new StringBuilder().append("QP");
		buffy.append(Math.round( inverseMinPriceIncrement));
		buffy.append("/");
		buffy.append(significantDigits);
		if (halfStep){
			buffy.append("H");
		}
		return buffy.toString();
	}

	public static QuotePrecision getQuotePrecision( int places) {
		Integer pl = Integer.valueOf( places);
		QuotePrecision qp = qpMap.get( pl);
		if ( qp == null) {
			qp = new QuotePrecision();
			qp.setSignificantDigits( places);
			qpMap.put( pl, qp);
		}
		return qp;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ( halfStep ? 1231 : 1237);
		long temp;
		temp = Double.doubleToLongBits( minPriceIncrement);
		result = prime * result + ( int) ( temp ^ ( temp >>> 32));
		temp = Double.doubleToLongBits( minQuotableSize);
		result = prime * result + ( int) ( temp ^ ( temp >>> 32));
		temp = Double.doubleToLongBits( minSizeIncrement);
		result = prime * result + ( int) ( temp ^ ( temp >>> 32));
		temp = Double.doubleToLongBits( minTradableSize);
		result = prime * result + ( int) ( temp ^ ( temp >>> 32));
		result = prime * result + significantDigits;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals( Object obj) {
		if ( this == obj)
			return true;
		if ( obj == null)
			return false;
		if ( getClass() != obj.getClass())
			return false;
		final QuotePrecision other = ( QuotePrecision) obj;
		if ( halfStep != other.halfStep)
			return false;
		if ( Double.doubleToLongBits( minPriceIncrement) != Double.doubleToLongBits( other.minPriceIncrement))
			return false;
		if ( Double.doubleToLongBits( minQuotableSize) != Double.doubleToLongBits( other.minQuotableSize))
			return false;
		if ( Double.doubleToLongBits( minSizeIncrement) != Double.doubleToLongBits( other.minSizeIncrement))
			return false;
		if ( Double.doubleToLongBits( minTradableSize) != Double.doubleToLongBits( other.minTradableSize))
			return false;
		if ( significantDigits != other.significantDigits)
			return false;
		return true;
	}

	/**
	 *
	 */
	public static String format( double value, boolean stripZerosAndPeriod, boolean isRoundable, double border, int defaultDecimalPlaces) {
		QuotePrecision qp = instances.get( DefaultPrintMode);
		return qp.format0( value, stripZerosAndPeriod, isRoundable, border, defaultDecimalPlaces);
	}

	/**
	 * Formatiert einen Double Wert und gibt diesen als String zur�ck. Dabei wird
	 * anhand der border entschieden, ob auf 0, 2 oder 3 stellen gerundet wird.
	 *
	 * @param value						zu rundender Wert
	 * @param stripZerosAndPeriod		true: 0 und Kommazeichen wird abgeschnitten.
	 * @param isRoundable				true, f�r automatische Rundung je nach value
	 * @param border					Grenze f�r value, unter der auf 3 Stellen gerundet wird
	 * @param defaultDecimalPlaces		Standard-Dezimalstellen, falls keine automatische Rundung
	 * @return							gerundeter Wert als String
	 */
	public String format0( double value, boolean stripZerosAndPeriod, boolean isRoundable, double border, int defaultDecimalPlaces) {
		final String roundedValue;
		if ( Double.isNaN( border) || ( ! isRoundable)) {
			roundedValue = checkAndformat0( value, stripZerosAndPeriod, defaultDecimalPlaces);
		} else {
			int dp = getDecimalPlacesByBorder( Math.abs( value), border);
			roundedValue = format0( value, stripZerosAndPeriod, dp);
		}
		return roundedValue;
	}

	/**
	 * Pr�ft auf wieviele Stellen nach dem Komma gerundet werden soll. Dabei wird
	 * anhand der border entschieden, ob auf 0, 2 oder 3 stellen gerundet wird.
	 * @param value 			zu rundender Wert
	 * @param border 			Grenze unter der auf 3 Stellen gerundet wird
	 * @return 					<ul><li>0 bei ganzen Zahlen, <li>2, wenn value > border, <li>sonst 3
	 */
	public static int getDecimalPlacesByBorder( double value, double border) {
		if ( Math.rint( value) == value) {
			return 0;		// ganze Zahl? -> 0 Stellen
		} else if ( value > border) {
			return 2;	// normalerweise zwei Stellen
		} else {
			return 3;	// unterhalb 1 drei Stellen
		}
	}

	/**
	 * formatiere double, anhand des value wird entschieden, ob auf 0 oder auf 'places' stellen gerundet wird.
	 * @param value 				zu rundender Wert
	 * @param stripZerosAndPeriod 	true: 0 und Periode wird abgeschnitten.
	 * @param places 				Anzahl der Stellen auf die gerundet wird, wenn es keine ganze Zahl ist
	 * @return 						gerundeter Wert als String
	 */
	public static String checkAndformat( double value, boolean stripZerosAndPeriod, int places) {
		QuotePrecision qp = instances.get( DefaultPrintMode);
		return qp.checkAndformat0( value, stripZerosAndPeriod, places);
	}

	public String checkAndformat0( double value, boolean stripZerosAndPeriod, int places) {
		if ( Math.rint( value) == value) {
			return format0( value, stripZerosAndPeriod, 0);		// ganze Zahl? -> 0 Stellen
		}
		return format0( value, stripZerosAndPeriod, places);	// unterhalb 1 drei Stellen
	}

	public static String format(double value, boolean stripZerosAndPeriod, int digits) {
		QuotePrecision qp = instances.get( DefaultPrintMode);
		return qp.format0( value, stripZerosAndPeriod, digits);
	}

	/**
	 * formatiere double, runde auf die angegebene Anzahl an nachkommstellen
	 * @param value zu rundender Wert
	 * @param stripZerosAndPeriod true: 0 und Periode wird abgeschnitten.
	 * @param digits Anzahl der Nachkommastellen
	 * @return gerundeter Wert als String
	 */
	public String format0(double value, boolean stripZerosAndPeriod, int digits) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return Double.toString(value);
		}
		String result;
		if (value == 0.0) {
			result = "0.0";
		} else {
			result = printMode.formatDoubleRounded( value, digits);
		}
		result = stripZerosAndPeriod( result, stripZerosAndPeriod);
		// hier m��ten wir auf -0 testen, was bei kleinen negativen Werten rauskommen kann und bei QuotePrecision(BigDecimal) nicht.
		if ( "-0".equals( result))
			return "0";

		return result;
	}

	/**
	 * Nimmt den Punkt als Dezimalzeichen an. Entfernt immer den Punkt, wenn er am Ende von number steht. Tut dar�ber hinaus nichts, wenn stripZerosAndPeriod false ist.
	 * Falls auch
	 * noch das Entfernen der Endnullen gew�nscht ist, werden alle Nullen von rechts entfernt. Gibt es au�er den
	 * entfernten Nullen keine weiteren Nachkommastellen mehr, wird auch das Dezimalzeichen mit entfernt.
	 *
	 * @param numberS
	 * @param stripZerosAndPeriod
	 * @return
	 */
	public static String stripZerosAndPeriod( String numberS, boolean stripZerosAndPeriod) {
		// wenn String auf "." endet, hacke den Punkt weg und raus: auch wenn stripZerosAndPeriod = false
		if ( numberS.isEmpty()) {
			return numberS;
		}
		final int maxIndex = numberS.length() - 1;
		if ( numberS.charAt( maxIndex) == '.') {
			return numberS.substring(0, maxIndex);
		}
		// das eigentliche Weghacken der Nullen und des evtl verbleibenden Punktes nur auf Anforderung
		if ( ! stripZerosAndPeriod)
			return numberS;
		// hacke Nullen am Ende weg
		if ( numberS.contains(".")) {	// nur wenn ein Dezimalzeichen im String ist
			int index = maxIndex;
			while (index >= 0 && numberS.charAt(index) == '0') {
				--index;
			}
			// wir haben mindestens eine Null �bersprungen, stehen auf einer Nicht-Null: gib String bis einschlie�lich Index zur�ck
			if ( index < maxIndex) {
				// wir stehen nicht mehr auf einer 0. Da wir aber mindestens ein Nicht-"0"-Zeichen hatten, mu� index >= 0 sein
				if ( numberS.charAt(index) == '.') {
					// wir sind jetzt beim Komma: das auch noch weg, wenn es bis auf Nullen das letzte Zeichen war
					return numberS.substring(0, index);
				}
				return numberS.substring(0, index + 1);
			}
		}
		// in allen anderen F�llen: gibt den unver�nderten String zur�ck
		return numberS;
	}

	/**
	 * formatiere double, runde auf relevante Nachkommastelle und schneide anh�ngende Nullen ab, Siehe BUG 11983
	 *
	 * @param value
	 *            zu rundender Wert
	 * @return gerundeter Wert als Double
	 */
	public static String format(double value) {
		QuotePrecision qp = instances.get( DefaultPrintMode);
		return qp.format0( value);
	}

	private String format0( double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return Double.toString(value);
		}
		String result;
		if (value == 0.0) {
			result = "0.0";
		} else {
			final double firstDigitPlace = Math.log10(Math.abs(value));
			final int newScale;
			if (firstDigitPlace < 0) {
				newScale = 14 - (int)firstDigitPlace;
			} else {
				newScale = 13 - (int)firstDigitPlace;
			}
			result = printMode.formatSingleRounded( value, newScale);
			result = stripZerosAndPeriod( result, true);
//			BigDecimal temp = new BigDecimal(value).setScale(newScale, RoundingMode.HALF_UP);
//			// hacke Nullen am Ende weg
//			result = temp.stripTrailingZeros().toPlainString();
		}
		return result;
	}

	/**
	 * Rundet den �bergebenen Wert auf die gew�nschte Anzahl an Nachkommastellen
	 *
	 * @param value			der zu rundende Wert
	 * @param decimalplaces	die Anzahl der Nachkommastellen
	 * @return				der gerundete Wert
	 */
	public static final double round(final double value, final int decimalplaces) {
		QuotePrecision qp = instances.get( DefaultPrintMode);
		return qp.round0( value, decimalplaces);
	}

	public double round0( double value, int decimalplaces) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
			return value;
		}
		return printMode.round( value, decimalplaces);
	}

	public static double round( double toRound, double decimalPlaceBorder) {

		if ( Double.isNaN( toRound)) {
			return Double.NaN;
		}

		int	places = getDecimalPlacesByBorder( toRound, decimalPlaceBorder);
		final double rounded = round( toRound, places);
//		IQLog.logHistory.system( "rounding " + toRound + " to " + places + " DP: " + rounded);
		return rounded;
	}
}
