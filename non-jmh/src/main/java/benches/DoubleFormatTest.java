package benches;

import static org.junit.Assert.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;
import de.icubic.mm.server.utils.QuotePrecision.*;

public class DoubleFormatTest {

	static private final ThreadLocal<NumberFormat> NfTL = new ThreadLocal<NumberFormat>() {
		@Override
		protected NumberFormat initialValue() {
			final NumberFormat numberInstance = DecimalFormat.getNumberInstance( Locale.US);
			numberInstance.setGroupingUsed( false);
			return numberInstance;
		}
	};

	public enum RunMode {
			SC {
				@Override
				public String getStringValue( double value) {
					return null;
				}

				@Override
				public double round( double value, int dp) {
					return 0;
				}
			},
			BD {
				@Override
				public String getStringValue( double value) {
					return QuotePrecision.BDInstance.checkAndformat0( value, true, 8);
				}

				@Override
				public double round( double value, int dp) {
					return QuotePrecision.BDInstance.round0( value, dp);
				}
			},
			NF {
				@Override
				public String getStringValue( double value) {
					NumberFormat	nf = NfTL.get();
					if ( Math.rint( value) == value) {
						nf.setMaximumFractionDigits( 0);
					} else {
						nf.setMaximumFractionDigits( 8);
					}
					String s = nf.format( value);
					s = QuotePrecision.stripZerosAndPeriod( s, true);
					// hier müßten wir auf -0 testen, was bei kleinen negativen Werten rauskommen kann und bei QuotePrecision(BigDecimal) nicht.
					if ( "-0".equals( s))
						return "0";
					return s;
				}

				@Override
				public double round( double value, int dp) {
					return 0;
				}
			},
			DS {
				@Override
				public String getStringValue( double value) {
					final int maximumFractionDigits = 8;
					return QuotePrecision.DSInstance.checkAndformat0( value, true, maximumFractionDigits);
				}
				@Override
				public double round( double value, int dp) {
					return QuotePrecision.DSInstance.round0( value, dp);
				}
			};

			public abstract String getStringValue( double value);

			public abstract double round( double value, int dp);
		}

	private static Double[] values;
	private static String[] strings;

	public static void setupValueList( List<Double> plusMinusValues, double extraValues[]) {
		if ( extraValues != null) {
			for ( double d : extraValues) {
				plusMinusValues.add( Double.valueOf( d));
				plusMinusValues.add( Double.valueOf( -d));
			}
		}
		NumberFormat	nfnf = DecimalFormat.getNumberInstance( Locale.US);
		nfnf.setMaximumFractionDigits( 7);
		// alle Werte zwischen 0 und 100 in zweihunderstel Schritten
		for ( int intPart = 0;  intPart < 100;  intPart++) {
			for ( int fracPart = 0;  fracPart < 200;  fracPart++) {
				final double v = intPart + ( fracPart / 200.0);
				String	s = nfnf.format( v);
				plusMinusValues.add( Double.valueOf( s));
			}
		}
		// alle Werte zwischen 0 und 1 in 2000stel Schritten
		for ( int fracPart = 0;  fracPart < 2000;  fracPart++) {
			final double v = ( fracPart / 2000.0);
			String	s = nfnf.format( v);
			plusMinusValues.add( Double.valueOf( s));
		}
		// nun noch viele Zufallszahlen
		int	rndCount = 100000;
		double	scale = 10000;
		Random rnd = new Random( 0);
		for ( int scaleDigit = 5;  scaleDigit > -1;  scaleDigit--) {
			for ( int r = 0;  r < rndCount;  r++) {
				double d = rnd.nextDouble() * scale;
				plusMinusValues.add( Double.valueOf( d));
				plusMinusValues.add( Double.valueOf( -d));
			}
			scale /= 10;
		}
	}

	private static void setupArrays( List<Double> plusMinusValues) {
		values = new Double[ plusMinusValues.size()];
		for ( int i = 0;  i < plusMinusValues.size();  i++) {
			values[ i] = plusMinusValues.get( i);
		}
		strings = new String[ values.length];
		for ( int i = 0;  i < strings.length;  i++) {
			strings[ i] = "" + values[ i];
		}
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBigNums() {
		Random	rndgen = new Random( 0);
		int maxRuns = 100;
		for ( int mag = -20;  mag < 20;  mag++) {
			double dim = Math.pow( 10, mag);
			for ( int run = 0;  run < maxRuns;  run++) {
				double rnd = rndgen.nextDouble();
				double num = dim * rnd;
				for ( int prec = -3;  prec < 20;  prec++) {
					try {
						String sDS = PrintMode.DS.formatDoubleRounded( num, prec);
						if ( sDS.isEmpty()) {
							sDS = PrintMode.DS.formatDoubleRounded( num, prec);
						}
						sDS = QuotePrecision.stripZerosAndPeriod( sDS, false);
						String sBD = PrintMode.BD.formatDoubleRounded( num, prec);
						if ( ! sDS.equals( sBD)) {	// gib sämtliche Abweichungen aus
							BenchLogger.sysout( "Round mismatch for " + num + " with " + prec + " digits: " + sDS + " != " +sBD);
						}
						int	precBD = mag + prec;	// wenn das gr��er als 13 wird, weichen sie immer ab, weil unsere BD-Methode auf 13 Stellen begrenzt ist
						if ( precBD < 13) {
							assertEquals( "Round mismatch for " + num + " with " + prec + " digits" , sBD, sDS);
						}
					} catch ( Exception e) {
						BenchLogger.syserr( "Exception for " + num + " with " + prec + " digits", e);
					}
				}
			}
		}
	}

	@Test
	public void testStripZerosAndPeriod() {
		double	numbers[] = { 10, 1, 20.7, 1.23456, 0.003};
		String		stripped[] = { "10", "1", "20.7", "1.23", "0"};
		String		twoFracts[] = { "10.00", "1.00", "20.70", "1.23", "0.00"};
		String		oneFracts[] = { "10.0", "1.0", "20.7", "1.2", "0.0"};
		for ( int i = 0;  i < numbers.length;  i++) {
			double d = numbers[ i];
			String	raw2fracts = QuotePrecision.DSInstance.format0( d, false, 2);
			String	raw1fracts = QuotePrecision.DSInstance.format0( d, false, 1);
//			try {
				assertEquals( "Raw mismatch", twoFracts[ i], raw2fracts);
				assertEquals( "Raw mismatch", oneFracts[ i], raw1fracts);
//			} catch ( AssertionError ae) {
//				QuotePrecision.DSInstance.format0( d, false, 2);
//				throw ae;
//			}
			String	s = QuotePrecision.stripZerosAndPeriod( raw2fracts, true);
			assertEquals( "Stripped mismatch", stripped[ i], s);
		}
		// endet in "." -> Punkt weg auch bei false
		assertEquals( "Stripped mismatch", "10", QuotePrecision.stripZerosAndPeriod( "10.", true));
		assertEquals( "Stripped mismatch", "10", QuotePrecision.stripZerosAndPeriod( "10.", false));
		// der muß so bleiben
		assertEquals( "Stripped mismatch", "10.000", QuotePrecision.stripZerosAndPeriod( "10.000", false));
		assertEquals( "Stripped mismatch", "1004", QuotePrecision.stripZerosAndPeriod( "1004", true));
		// der nicht
		assertEquals( "Stripped mismatch", "10.04", QuotePrecision.stripZerosAndPeriod( "10.0400", true));
	}

	@Test
	public void testXFormat() {
		List<Double> plusMinusValues = new ArrayList<Double>();
		setupValueList( plusMinusValues, null);
		setupArrays( plusMinusValues);
		checkFirst();
		checkEquals();
	}

	private static void checkEquals() {
		BenchLogger.sysout( "Testing " + values.length + " Numbers");
		long	now = BenchRunner.getNow();
		ExecutorService	es = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors());
		final AtomicInteger	misMatches = new AtomicInteger( 0);
		for ( int i = 0;  i < values.length;  i++) {
			final double 	value = values[ i].doubleValue();
			final String		s = strings[ i];
			// ja, die einzelnen Jobs schreiben alle gleichzeitig auf System.err
			Runnable job = new Runnable() {
				@Override
				public void run() {
					try {
						// hole die Print-Ergebnisse
						String	bd = RunMode.BD.getStringValue( value);
						String	nf = RunMode.NF.getStringValue( value);
						String	ds = RunMode.DS.getStringValue( value);
						// Vergleiche
						StringBuilder	sb = new StringBuilder();
						if ( ! bd.equals( nf)) {
							sb.append( value + ": BD " + bd + " != NF " + nf + " ");
						} else if ( ! bd.equals( ds)) {
							misMatches.incrementAndGet();
							if ( sb.length() == 0) {
								sb.append( value + ": ");
							}
							double dsD;
							try {
								dsD = value - Double.parseDouble( ds);
							} catch ( NumberFormatException e) {
								dsD = Double.NaN;
							}
							double	bdD = value - Double.parseDouble( bd);
							DoubleToString.debug = true;
							RunMode.DS.getStringValue( value);
							DoubleToString.debug = false;
							sb.append( "BD " + bd + " != DS " + ds + " " + " bdD: " + bdD + " dsD: " + dsD);
						} else if ( ! nf.equals( ds)) {
							if ( sb.length() == 0) {
								sb.append( value + ": ");
							}
							sb.append( "NF " + nf + " != DS " + ds + " ");
						}
						testParse();
						// Runden: teste nur noch BD gegen DS
						testRound();

						if ( sb.length() != 0) {
							BenchLogger.syserr( sb.toString());
						}
					} catch ( Exception e) {
						BenchLogger.syserr( "" + value, e);
					}
				}

				private void testParse() {
					// Gegenprobe: Parsen
					double v = Double.parseDouble( s);
					if ( v != value) {
						BenchLogger.syserr( value + ": SC " + v + " V " + value + " ");
					}
				}

				private void testRound() {
					boolean noMismatch = true;
					for ( int dp = 0;  dp < 5 && noMismatch;  dp++) {
						double rBD = RunMode.BD.round( value, dp);
						double rDS = RunMode.DS.round( value, dp);
						if ( rBD != rDS) {
							misMatches.incrementAndGet();
							BenchLogger.syserr( value + "/" + dp + ": rBD " + rBD + " != rDS " + rDS + " ");
							noMismatch = false;
						}
					}
				}
			};
			es.execute( job);
		}
		es.shutdown();
		try {
			es.awaitTermination( 1, TimeUnit.HOURS);
		} catch ( InterruptedException e) {}
		long testDurMS = ( long) ( ( BenchRunner.getNow() - now) / 1e6);
		NumberFormat	nfnf = BenchLogger.LNF;
		nfnf.setGroupingUsed( true);
		BenchLogger.sysout( "Test finished in " + nfnf.format( testDurMS) + " ms, " + misMatches + " mismatches");
	}

	private static void checkFirst() {
		boolean noMismatch = true;
		double value = 0.97;
		for ( int dp = 0;  dp < 100 && noMismatch;  dp++) {
			double rBD = RunMode.BD.round( value, dp);
			double rDS = RunMode.DS.round( value, dp);
			if ( rBD != rDS) {
				BenchLogger.syserr( value + "/" + dp + ": rBD " + rBD + " != rDS " + rDS + " ");
				noMismatch = false;
				RunMode.DS.round( value, 100);
			}
		}
	}

}
