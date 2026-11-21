/**
 *
 */
package misc;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import java.util.zip.CRC32;

/**
 *
 * versucht, einen brauchbare Parameterkombination zu finden, die Kompressionsrate und Ressourcenbedarf (RAM, Zeit)
 * unter einen Hut bringt.
 * <p>
 * Werkzeuge: 7zz (LZMA2, PPMd, BZip2), xz, zstd. Phasen:
 * <ol>
 * <li>baseline: alle Presets mit max. Threads, dazu Thread-Sweeps auf zwei Leveln</li>
 * <li>sweep: um einen Anker je Familie jeden Parameter einzeln über alle Werte variieren (Sensitivität)</li>
 * <li>search: Pareto-Front (Zeit vs. Größe) je Familie bestimmen, alle Nachbarn der Frontpunkte messen,
 *     bis sich die Front nicht mehr ändert oder das Budget aufgebraucht ist</li>
 * </ol>
 * Aufruf: {@code CompressBench2 [--methods=LZMA2,PPMD,BZIP2,XZ,ZSTD] [--phases=baseline,sweep,search]
 * [--budget=Minuten] [--repeat=N] [--tmp=DIR] [--objective=wall|cpu] [--threads=1,4,16] [--no-test] [--no-prune]
 * [--dry-run] [--no-compare] [--rerun=disturbed,front,XZ,...] [--tol=Zeit%[,Größe%]] [Dateien/Verzeichnisse...]},
 * nur Vergleich: {@code CompressBench2 --compare}
 * <br>
 * Ohne Dateien: alle *.log im aktuellen Verzeichnis. 7z bekommt die Einzeldateien (solid), xz und zstd ein
 * einmalig erzeugtes, unkomprimiertes tar derselben Dateien. Ergebnisse werden pro Lauf an
 * compressBench2-&lt;Host&gt;-&lt;Datum&gt;.csv angehängt, im Kopf (#-Zeilen) stehen Maschine, Werkzeugversionen und
 * Korpus. Frühere CSVs derselben Maschine und desselben Korpus werden wiederverwendet; CSVs anderer Maschinen im
 * selben Verzeichnis werden zu einem Vergleich zusammengestellt (compressBench2-compare-&lt;Datum&gt;.txt/.tsv).
 * <br>
 * Temporäre Dateien (tar, Archive) liegen standardmäßig in compressBench2-tmp im aktuellen Verzeichnis, nicht in
 * einem RAM-Dateisystem (/tmp ist oft tmpfs: das tar konkurriert dort mit den Kompressoren um RAM und wird ausgelagert).
 * Je Lauf werden Störgrößen mitgeschrieben (Major Faults, unfreiwillige Kontextwechsel, CPU-Zeit anderer Prozesse,
 * Swap-In); gestörte Läufe werden markiert und lassen sich mit --rerun=disturbed nachmessen.
 *
 * @author rhelbing
 *
 */
public class CompressBench2 {

	private static final String CSV_SEP = "\t";
	public static final String COMPRESS_BENCH = "compressBench2-";
	public static final String CSV = ".csv";
	/** neue Spalten nur hinten anfügen: ältere CSVs bleiben lesbar */
	private static final String CSV_HEADER = String.join( CSV_SEP, "Family", "Level", "Threads", "Params", "Phase",
			"InputBytes", "Size", "Ratio", "WallMs", "UserMs", "SysMs", "MaxRssMB", "TestWallMs", "TestRssMB",
			"Status", "Command", "MajFaults", "InvCtxSw", "OtherCpuMs", "OtherPct", "SwapInMB");
	/** Größenunterschied, ab dem ein Parameter im Sweep als wirksam gilt */
	private static final double SIZE_TOL = 0.001;
	/** Zeitunterschied, ab dem ein Parameter im Sweep als wirksam gilt (Messrauschen) */
	private static final double TIME_TOL = 0.03;
	/** gestört: andere Prozesse brauchten mehr als diesen Anteil der Maschine (%) ... */
	private static final double DISTURBED_OTHER_PCT = 10;
	/** ... oder es wurde mehr als so viel eingelagert (MB) */
	private static final long DISTURBED_SWAP_MB = 100;

	private static final int	maxThreads = Runtime.getRuntime().availableProcessors();

	private static final DateFormat	df = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	private static final DateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

	// Optionen
	private static EnumSet<Family> families;
	private static Set<String> phases = new LinkedHashSet<>( Arrays.asList( "baseline", "sweep", "search"));
	private static long deadline = Long.MAX_VALUE;
	private static int repeat = 1;
	private static File tmpDir = new File( System.getProperty( "user.dir"), "compressBench2-tmp");
	private static boolean cpuObjective = false;
	/** Pareto-Front mit Toleranz: Zeit- bzw. Größenunterschiede darunter gelten als gleich, 0 = streng */
	private static double frontTimeTol = TIME_TOL;
	private static double frontSizeTol = SIZE_TOL;
	private static boolean testArchives = true;
	private static boolean pruneFlatDims = true;
	private static boolean dryRun = false;
	private static boolean compareOnly = false;
	private static boolean compareAfter = true;
	private static String[] mainArgs = new String[ 0];
	/** was aus früheren Läufen neu gemessen werden soll: disturbed, front, Familiennamen */
	private static Set<String> rerun = new HashSet<>();
	private static final List<Config> rerunConfigs = new ArrayList<>();
	private static List<Integer> threadsList = defaultThreads();

	/** GNU time für CPU-Zeit und max. RSS, null wenn nicht vorhanden */
	private static String timeCmd;
	/** größter sinnvoller Zweierexponent für Wörterbücher/Fenster: ceil(log2(Eingabegröße)) */
	private static int inputExpCap = 31;

	// Zustand
	private static final List<Result> results = new ArrayList<>();
	private static final Map<String, Result> byKey = new HashMap<>();
	private static String outName;
	/** Eigenschaften dieser Maschine und dieses Laufs */
	private static RunData machine;
	private static long startMS;
	private static int runCount;

	// ---------------------------------------------------------------------------------------------------------------
	// Parameterraum

	/** eine Parameterdimension: Kandidatenwerte, int-codiert */
	private static final class Dim {
		final String name;
		final int[] values;
		/** Anzeige je Wert (gleicher Index) oder null */
		final String[] labels;
		/** ungeordnet: Nachbarn sind alle anderen Werte statt nur ±1 */
		final boolean categorical;
		/** Werte sind Zweierexponenten, Kandidaten über der Eingabegröße fallen weg */
		final boolean log2;

		private Dim(String name, int[] values, String[] labels, boolean categorical, boolean log2) {
			this.name = name;
			this.values = values;
			this.labels = labels;
			this.categorical = categorical;
			this.log2 = log2;
		}

		static Dim num( String name, int... values) {
			return new Dim( name, values, null, false, false);
		}
		/** Zweierexponent, Anzeige als Zahl (zstd wlog) */
		static Dim log2( String name, int min, int max) {
			return new Dim( name, IntStream.rangeClosed( min, max).toArray(), null, false, true);
		}
		/** Zweierexponent, Anzeige als 64m usw. (7z-Syntax) */
		static Dim size( String name, int min, int max) {
			int[] v = IntStream.rangeClosed( min, max).toArray();
			return new Dim( name, v, IntStream.of( v).mapToObj( CompressBench2::sizeLabel).toArray( String[]::new), false, true);
		}
		static Dim cat( String name, String... labels) {
			return new Dim( name, IntStream.range( 0, labels.length).toArray(), labels, true, false);
		}
		/** geordnet mit Namen, Werte ab first */
		static Dim ordered( String name, int first, String... labels) {
			return new Dim( name, IntStream.range( first, first + labels.length).toArray(), labels, false, false);
		}
		Dim labeled( IntFunction<String> f) {
			return new Dim( name, values, IntStream.of( values).mapToObj( f).toArray( String[]::new), categorical, log2);
		}

		int[] candidates() {
			return log2 ? IntStream.of( values).filter( v -> v <= inputExpCap).toArray() : values;
		}
		String format( int v) {
			if ( labels != null) {
				for ( int i = 0;  i < values.length;  i++) {
					if ( values[ i] == v) {
						return labels[ i];
			}   }   }
			return String.valueOf( v);
		}
		int parse( String s) {
			if ( labels != null) {
				for ( int i = 0;  i < labels.length;  i++) {
					if ( labels[ i].equals( s)) {
						return values[ i];
			}   }   }
			return Integer.parseInt( s);
		}
	}

	private static String sizeLabel( int exp) {
		if ( exp >= 30) return ( 1 << ( exp - 30)) + "g";
		if ( exp >= 20) return ( 1 << ( exp - 20)) + "m";
		return ( 1 << ( exp - 10)) + "k";
	}

	private static String xzSize( int exp) {
		if ( exp >= 30) return ( 1 << ( exp - 30)) + "GiB";
		if ( exp >= 20) return ( 1 << ( exp - 20)) + "MiB";
		return ( 1 << ( exp - 10)) + "KiB";
	}

	/** eine Messkonfiguration: Preset-Level + explizit abweichende Parameter */
	private static final class Config {
		final Family family;
		final int level;
		final int threads;
		/** nur Abweichungen vom Default des Levels */
		final TreeMap<String, Integer> params;
		final String key;

		Config(Family family, int level, int threads, Map<String, Integer> params) {
			this.family = family;
			this.level = level;
			this.threads = family.multiThreaded ? threads : 1;
			this.params = new TreeMap<>( params);
			this.key = family + " x" + level + " t" + this.threads + " " + paramString();
		}
		Config( Family family, int level, int threads) {
			this( family, level, threads, Collections.emptyMap());
		}

		/** effektiver Wert: explizit oder Default des Levels */
		int eff( String dim) {
			Integer v = params.get( dim);
			return v != null ? v : family.def( this, dim);
		}

		/** setzt dim=value; entspricht der Wert dem Default, fällt der Parameter weg */
		Config with( String dim, int value) {
			TreeMap<String, Integer> p = new TreeMap<>( params);
			p.remove( dim);
			Config base = new Config( family, level, threads, p);
			if ( base.eff( dim) == value) {
				return base;
			}
			p.put( dim, value);
			return new Config( family, level, threads, p);
		}
		Config withLevel( int l) {
			return new Config( family, l, threads, params).normalized();
		}
		Config withThreads( int t) {
			return new Config( family, level, t, params);
		}
		Config normalized() {
			Config c = this;
			for ( Map.Entry<String, Integer> e : params.entrySet()) {
				c = c.with( e.getKey(), e.getValue());
			}
			return c;
		}

		String paramString() {
			if ( params.isEmpty()) {
				return "-";
			}
			return params.entrySet().stream()
					.map( e -> e.getKey() + "=" + family.dim( e.getKey()).format( e.getValue()))
					.collect( Collectors.joining( " "));
		}

		@Override
		public boolean equals( Object o) {
			return o instanceof Config && key.equals( ( (Config) o).key);
		}
		@Override
		public int hashCode() {
			return key.hashCode();
		}
		@Override
		public String toString() {
			return key;
		}
	}

	/**
	 * zstd-Parameter je Level für Eingaben > 256 KB (lib/compress/clevels.h):
	 * wlog, clog, hlog, slog, mml, tlen, strat. Zeile 0 ist die Basis für negative Level.
	 * Dient nur als Startpunkt für Nachbarschritte; gemessen wird immer mit den explizit übergebenen Werten.
	 */
	private static final int[][] ZSTD_CPARAMS = {
			{ 19, 12, 13, 1, 6,   1, 1 },
			{ 19, 13, 14, 1, 7,   0, 1 },  //  1
			{ 20, 15, 16, 1, 6,   0, 1 },
			{ 21, 16, 17, 1, 5,   0, 2 },
			{ 21, 18, 18, 1, 5,   0, 2 },
			{ 21, 18, 19, 3, 5,   2, 3 },  //  5
			{ 21, 18, 19, 3, 5,   4, 4 },
			{ 21, 19, 20, 4, 5,   8, 4 },
			{ 21, 19, 20, 4, 5,  16, 5 },
			{ 22, 20, 21, 4, 5,  16, 5 },
			{ 22, 21, 22, 5, 5,  16, 5 },  // 10
			{ 22, 21, 22, 6, 5,  16, 5 },
			{ 22, 22, 23, 6, 5,  32, 5 },
			{ 22, 22, 22, 4, 5,  32, 6 },
			{ 22, 22, 23, 5, 5,  32, 6 },
			{ 22, 23, 23, 6, 5,  32, 6 },  // 15
			{ 22, 22, 22, 5, 5,  48, 7 },
			{ 23, 23, 22, 5, 4,  64, 7 },
			{ 23, 23, 22, 6, 3,  64, 8 },
			{ 23, 24, 22, 7, 3, 256, 9 },
			{ 25, 25, 23, 7, 3, 256, 9 },  // 20
			{ 26, 26, 24, 7, 3, 512, 9 },
			{ 27, 27, 25, 9, 3, 999, 9 },
	};
	private static final String[] ZSTD_KEYS = { "wlog", "clog", "hlog", "slog", "mml", "tlen", "strat"};

	/** PPMd-Ordnung je Level (7-Zip PpmdEncoder.cpp) */
	private static final int[] PPMD_ORDERS = { 3, 4, 4, 5, 5, 6, 8, 16, 24, 32};
	/** xz Wörterbuch-Exponent je Preset (liblzma lzma_encoder_presets.c) */
	private static final int[] XZ_DICT = { 18, 20, 21, 22, 22, 23, 23, 24, 25, 26};
	private static final int[] XZ_FAST_DEPTH = { 4, 8, 24, 48};

	private static final int MF7_HC4 = 0, MF7_HC5 = 1, MF7_BT4 = 4;
	private static final int MFXZ_HC3 = 0, MFXZ_HC4 = 1, MFXZ_BT4 = 4;

	private enum Family {
		/** 7z LZMA2 */
		LZMA2( "7zz", ".7z", true) {
			@Override
			List<Dim> createDims() {
				return Arrays.asList(
						Dim.size( "d", 18, 30),
						Dim.num( "fb", 8, 16, 24, 32, 48, 64, 96, 128, 192, 273),
						Dim.cat( "mf", "hc4", "hc5", "bt2", "bt3", "bt4", "bt5"),
						Dim.num( "mc", 1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 256, 1000),
						Dim.num( "lc", 0, 1, 2, 3, 4),
						Dim.num( "lp", 0, 1, 2, 3, 4),
						Dim.num( "pb", 0, 1, 2, 3, 4),
						Dim.num( "a", 0, 1),
						// Blockgröße für Multithreading: pro Block ein Thread (bt-Matchfinder: zwei)
						Dim.size( "c", 20, 30));
			}
			@Override int[] levels() { return range( 1, 9); }
			@Override int[] baselineLevels() { return range( 1, 9); }
			@Override int[] threadSweepLevels() { return new int[] { 5, 9}; }
			@Override int anchorLevel() { return 9; }

			/** LzmaEnc.c LzmaEncProps_Normalize, Lzma2Enc.c Lzma2EncProps_Normalize */
			@Override
			int def( Config c, String dim) {
				int level = c.level;
				switch ( dim) {
					case "d":  return level <= 3 ? level * 2 + 16 : level <= 6 ? level + 19 : level == 7 ? 25 : 26;
					case "a":  return level < 5 ? 0 : 1;
					case "fb": return level < 7 ? 32 : 64;
					case "mf": return c.eff( "a") == 0 ? MF7_HC5 : MF7_BT4;
					case "mc": return ( 16 + c.eff( "fb") / 2) >> ( isBt( c) ? 0 : 1);
					case "lc": return 3;
					case "lp": return 0;
					case "pb": return 2;
					case "c": {
						int d = c.eff( "d");
						return Math.max( d, Math.min( 28, Math.max( 20, d + 2)));
					}
				}
				throw new IllegalArgumentException( dim);
			}
			private boolean isBt( Config c) {
				return c.eff( "mf") >= 2;
			}
			@Override
			boolean valid( Config c) {
				return c.eff( "lc") + c.eff( "lp") <= 4;
			}
			@Override
			boolean relevant( Config c, Dim d) {
				return !d.name.equals( "c") || c.threads > 1;
			}
			@Override
			List<String> compressArgs( Config c, File archive, Inputs in) {
				return sevenZipArgs( c, "LZMA2", archive, in);
			}
			@Override
			List<String> testArgs( File archive) {
				return sevenZipTestArgs( this, archive);
			}
		},
		/** 7z PPMd, single threaded */
		PPMD( "7zz", ".7z", false) {
			@Override
			List<Dim> createDims() {
				return Arrays.asList(
						Dim.size( "mem", 20, 31),
						Dim.num( "o", 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32));
			}
			@Override int[] levels() { return range( 1, 9); }
			@Override int[] baselineLevels() { return new int[] { 1, 3, 5, 7, 9}; }
			@Override int[] threadSweepLevels() { return new int[ 0]; }
			@Override int anchorLevel() { return 9; }
			@Override
			int def( Config c, String dim) {
				switch ( dim) {
					case "mem": return c.level + 19;
					case "o":   return PPMD_ORDERS[ c.level];
				}
				throw new IllegalArgumentException( dim);
			}
			@Override
			List<String> compressArgs( Config c, File archive, Inputs in) {
				return sevenZipArgs( c, "PPMd", archive, in);
			}
			@Override
			List<String> testArgs( File archive) {
				return sevenZipTestArgs( this, archive);
			}
		},
		/** 7z BZip2 */
		BZIP2( "7zz", ".7z", true) {
			@Override
			List<Dim> createDims() {
				return Arrays.asList(
						Dim.num( "d", 1, 3, 5, 7, 9).labeled( v -> v + "00k"),
						Dim.num( "pass", 1, 2, 3, 4, 5, 7, 10));
			}
			@Override int[] levels() { return range( 1, 9); }
			@Override int[] baselineLevels() { return new int[] { 1, 5, 9}; }
			@Override int[] threadSweepLevels() { return new int[] { 9}; }
			@Override int anchorLevel() { return 9; }
			/** BZip2Encoder.cpp CEncProps::Normalize */
			@Override
			int def( Config c, String dim) {
				int level = c.level;
				switch ( dim) {
					case "d":    return level >= 5 ? 9 : level * 2 - 1;
					case "pass": return level >= 9 ? 7 : level >= 7 ? 2 : 1;
				}
				throw new IllegalArgumentException( dim);
			}
			@Override
			List<String> compressArgs( Config c, File archive, Inputs in) {
				return sevenZipArgs( c, "BZip2", archive, in);
			}
			@Override
			List<String> testArgs( File archive) {
				return sevenZipTestArgs( this, archive);
			}
		},
		/** xz (liblzma LZMA2), bekommt das tar */
		XZ( "xz", ".xz", true) {
			@Override
			List<Dim> createDims() {
				return Arrays.asList(
						Dim.num( "e", 0, 1),
						Dim.size( "dict", 18, 30),
						Dim.cat( "mode", "fast", "normal"),
						Dim.cat( "mf", "hc3", "hc4", "bt2", "bt3", "bt4"),
						Dim.num( "nice", 8, 16, 32, 48, 64, 96, 128, 192, 273),
						Dim.num( "depth", 4, 8, 16, 24, 32, 48, 64, 128, 256, 512, 1000),
						Dim.num( "lc", 0, 1, 2, 3, 4),
						Dim.num( "lp", 0, 1, 2, 3, 4),
						Dim.num( "pb", 0, 1, 2, 3, 4),
						// Blockgröße in MiB für Multithreading, Default 3 * dict
						Dim.num( "bs", 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024)
								.labeled( v -> v + "m"));
			}
			@Override int[] levels() { return range( 0, 9); }
			@Override int[] baselineLevels() { return range( 0, 9); }
			@Override int[] threadSweepLevels() { return new int[] { 6, 9}; }
			@Override int anchorLevel() { return 9; }

			/** liblzma lzma_encoder_presets.c, Auto-Tiefe aus lz_encoder.c */
			@Override
			int def( Config c, String dim) {
				if ( dim.equals( "e")) {
					return 0;
				}
				int level = c.level;
				boolean extreme = c.eff( "e") == 1;
				switch ( dim) {
					case "dict":return XZ_DICT[ level];
					case "mode": return extreme || level > 3 ? 1 : 0;
					case "mf":   return extreme || level > 3 ? MFXZ_BT4 : level == 0 ? MFXZ_HC3 : MFXZ_HC4;
					case "nice":
						if ( extreme) return level == 3 || level == 5 ? 192 : 273;
						if ( level <= 3) return level <= 1 ? 128 : 273;
						return level == 4 ? 16 : level == 5 ? 32 : 64;
					case "depth": {
						if ( extreme && level != 3 && level != 5) return 512;
						if ( !extreme && level <= 3) return XZ_FAST_DEPTH[ level];
						int nice = c.eff( "nice");
						return c.eff( "mf") >= 2 ? 16 + nice / 2 : 4 + nice / 4;
					}
					case "lc": return 3;
					case "lp": return 0;
					case "pb": return 2;
					case "bs": {
						int dict = c.eff( "dict");
						return dict >= 20 ? 3 << ( dict - 20) : 1;	// min. 1 MiB
					}
				}
				throw new IllegalArgumentException( dim);
			}
			@Override
			boolean valid( Config c) {
				return c.eff( "lc") + c.eff( "lp") <= 4;
			}
			@Override
			boolean relevant( Config c, Dim d) {
				return !d.name.equals( "bs") || c.threads > 1;
			}
			@Override
			boolean okExit( int rc) {
				return rc == 0 || rc == 2;	// 2 = Warnung
			}
			@Override
			boolean archiveOnStdout() {
				return true;
			}
			@Override
			List<String> compressArgs( Config c, File archive, Inputs in) {
				StringBuilder filter = new StringBuilder( "--lzma2=preset=" + c.level + ( c.eff( "e") == 1 ? "e" : ""));
				for ( Map.Entry<String, Integer> p : c.params.entrySet()) {
					String name = p.getKey();
					if ( name.equals( "e") || name.equals( "bs")) {
						continue;
					}
					String value = name.equals( "dict") ? xzSize( p.getValue()) : dim( name).format( p.getValue());
					filter.append( ',').append( name).append( '=').append( value);
				}
				List<String> args = new ArrayList<>( Arrays.asList( command(), "-c", "-T" + c.threads, filter.toString()));
				if ( c.params.containsKey( "bs")) {
					args.add( "--block-size=" + c.params.get( "bs") + "MiB");
				}
				args.add( in.stream.getPath());
				return args;
			}
			@Override
			List<String> testArgs( File archive) {
				return Arrays.asList( command(), "-t", "-T0", archive.getPath());
			}
		},
		/** zstd, bekommt das tar */
		ZSTD( "zstd", ".zst", true) {
			@Override
			List<Dim> createDims() {
				return Arrays.asList(
						Dim.log2( "wlog", 18, 30),
						Dim.num( "clog", 12, 14, 16, 18, 20, 22, 23, 24, 25, 26, 27, 28),
						Dim.num( "hlog", 12, 14, 16, 18, 20, 21, 22, 23, 24, 25, 26, 27, 28),
						Dim.num( "slog", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
						Dim.num( "mml", 3, 4, 5, 6, 7),
						Dim.num( "tlen", 0, 1, 2, 4, 8, 16, 32, 48, 64, 96, 128, 256, 512, 999),
						Dim.ordered( "strat", 1,
								"fast", "dfast", "greedy", "lazy", "lazy2", "btlazy2", "btopt", "btultra", "btultra2"),
						// long distance matching, nur per --long einschaltbar
						Dim.num( "ldm", 0, 1));
			}
			@Override
			int[] levels() {
				return IntStream.concat( IntStream.of( -10, -7, -5, -3, -2, -1), IntStream.rangeClosed( 1, 22)).toArray();
			}
			@Override
			int[] baselineLevels() {
				return IntStream.concat( IntStream.of( -10, -5, -3, -1), IntStream.rangeClosed( 1, 22)).toArray();
			}
			@Override int[] threadSweepLevels() { return new int[] { 3, 19}; }
			@Override int anchorLevel() { return 19; }
			@Override
			int def( Config c, String dim) {
				if ( dim.equals( "ldm")) {
					return 0;
				}
				int idx = Arrays.asList( ZSTD_KEYS).indexOf( dim);
				if ( idx < 0) {
					throw new IllegalArgumentException( dim);
				}
				if ( c.level < 0 && dim.equals( "tlen")) {
					return -c.level;
				}
				return ZSTD_CPARAMS[ Math.max( 0, c.level)][ idx];
			}
			@Override
			List<String> compressArgs( Config c, File archive, Inputs in) {
				List<String> args = new ArrayList<>( Arrays.asList( command(), "-q", "-f", "-T" + c.threads, "--ultra",
						c.level < 0 ? "--fast=" + ( -c.level) : "-" + c.level));
				String zp = c.params.entrySet().stream()
						.filter( e -> !e.getKey().equals( "ldm"))
						.map( e -> e.getKey() + "=" + e.getValue())
						.collect( Collectors.joining( ","));
				if ( !zp.isEmpty()) {
					args.add( "--zstd=" + zp);
				}
				if ( c.eff( "ldm") == 1) {
					args.add( "--long=" + c.eff( "wlog"));
				}
				args.add( in.stream.getPath());
				args.add( "-o");
				args.add( archive.getPath());
				return args;
			}
			@Override
			List<String> testArgs( File archive) {
				return Arrays.asList( command(), "-t", "-q", "--long=31", archive.getPath());
			}
		};

		final String tool;
		final String suffix;
		final boolean multiThreaded;
		final List<Dim> dims;
		private String commandPath;

		Family( String tool, String suffix, boolean multiThreaded) {
			this.tool = tool;
			this.suffix = suffix;
			this.multiThreaded = multiThreaded;
			this.dims = createDims();
		}

		abstract List<Dim> createDims();
		/** alle Level, zwischen denen die Suche wechseln darf */
		abstract int[] levels();
		abstract int[] baselineLevels();
		abstract int[] threadSweepLevels();
		/** Ausgangspunkt für den Parameter-Sweep */
		abstract int anchorLevel();
		/** Default eines Parameters für Level und übrige Parameter von c */
		abstract int def( Config c, String dim);
		abstract List<String> compressArgs( Config c, File archive, Inputs in);
		abstract List<String> testArgs( File archive);

		boolean valid( Config c) {
			return true;
		}
		/** false: Parameter hat in dieser Konfiguration keine Wirkung, Nachbarn sparen */
		boolean relevant( Config c, Dim d) {
			return true;
		}
		boolean okExit( int rc) {
			return rc == 0 || ( tool.equals( "7zz") && rc == 1);	// 7z: 1 = Warnung
		}
		boolean archiveOnStdout() {
			return false;
		}
		Dim dim( String name) {
			for ( Dim d : dims) {
				if ( d.name.equals( name)) {
					return d;
			}   }
			throw new IllegalArgumentException( this + ": unknown parameter " + name);
		}
		String command() {
			if ( commandPath == null) {
				try {
					commandPath = findCommand( tool, tool.equals( "7zz") ? "7-Zip" : tool);
				} catch ( IOException e) {
					e.printStackTrace();
				}
				if ( commandPath == null) {
					commandPath = tool;
				}
			}
			return commandPath;
		}
		Config anchor() {
			return new Config( this, anchorLevel(), maxThreads);
		}
	}

	private static int[] range( int from, int to) {
		return IntStream.rangeClosed( from, to).toArray();
	}

	private static List<String> sevenZipArgs( Config c, String method, File archive, Inputs in) {
		List<String> argList = new ArrayList<>();
		argList.add( c.family.command());
		argList.add( "a");
		argList.add( archive.getPath());
		argList.add( "-t7z");
		argList.add( "-ms=1t");	// solid Blockgröße 1TB
		argList.add( "-bso0");	// kein StdOut
		argList.add( "-bsp0");	// kein Fortschritt
		argList.add( "-mx=" + c.level);
		if ( c.family.multiThreaded) {
			argList.add( "-mmt=" + c.threads);
		}
		StringBuilder methodSpec = new StringBuilder( "-m0=" + method);
		for ( Map.Entry<String, Integer> p : c.params.entrySet()) {
			methodSpec.append( ':').append( p.getKey()).append( '=').append( c.family.dim( p.getKey()).format( p.getValue()));
		}
		argList.add( methodSpec.toString());
		for ( File f : in.files) {
			argList.add( f.getPath());
		}
		return argList;
	}

	private static List<String> sevenZipTestArgs( Family f, File archive) {
		return Arrays.asList( f.command(), "t", "-bso0", "-bsp0", archive.getPath());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Messung

	private static final class Inputs {
		final List<File> files;
		final long totalSize;
		/** Eingabe für xz/zstd: tar aller Dateien oder die einzige Datei */
		File stream;

		Inputs( List<File> files) {
			this.files = files;
			this.totalSize = files.stream().mapToLong( File::length).sum();
		}
	}

	private static final class Measure {
		int exit;
		long wallMS;
		long userMS = -1;
		long sysMS = -1;
		long maxRssKB = -1;
		/** Seitenfehler mit Plattenzugriff (beim Kompressor praktisch nur Swap) */
		long majFaults = -1;
		/** unfreiwillige Kontextwechsel: von anderen Prozessen verdrängt */
		long invCtxSw = -1;
		/** CPU-Zeit aller anderen Prozesse während des Laufs */
		long otherCpuMS = -1;
		/** otherCpuMS als Anteil der ganzen Maschine (alle Threads × Wandzeit) */
		double otherPct = -1;
		/** systemweit eingelagert während des Laufs */
		long swapInMB = -1;
	}

	private static final class Result {
		final Config config;
		final String phase;
		final long inputBytes;
		long size = -1;
		Measure comp;
		Measure test;
		String status = "ok";
		String command = "";

		Result( Config config, String phase, long inputBytes) {
			this.config = config;
			this.phase = phase;
			this.inputBytes = inputBytes;
		}
		boolean ok() {
			return status.startsWith( "ok") && size > 0;
		}
		double ratio() {
			return size > 0 ? ( (double) inputBytes) / size : 0;
		}
		long cpuMS() {
			return comp.userMS < 0 ? -1 : comp.userMS + comp.sysMS;
		}
		/** Zeit-Zielgröße für die Pareto-Front */
		long time() {
			return cpuObjective && cpuMS() >= 0 ? cpuMS() : comp.wallMS;
		}
		/** CPU-Zeit / (Wandzeit × Threads): wie gut die Threads ausgelastet waren, -1 unbekannt */
		double efficiency() {
			return cpuMS() < 0 || comp.wallMS <= 0 ? -1 : 100.0 * cpuMS() / ( comp.wallMS * config.threads);
		}
		boolean disturbed() {
			return comp.otherPct > DISTURBED_OTHER_PCT || comp.swapInMB > DISTURBED_SWAP_MB;
		}
	}

	private static Measure runMeasured( List<String> cmd, File stdoutTo) throws IOException, InterruptedException {
		List<String> full = new ArrayList<>();
		File timeFile = new File( tmpDir, COMPRESS_BENCH + "time.txt");
		if ( timeCmd != null) {
			timeFile.delete();
			full.addAll( Arrays.asList( timeCmd, "-f", "%e %U %S %M %F %c", "-o", timeFile.getPath()));
		}
		full.addAll( cmd);
		ProcessBuilder pb = new ProcessBuilder( full);
		pb.redirectError( ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput( stdoutTo != null ? ProcessBuilder.Redirect.to( stdoutTo) : ProcessBuilder.Redirect.INHERIT);
		Measure m = new Measure();
		long busyBefore = systemBusyMS();
		long swapBefore = swapInPages();
		long then = System.nanoTime();
		Process process = pb.start();
		m.exit = process.waitFor();
		m.wallMS = ( System.nanoTime() - then) / 1_000_000;
		long busyAfter = systemBusyMS();
		long swapAfter = swapInPages();
		if ( swapBefore >= 0 && swapAfter >= 0) {
			m.swapInMB = ( swapAfter - swapBefore) * 4096 >> 20;
		}
		if ( timeCmd != null && timeFile.exists()) {
			// bei Fehler steht vorher "Command exited with non-zero status": letzte Zeile nehmen
			List<String> lines = Files.readAllLines( timeFile.toPath());
			for ( int i = lines.size() - 1;  i >= 0;  i--) {
				String[] parts = lines.get( i).trim().split( "\\s+");
				if ( parts.length == 6) {
					try {
						m.userMS = Math.round( Double.parseDouble( parts[ 1]) * 1000);
						m.sysMS = Math.round( Double.parseDouble( parts[ 2]) * 1000);
						m.maxRssKB = Long.parseLong( parts[ 3]);
						m.majFaults = Long.parseLong( parts[ 4]);
						m.invCtxSw = Long.parseLong( parts[ 5]);
						break;
					} catch ( NumberFormatException e) {
						// nächste Zeile
			}   }   }
			timeFile.delete();
		}
		if ( busyBefore >= 0 && busyAfter >= 0 && m.userMS >= 0 && m.wallMS > 0) {
			m.otherCpuMS = Math.max( 0, busyAfter - busyBefore - m.userMS - m.sysMS);
			m.otherPct = 100.0 * m.otherCpuMS / ( m.wallMS * (double) maxThreads);
		}
		return m;
	}

	/** belegte CPU-Zeit aller Kerne seit Boot in ms (Linux /proc/stat, USER_HZ = 100), -1 wenn nicht verfügbar */
	private static long systemBusyMS() {
		try ( BufferedReader reader = new BufferedReader( new FileReader( "/proc/stat"))) {
			// cpu user nice system idle iowait irq softirq steal
			String[] p = reader.readLine().trim().split( "\\s+");
			long busy = 0;
			for ( int i : new int[] { 1, 2, 3, 6, 7, 8}) {
				busy += Long.parseLong( p[ i]);
			}
			return busy * 10;
		} catch ( Exception e) {
			return -1;
		}
	}

	/** eingelagerte Seiten seit Boot (Linux /proc/vmstat), -1 wenn nicht verfügbar */
	private static long swapInPages() {
		try {
			for ( String line : Files.readAllLines( Paths.get( "/proc/vmstat"))) {
				if ( line.startsWith( "pswpin ")) {
					return Long.parseLong( line.substring( 7).trim());
			}   }
		} catch ( Exception e) {
			// nicht Linux
		}
		return -1;
	}

	private static Result runConfig( Config c, String phase, Inputs in) {
		Result r = new Result( c, phase, in.totalSize);
		File archive = new File( tmpDir, COMPRESS_BENCH + "tmp" + c.family.suffix);
		List<String> args = c.family.compressArgs( c, archive, in);
		r.command = String.join( " ", args);
		try {
			for ( int rep = 0;  rep < repeat;  rep++) {
				archive.delete();	// 7z würde sonst aktualisieren
				Measure m = runMeasured( args, c.family.archiveOnStdout() ? archive : null);
				if ( !c.family.okExit( m.exit)) {
					r.comp = m;
					r.status = "fail rc=" + m.exit;
					break;
				}
				if ( r.comp == null || m.wallMS < r.comp.wallMS) {
					r.comp = m;
				}
				r.size = archive.length();
			}
			if ( r.ok() && testArchives) {
				r.test = runMeasured( c.family.testArgs( archive), null);
				if ( !c.family.okExit( r.test.exit)) {
					r.status = "testfail rc=" + r.test.exit;
				}
			}
		} catch ( Exception e) {
			e.printStackTrace();
			r.status = "fail " + e.getClass().getSimpleName();
			if ( r.comp == null) {
				r.comp = new Measure();
			}
		} finally {
			archive.delete();
		}
		return r;
	}

	/** misst alle noch unbekannten, gültigen Konfigurationen; false wenn das Budget erschöpft ist */
	private static boolean runAll( Collection<Config> configs, String phase, Inputs in) {
		List<Config> todo = configs.stream()
				.distinct()
				.filter( c -> !byKey.containsKey( c.key))
				.filter( c -> c.family.valid( c))
				.collect( Collectors.toList());
		for ( Config c : todo) {
			if ( dryRun) {
				System.out.println( "dry " + phase + ": " + c + "\n    " +
						String.join( " ", c.family.compressArgs( c, new File( tmpDir, "x" + c.family.suffix), in)));
				byKey.put( c.key, null);
				continue;
			}
			if ( System.currentTimeMillis() > deadline) {
				System.out.println( "budget exhausted");
				return false;
			}
			Result r = runConfig( c, phase, in);
			results.add( r);
			byKey.put( c.key, r);
			runCount++;
			writeTo( outName, out -> writeResult( r, out));
			printResult( r);
		}
		return true;
	}

	private static void printResult( Result r) {
		long el = ( System.currentTimeMillis() - startMS) / 1000;
		System.out.println( String.format( Locale.US,
				"%4d %-8s %-50s ratio %8.3f  wall %7.1fs  cpu %7.1fs  eff %3.0f%%  rss %6s MB  other %4.1f%%  swapin %5d MB%s  test %6.1fs  %s  [%d:%02d:%02d]",
				runCount, r.phase, r.config.key, r.ratio(), r.comp.wallMS / 1e3, r.cpuMS() / 1e3, r.efficiency(),
				r.comp.maxRssKB < 0 ? "?" : String.valueOf( r.comp.maxRssKB / 1024),
				r.comp.otherPct, r.comp.swapInMB, r.disturbed() ? " DISTURBED" : "",
				r.test != null ? r.test.wallMS / 1e3 : 0.0, r.status, el / 3600, el / 60 % 60, el % 60));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Phasen

	public static void main( String args[]) {
		try {
			mainArgs = args;
			List<File> files = parseArgs( args);
			if ( compareOnly) {
				compare( null);
				return;
			}
			if ( files == null || files.isEmpty()) {
				System.err.println( "no input files");
				return;
			}
			runBenchmarks( files);
		} catch ( Exception e) {
			e.printStackTrace();
		}
	}

	private static void runBenchmarks( List<File> files) throws Exception {
		startMS = System.currentTimeMillis();
		Inputs in = new Inputs( files);
		inputExpCap = 64 - Long.numberOfLeadingZeros( in.totalSize - 1);
		timeCmd = new File( "/usr/bin/time").canExecute() ? "/usr/bin/time" : null;
		if ( timeCmd == null) {
			System.out.println( "GNU time not found: no CPU time and RSS");
		}
		if ( !dryRun && !tmpDir.isDirectory() && !tmpDir.mkdirs()) {
			throw new IOException( "cannot create temp dir " + tmpDir);
		}
		String tmpFs = fsType( tmpDir);
		if ( tmpFs != null && ( tmpFs.equals( "tmpfs") || tmpFs.equals( "ramfs"))) {
			System.out.println( "WARNING: temp dir " + tmpDir + " is in RAM (" + tmpFs + "): the input tar ("
					+ in.totalSize / ( 1 << 20) + " MB) competes with the compressors for memory and may be swapped out."
					+ " Use --tmp=<dir on disk>.");
		}
		machine = detectMachine( in);
		machine.info.put( "tmp", tmpDir.getAbsolutePath() + ( tmpFs != null ? " (" + tmpFs + ")" : ""));
		System.out.println( "Compress Bench on " + files.size() + " files, " + in.totalSize / ( 1 << 20) + " MB"
				+ ", families " + families + ", phases " + phases + ", threads " + threadsList);
		for ( Family f : families) {
			System.out.println( "  " + f + ": " + f.command());
		}
		machine.label = machine.get( "host");
		writeMachine( machine, new PrintWriter( System.out, true));

		if ( !dryRun) {
			prepareStream( in);
			warmCache( in);
			resume( loadRuns());
			outName = buildOutName();
			writeTo( outName, out -> {
				out.println( RunData.MAGIC);
				for ( Map.Entry<String, String> e : machine.info.entrySet()) {
					out.println( "#" + e.getKey() + CSV_SEP + e.getValue());
				}
				out.println( CSV_HEADER);
			});
		} else {
			in.stream = in.files.size() == 1 ? in.files.get( 0) : new File( tmpDir, COMPRESS_BENCH + "input.tar");
		}

		boolean inBudget = true;
		if ( !rerunConfigs.isEmpty()) {
			System.out.println( "rerunning " + rerunConfigs.size() + " earlier results (" + rerun + ")");
			inBudget = runAll( rerunConfigs, "rerun", in);
		}
		if ( inBudget && phases.contains( "baseline")) {
			List<Config> list = new ArrayList<>();
			for ( Family f : families) {
				for ( int level : f.baselineLevels()) {
					list.add( new Config( f, level, maxThreads));
				}
				for ( int level : f.threadSweepLevels()) {
					for ( int t : threadsList) {
						list.add( new Config( f, level, t));
					}
				}
			}
			inBudget = runAll( list, "baseline", in);
		}
		if ( inBudget && phases.contains( "sweep")) {
			for ( Family f : families) {
				Config anchor = f.anchor();
				List<Config> list = new ArrayList<>();
				list.add( anchor);
				for ( Dim d : f.dims) {
					if ( f.relevant( anchor, d)) {
						for ( int v : d.candidates()) {
							list.add( anchor.with( d.name, v));
						}
					}
				}
				inBudget = runAll( list, "sweep", in);
				if ( !inBudget) {
					break;
				}
			}
		}
		if ( inBudget && phases.contains( "search") && !dryRun) {
			search( in);
		}
		if ( in.stream != null && !in.files.contains( in.stream)) {
			in.stream.delete();
		}
		tmpDir.delete();	// nur wenn leer
		if ( !dryRun) {
			writeReport( in);
			if ( compareAfter) {
				compare( machine.groupKey());
			}
		}
	}

	/**
	 * Nachbarschaftssuche: jeder Punkt der Pareto-Front einer Familie wird einmal expandiert
	 * (Level ±1, Threads ±1 Stufe, jeder Parameter ±1 Stufe bzw. alle Alternativen bei kategorischen).
	 * Ende, wenn keine Familie mehr unexpandierte Frontpunkte hat.
	 */
	private static void search( Inputs in) {
		Set<String> expanded = new HashSet<>();
		Map<Family, Set<String>> flat = new EnumMap<>( Family.class);
		for ( Family f : families) {
			flat.put( f, pruneFlatDims ? flatDims( f) : Collections.<String>emptySet());
			if ( !flat.get( f).isEmpty()) {
				System.out.println( f + ": no effect in sweep, not searched: " + flat.get( f));
			}
		}
		for ( int round = 1;  ;  round++) {
			// reihum über Familien, darin reihum über Frontpunkte: das Budget verteilt sich gleichmäßig
			List<Deque<Config>> familyQueues = new ArrayList<>();
			for ( Family f : families) {
				List<Deque<Config>> pointQueues = new ArrayList<>();
				for ( Result r : paretoFront( resultsOf( f))) {
					if ( expanded.add( r.config.key)) {
						pointQueues.add( new ArrayDeque<>( neighbors( r.config, flat.get( f))));
					}
				}
				Deque<Config> fq = new ArrayDeque<>( roundRobin( pointQueues));
				fq.removeIf( c -> byKey.containsKey( c.key) || !f.valid( c));
				System.out.println( "search round " + round + ", " + f + ": " + fq.stream().distinct().count() + " neighbors");
				familyQueues.add( fq);
			}
			List<Config> next = roundRobin( familyQueues);
			if ( next.stream().noneMatch( c -> !byKey.containsKey( c.key) && c.family.valid( c))) {
				System.out.println( "search converged after " + round + " rounds");
				return;
			}
			if ( !runAll( next, "search" + round, in)) {
				return;
			}
		}
	}

	private static List<Config> roundRobin( List<Deque<Config>> queues) {
		List<Config> list = new ArrayList<>();
		while ( queues.stream().anyMatch( q -> !q.isEmpty())) {
			for ( Deque<Config> q : queues) {
				if ( !q.isEmpty()) {
					list.add( q.poll());
		}   }   }
		return list;
	}

	private static List<Config> neighbors( Config c, Set<String> skip) {
		Family f = c.family;
		List<Config> list = new ArrayList<>();
		for ( int l : stepNeighbors( f.levels(), c.level)) {
			list.add( c.withLevel( l));
		}
		if ( f.multiThreaded) {
			for ( int t : stepNeighbors( threadsList.stream().mapToInt( Integer::intValue).toArray(), c.threads)) {
				list.add( c.withThreads( t));
			}
		}
		for ( Dim d : f.dims) {
			if ( skip.contains( d.name) || !f.relevant( c, d)) {
				continue;
			}
			int cur = c.eff( d.name);
			int[] values = d.categorical ? d.candidates() : stepNeighbors( d.candidates(), cur);
			for ( int v : values) {
				if ( v != cur) {
					list.add( c.with( d.name, v));
				}
			}
		}
		return list;
	}

	/** nächstkleinerer und nächstgrößerer Kandidat zu cur (cur muss nicht in values sein) */
	private static int[] stepNeighbors( int[] values, int cur) {
		int[] sorted = values.clone();
		Arrays.sort( sorted);
		Integer lower = null, higher = null;
		for ( int v : sorted) {
			if ( v < cur) lower = v;
			if ( v > cur && higher == null) higher = v;
		}
		return Stream.of( lower, higher).filter( Objects::nonNull).mapToInt( Integer::intValue).toArray();
	}

	/** Parameter, die im Sweep um den Anker weder Größe noch Zeit merklich verbessert haben */
	private static Set<String> flatDims( Family f) {
		Set<String> flat = new TreeSet<>();
		Config anchor = f.anchor();
		Result ar = byKey.get( anchor.key);
		if ( ar == null || !ar.ok()) {
			return flat;
		}
		for ( Dim d : f.dims) {
			List<Result> variants = IntStream.of( d.candidates())
					.mapToObj( v -> anchor.with( d.name, v))
					.filter( c -> !c.equals( anchor))
					.map( c -> byKey.get( c.key))
					.filter( r -> r != null && r.ok())
					.collect( Collectors.toList());
			boolean useful = variants.stream().anyMatch(
					r -> r.size < ar.size * ( 1 - SIZE_TOL) || r.time() < ar.time() * ( 1 - TIME_TOL));
			if ( !variants.isEmpty() && !useful) {
				flat.add( d.name);
			}
		}
		return flat;
	}

	private static List<Result> resultsOf( Family f) {
		return results.stream().filter( r -> r.config.family == f).collect( Collectors.toList());
	}

	/**
	 * nicht dominierte Ergebnisse (kein anderes ist zugleich kleiner-gleich und schneller-gleich), nach Zeit sortiert.
	 * Mit Toleranz (ε-Boxen): Zeit und Größe werden logarithmisch in Boxen der Breite frontTimeTol bzw. frontSizeTol
	 * eingeteilt, je Box zählt nur das kleinste Ergebnis, dominiert wird über die Boxnummern. Messrauschen erzeugt so
	 * keine Treppe fast gleicher Punkte, und anders als bei "b ist höchstens x% langsamer" gibt es keine Ketten,
	 * in denen sich viele kleine Schritte zu einem großen Zeitunterschied aufsummieren.
	 */
	private static List<Result> paretoFront( Collection<Result> rs) {
		Map<String, Result> boxes = new HashMap<>();
		for ( Result r : rs) {
			if ( r.ok()) {
				String k = box( r.time(), frontTimeTol) + "/" + box( r.size, frontSizeTol);
				Result cur = boxes.get( k);
				if ( cur == null || r.size < cur.size || r.size == cur.size && r.time() < cur.time()) {
					boxes.put( k, r);
		}   }   }
		List<Result> front = new ArrayList<>();
		for ( Result a : boxes.values()) {
			double ta = box( a.time(), frontTimeTol), sa = box( a.size, frontSizeTol);
			boolean dominated = false;
			for ( Result b : boxes.values()) {
				double tb = box( b.time(), frontTimeTol), sb = box( b.size, frontSizeTol);
				if ( b != a && tb <= ta && sb <= sa) {	// verschiedene Boxen: mindestens eine Nummer echt kleiner
					dominated = true;
					break;
			}   }
			if ( !dominated) {
				front.add( a);
		}   }
		front.sort( Comparator.comparingLong( Result::time));
		return front;
	}

	/** Boxnummer für die tolerante Front, ohne Toleranz der Wert selbst */
	private static double box( double v, double tol) {
		return tol <= 0 ? v : Math.floor( Math.log( Math.max( v, 1)) / Math.log1p( tol));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Ein-/Ausgabe

	private static List<File> parseArgs( String[] args) {
		families = EnumSet.of( Family.LZMA2, Family.PPMD, Family.XZ, Family.ZSTD);
		List<File> files = new ArrayList<>();
		for ( String arg : args) {
			if ( arg.startsWith( "--")) {
				int eq = arg.indexOf( '=');
				String name = eq < 0 ? arg.substring( 2) : arg.substring( 2, eq);
				String value = eq < 0 ? "" : arg.substring( eq + 1);
				switch ( name) {
					case "methods":
						families = EnumSet.noneOf( Family.class);
						for ( String m : value.split( ",")) {
							families.add( Family.valueOf( m.trim().toUpperCase()));
						}
						break;
					case "phases":    phases = new LinkedHashSet<>( Arrays.asList( value.split( ","))); break;
					case "budget":    deadline = System.currentTimeMillis() + (long) ( Double.parseDouble( value) * 60_000); break;
					case "repeat":    repeat = Integer.parseInt( value); break;
					case "tmp":       tmpDir = new File( value); break;
					case "objective": cpuObjective = value.equals( "cpu"); break;
					case "tol": {
						String[] t = value.split( ",");
						frontTimeTol = Double.parseDouble( t[ 0]) / 100;
						if ( t.length > 1) {
							frontSizeTol = Double.parseDouble( t[ 1]) / 100;
						}
						break;
					}
					case "no-test":   testArchives = false; break;
					case "no-prune":  pruneFlatDims = false; break;
					case "dry-run":   dryRun = true; break;
					case "compare":   compareOnly = true; break;
					case "no-compare": compareAfter = false; break;
					case "rerun":
						for ( String what : value.split( ",")) {
							rerun.add( Arrays.stream( Family.values()).anyMatch( f -> f.name().equalsIgnoreCase( what))
									? what.toUpperCase() : what.toLowerCase());
						}
						break;
					case "threads":
						threadsList = Arrays.stream( value.split( ",")).map( Integer::valueOf).collect( Collectors.toList());
						break;
					default:
						throw new IllegalArgumentException( "unknown option " + arg);
				}
			} else {
				File f = new File( arg);
				if ( f.isDirectory()) {
					File[] children = f.listFiles( c -> c.isFile() && !c.getName().startsWith( COMPRESS_BENCH));
					if ( children != null) {
						Arrays.sort( children);
						files.addAll( Arrays.asList( children));
					}
				} else {
					files.add( f);
				}
			}
		}
		if ( files.isEmpty() && !compareOnly) {
			files = findFiles();
		}
		return files == null ? null : files.stream().map( File::getAbsoluteFile).collect( Collectors.toList());
	}

	private static List<Integer> defaultThreads() {
		List<Integer> list = new ArrayList<>();
		for ( int t = 1;  t < maxThreads;  t *= 2) {
			list.add( t);
		}
		list.add( maxThreads);
		return list;
	}

	/** xz und zstd komprimieren einen Strom: mehrere Dateien einmal zu einem tar zusammenfassen */
	private static void prepareStream( Inputs in) throws IOException, InterruptedException {
		boolean needed = families.stream().anyMatch( f -> f == Family.XZ || f == Family.ZSTD);
		if ( !needed) {
			return;
		}
		if ( in.files.size() == 1) {
			in.stream = in.files.get( 0);
			return;
		}
		File tar = new File( tmpDir, COMPRESS_BENCH + "input.tar");
		List<String> args = new ArrayList<>( Arrays.asList( "tar", "-cf", tar.getPath()));
		for ( File f : in.files) {
			args.addAll( Arrays.asList( "-C", f.getParent(), f.getName()));
		}
		Process p = new ProcessBuilder( args).inheritIO().start();
		if ( p.waitFor() != 0) {
			throw new IOException( "tar failed: " + args);
		}
		in.stream = tar;
		System.out.println( "tar for xz/zstd: " + tar + " (" + tar.length() / ( 1 << 20) + " MB)");
	}

	/** Eingaben einmal lesen, damit der erste Lauf nicht von der Platte bremst */
	private static void warmCache( Inputs in) throws IOException {
		byte[] buf = new byte[ 1 << 20];
		List<File> all = new ArrayList<>( in.files);
		if ( in.stream != null) {
			all.add( in.stream);
		}
		for ( File f : all) {
			try ( InputStream is = new FileInputStream( f)) {
				while ( is.read( buf) > 0) {
					// nur lesen
		}   }   }
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Maschinen, frühere Läufe, Vergleich

	/** Ergebnisse einer Maschine (ggf. aus mehreren CSVs zusammengeführt) samt Eigenschaften aus dem CSV-Kopf */
	private static final class RunData {
		static final String MAGIC = "#CompressBench2";
		/** bestimmen die Vergleichbarkeit: andere Werte = andere Maschine bzw. andere Werkzeugversion */
		static final String[] IDENTITY = { "host", "cpu", "threads", "ramGB", "7zz", "xz", "zstd"};

		final Map<String, String> info = new LinkedHashMap<>();
		final List<Result> results = new ArrayList<>();
		final Map<String, Result> byKey = new HashMap<>();
		final List<String> files = new ArrayList<>();
		String label;

		String get( String key) {
			String v = info.get( key);
			return v != null ? v : "?";
		}
		String groupKey() {
			return Arrays.stream( IDENTITY).map( this::get).collect( Collectors.joining( "|"));
		}
		String corpus() {
			return get( "corpus");
		}
		int threads() {
			try {
				return Integer.parseInt( get( "threads"));
			} catch ( NumberFormatException e) {
				return Integer.MAX_VALUE;
			}
		}
		/** ein späteres Ergebnis derselben Konfiguration (Nachmessung) ersetzt das frühere */
		void add( Result r) {
			Result old = byKey.put( r.config.key, r);
			if ( old != null) {
				results.set( results.indexOf( old), r);
			} else {
				results.add( r);
			}
		}
	}

	private static RunData detectMachine( Inputs in) {
		RunData m = new RunData();
		m.info.put( "date", df.format( new Date()));
		m.info.put( "host", hostName());
		m.info.put( "cpu", cpuName());
		m.info.put( "cores", physicalCores());
		m.info.put( "threads", String.valueOf( maxThreads));
		long ram = totalRam();
		m.info.put( "ramMB", String.valueOf( ram >> 20));
		m.info.put( "ramGB", String.valueOf( Math.round( ram / (double) ( 1L << 30))));
		m.info.put( "ramAvailMB", String.valueOf( availableRam() >> 20));
		m.info.put( "load", String.format( Locale.US, "%.2f", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()));
		m.info.put( "os", System.getProperty( "os.name") + " " + System.getProperty( "os.version") + " " + System.getProperty( "os.arch"));
		m.info.put( "java", System.getProperty( "java.version"));
		m.info.put( "7zz", toolVersion( Family.LZMA2.command()));
		m.info.put( "xz", toolVersion( Family.XZ.command(), "--version"));
		m.info.put( "zstd", toolVersion( Family.ZSTD.command(), "--version"));
		m.info.put( "corpus", corpusSignature( in.files));
		m.info.put( "args", String.join( " ", mainArgs));
		return m;
	}

	/** Dateisystemtyp des Verzeichnisses aus /proc/mounts (längster passender Mountpoint), null wenn unbekannt */
	private static String fsType( File dir) {
		try {
			String path = dir.getCanonicalPath();
			String best = null, type = null;
			for ( String line : Files.readAllLines( Paths.get( "/proc/mounts"))) {
				String[] p = line.split( " ");
				if ( p.length < 3) {
					continue;
				}
				String mp = p[ 1];
				boolean match = path.equals( mp) || path.startsWith( mp.endsWith( "/") ? mp : mp + "/");
				if ( match && ( best == null || mp.length() > best.length())) {
					best = mp;
					type = p[ 2];
			}   }
			return type;
		} catch ( IOException e) {
			return null;
		}
	}

	private static String hostName() {
		for ( String env : new String[] { "HOSTNAME", "COMPUTERNAME"}) {
			String h = System.getenv( env);
			if ( h != null && !h.isEmpty()) {
				return h;
		}   }
		try {
			List<String> lines = Files.readAllLines( Paths.get( "/etc/hostname"));
			if ( !lines.isEmpty() && !lines.get( 0).trim().isEmpty()) {
				return lines.get( 0).trim();
			}
		} catch ( IOException e) {
			// weiter
		}
		try {
			return java.net.InetAddress.getLocalHost().getHostName();
		} catch ( IOException e) {
			return "unknown";
		}
	}

	private static List<String> cpuInfo() {
		try {
			return Files.readAllLines( Paths.get( "/proc/cpuinfo"));
		} catch ( IOException e) {
			return Collections.emptyList();
		}
	}

	private static String cpuInfoValue( String line) {
		return line.substring( line.indexOf( ':') + 1).trim();
	}

	private static String cpuName() {
		for ( String line : cpuInfo()) {
			if ( line.startsWith( "model name")) {
				return cpuInfoValue( line);
		}   }
		String id = System.getenv( "PROCESSOR_IDENTIFIER");	// Windows
		if ( id != null) {
			return id;
		}
		String mac = firstOutputLine( "sysctl", "-n", "machdep.cpu.brand_string");
		return mac != null ? mac : System.getProperty( "os.arch");
	}

	private static String physicalCores() {
		Set<String> cores = new HashSet<>();
		String physId = "";
		for ( String line : cpuInfo()) {
			if ( line.startsWith( "physical id")) {
				physId = cpuInfoValue( line);
			} else if ( line.startsWith( "core id")) {
				cores.add( physId + "/" + cpuInfoValue( line));
		}   }
		if ( !cores.isEmpty()) {
			return String.valueOf( cores.size());
		}
		String mac = firstOutputLine( "sysctl", "-n", "hw.physicalcpu");
		return mac != null ? mac : "?";
	}

	private static long totalRam() {
		java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		if ( os instanceof com.sun.management.OperatingSystemMXBean) {
			return ( (com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize();
		}
		return -1;
	}

	/** Linux: MemAvailable (inkl. freigebbarem Cache), sonst freier Speicher */
	private static long availableRam() {
		try {
			for ( String line : Files.readAllLines( Paths.get( "/proc/meminfo"))) {
				if ( line.startsWith( "MemAvailable:")) {
					return Long.parseLong( line.replaceAll( "\\D", "")) << 10;
			}   }
		} catch ( IOException | NumberFormatException e) {
			// weiter
		}
		java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		if ( os instanceof com.sun.management.OperatingSystemMXBean) {
			return ( (com.sun.management.OperatingSystemMXBean) os).getFreePhysicalMemorySize();
		}
		return -1;
	}

	/** erste Ausgabezeile mit Versionsnummer, z.B. "7-Zip (z) 25.01 (x64)" */
	private static String toolVersion( String... cmd) {
		try {
			Process p = new ProcessBuilder( cmd).redirectErrorStream( true).start();
			p.getOutputStream().close();
			String version = "-";
			try ( BufferedReader reader = new BufferedReader( new InputStreamReader( p.getInputStream()))) {
				String line;
				while ( ( line = reader.readLine()) != null) {
					if ( version.equals( "-") && line.matches( ".*\\d+\\.\\d+.*")) {
						version = line.replace( "*", "").split( " : |, by ")[ 0].trim();
			}   }   }
			p.waitFor();
			return version;
		} catch ( Exception e) {
			return "-";
		}
	}

	private static String firstOutputLine( String... cmd) {
		try {
			Process p = new ProcessBuilder( cmd).redirectErrorStream( true).start();
			try ( BufferedReader reader = new BufferedReader( new InputStreamReader( p.getInputStream()))) {
				String line = reader.readLine();
				return p.waitFor() == 0 && line != null && !line.trim().isEmpty() ? line.trim() : null;
			}
		} catch ( Exception e) {
			return null;
		}
	}

	/** Dateianzahl, Größe und CRC über Namen und Größen: Vergleich nur bei gleichem Korpus sinnvoll */
	private static String corpusSignature( List<File> files) {
		CRC32 crc = new CRC32();
		long total = 0;
		List<File> sorted = new ArrayList<>( files);
		sorted.sort( Comparator.comparing( File::getName));
		for ( File f : sorted) {
			crc.update( ( f.getName() + ":" + f.length() + "\n").getBytes( StandardCharsets.UTF_8));
			total += f.length();
		}
		return files.size() + " files, " + total + " bytes, crc " + Long.toHexString( crc.getValue());
	}

	/** liest alle CSVs dieses Programms im aktuellen Verzeichnis, zusammengeführt je Maschine und Korpus */
	private static List<RunData> loadRuns() {
		File[] csvs = new File( System.getProperty( "user.dir"))
				.listFiles( f -> f.isFile() && f.getName().startsWith( COMPRESS_BENCH) && f.getName().endsWith( CSV));
		Map<String, RunData> groups = new LinkedHashMap<>();
		if ( csvs == null) {
			return new ArrayList<>();
		}
		Arrays.sort( csvs);
		for ( File csv : csvs) {
			RunData file = readCsv( csv);
			if ( file == null) {
				System.out.println( "skipping " + csv.getName() + ": no " + RunData.MAGIC + " header");
				continue;
			}
			String key = file.groupKey() + "|" + file.corpus();
			RunData group = groups.get( key);
			if ( group == null) {
				groups.put( key, file);
			} else {
				group.files.addAll( file.files);
				file.results.forEach( group::add);
				group.info.put( "lastDate", file.get( "date"));
			}
		}
		return new ArrayList<>( groups.values());
	}

	private static RunData readCsv( File csv) {
		try ( BufferedReader reader = new BufferedReader( new FileReader( csv))) {
			String line = reader.readLine();
			if ( line == null || !line.startsWith( RunData.MAGIC)) {
				return null;
			}
			RunData data = new RunData();
			data.files.add( csv.getName());
			while ( ( line = reader.readLine()) != null) {
				if ( line.startsWith( "#")) {
					String[] kv = line.substring( 1).split( CSV_SEP, 2);
					if ( kv.length == 2) {
						data.info.put( kv[ 0], kv[ 1]);
					}
				} else if ( !line.startsWith( "Family" + CSV_SEP) && !line.trim().isEmpty()) {
					Result r = parseResult( line);
					if ( r != null) {
						data.add( r);
				}   }
			}
			return data;
		} catch ( IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Result parseResult( String line) {
		String[] a = line.split( CSV_SEP, -1);
		try {
			Family f = Family.valueOf( a[ 0]);
			Map<String, Integer> params = new TreeMap<>();
			if ( !a[ 3].equals( "-")) {
				for ( String kv : a[ 3].split( " ")) {
					String[] p = kv.split( "=", 2);
					params.put( p[ 0], f.dim( p[ 0]).parse( p[ 1]));
				}
			}
			Config c = new Config( f, Integer.parseInt( a[ 1]), Integer.parseInt( a[ 2]), params).normalized();
			Result r = new Result( c, a[ 4], Long.parseLong( a[ 5]));
			r.size = Long.parseLong( a[ 6]);
			r.comp = new Measure();
			r.comp.wallMS = Long.parseLong( a[ 8]);
			r.comp.userMS = Long.parseLong( a[ 9]);
			r.comp.sysMS = Long.parseLong( a[ 10]);
			r.comp.maxRssKB = Long.parseLong( a[ 11]) * 1024;
			if ( Long.parseLong( a[ 12]) >= 0) {
				r.test = new Measure();
				r.test.wallMS = Long.parseLong( a[ 12]);
				r.test.maxRssKB = Long.parseLong( a[ 13]) * 1024;
			}
			r.status = a[ 14];
			r.command = a.length > 15 ? a[ 15] : "";
			if ( a.length > 20) {
				r.comp.majFaults = Long.parseLong( a[ 16]);
				r.comp.invCtxSw = Long.parseLong( a[ 17]);
				r.comp.otherCpuMS = Long.parseLong( a[ 18]);
				r.comp.otherPct = Double.parseDouble( a[ 19]);
				r.comp.swapInMB = Long.parseLong( a[ 20]);
			}
			return r;
		} catch ( Exception e) {
			System.out.println( "not a result: " + line + " (" + e + ")");
			return null;
		}
	}

	/** Ergebnisse derselben Maschine und desselben Korpus übernehmen: werden nicht wiederholt und fließen in die Suche ein */
	private static void resume( List<RunData> runs) {
		for ( RunData d : runs) {
			if ( !d.groupKey().equals( machine.groupKey()) || !d.corpus().equals( machine.corpus())) {
				continue;
			}
			Set<String> front = new HashSet<>();
			if ( rerun.contains( "front")) {
				for ( Family f : families) {
					d.results.stream().filter( r -> r.config.family == f).collect( Collectors.collectingAndThen(
							Collectors.toList(), CompressBench2::paretoFront)).forEach( r -> front.add( r.config.key));
			}   }
			int count = 0;
			for ( Result r : d.results) {
				if ( !families.contains( r.config.family) || byKey.containsKey( r.config.key)) {
					continue;
				}
				boolean again = rerun.contains( r.config.family.name())
						|| r.ok() && ( rerun.contains( "disturbed") && r.disturbed() || front.contains( r.config.key));
				if ( again) {
					rerunConfigs.add( r.config);
				} else {
					results.add( r);
					byKey.put( r.config.key, r);
					count++;
			}   }
			if ( count > 0) {
				System.out.println( "reusing " + count + " results from " + d.files);
			}
		}
	}

	private static String buildOutName() {
		Date d = new Date();
		String	dateS = outFormat.format( d);
		return COMPRESS_BENCH + machine.get( "host").replaceAll( "[^A-Za-z0-9._-]", "_") + "-" + dateS + CSV;
	}

	/**
	 * Vergleich aller Maschinen je Korpus: Konsole (Maschinen, relative Zeiten, Pareto-Konfigurationen),
	 * compressBench2-compare-...txt (zusätzlich alle Konfigurationen) und .tsv (alle Werte je Maschine).
	 * @param refKey groupKey der Referenzmaschine (aktueller Lauf) oder null: erste gefundene
	 */
	private static void compare( String refKey) {
		Map<String, List<RunData>> byCorpus = loadRuns().stream()
				.collect( Collectors.groupingBy( RunData::corpus, LinkedHashMap::new, Collectors.toList()));
		String dateS = outFormat.format( new Date());
		int n = 0;
		for ( Map.Entry<String, List<RunData>> e : byCorpus.entrySet()) {
			List<RunData> runs = e.getValue();
			if ( runs.size() < 2) {
				System.out.println( "corpus " + e.getKey() + ": only " + runs.get( 0).get( "host") + ", nothing to compare");
				continue;
			}
			runs.sort( Comparator.comparing( ( RunData d) -> !d.groupKey().equals( refKey)));	// Referenz zuerst
			Map<String, Integer> hostCount = new HashMap<>();
			for ( RunData d : runs) {
				int c = hostCount.merge( d.get( "host"), 1, Integer::sum);
				d.label = d.get( "host") + ( c > 1 ? "#" + c : "");
			}
			StringWriter summary = new StringWriter();
			StringWriter full = new StringWriter();
			writeComparison( e.getKey(), runs, new PrintWriter( summary), new PrintWriter( full));
			String base = COMPRESS_BENCH + "compare-" + dateS + ( n++ > 0 ? "-" + n : "");
			System.out.println();
			System.out.print( summary);
			writeTo( base + ".txt", w -> w.print( full));
			writeTo( base + ".tsv", w -> writeComparisonTsv( runs, w));
			System.out.println( "comparison: " + base + ".txt, " + base + ".tsv");
		}
	}

	private static void writeMachine( RunData d, PrintWriter out) {
		out.println( d.label);
		out.println( "    CPU     " + d.get( "cpu") + ", " + d.get( "cores") + " cores / " + d.get( "threads") + " threads, "
				+ d.get( "ramGB") + " GB RAM (" + d.get( "ramAvailMB") + " MB available, load " + d.get( "load") + " at start)");
		out.println( "    System  " + d.get( "os") + ", Java " + d.get( "java"));
		out.println( "    Tools   " + d.get( "7zz") + " | " + d.get( "xz") + " | " + d.get( "zstd"));
		if ( !d.files.isEmpty()) {
			long failed = d.results.stream().filter( r -> !r.ok()).count();
			out.println( "    Runs    " + d.get( "date") + ( d.info.containsKey( "lastDate") ? " .. " + d.get( "lastDate") : "")
					+ ", " + d.results.size() + " results (" + failed + " failed) in " + d.files);
		}
	}

	private static final Comparator<Config> CONFIG_ORDER = Comparator.<Config>comparingInt( c -> c.family.ordinal())
			.thenComparingInt( c -> c.level).thenComparingInt( c -> c.threads).thenComparing( Config::paramString);

	private static List<Config> allConfigs( List<RunData> runs) {
		Map<String, Config> configs = new HashMap<>();
		for ( RunData d : runs) {
			for ( Result r : d.results) {
				configs.putIfAbsent( r.config.key, r.config);
		}   }
		List<Config> list = new ArrayList<>( configs.values());
		list.sort( CONFIG_ORDER);
		return list;
	}

	/** geometrisches Mittel von other/ref über gemeinsame, erfolgreiche Konfigurationen mit Referenzzeit >= 0.2 s */
	private static String relativeTime( RunData ref, RunData other, Predicate<Config> filter, ToLongFunction<Result> metric) {
		double logSum = 0;
		int count = 0;
		for ( Result r : ref.results) {
			Result o = other.byKey.get( r.config.key);
			if ( o != null && r.ok() && o.ok() && filter.test( r.config)
					&& metric.applyAsLong( r) >= 200 && metric.applyAsLong( o) > 0) {
				logSum += Math.log( (double) metric.applyAsLong( o) / metric.applyAsLong( r));
				count++;
		}   }
		return count == 0 ? "-" : String.format( Locale.US, "x%.2f (n=%d)", Math.exp( logSum / count), count);
	}

	private static void writeComparison( String corpus, List<RunData> runs, PrintWriter summary, PrintWriter full) {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter( sw);
		RunData ref = runs.get( 0);
		out.println( "=== Machines, corpus " + corpus + ", reference " + ref.label);
		for ( RunData d : runs) {
			writeMachine( d, out);
		}
		out.println();
		out.println( "=== Time relative to " + ref.label + " (geometric mean over common configs, >1 = slower)");
		out.println( String.format( "%-20s %-18s %-18s %-18s %-18s", "", "wall all", "wall 1 thread", "wall max threads", "cpu all"));
		for ( RunData d : runs.subList( 1, runs.size())) {
			int maxT = Math.min( ref.threads(), d.threads());
			out.println( String.format( "%-20s %-18s %-18s %-18s %-18s", d.label,
					relativeTime( ref, d, c -> true, r -> r.comp.wallMS),
					relativeTime( ref, d, c -> c.threads == 1, r -> r.comp.wallMS),
					relativeTime( ref, d, c -> c.threads == maxT, r -> r.comp.wallMS),
					relativeTime( ref, d, c -> true, Result::cpuMS)));
		}
		out.println();

		Set<String> onAnyFront = new HashSet<>();
		List<Set<String>> fronts = new ArrayList<>();
		for ( RunData d : runs) {
			Set<String> front = paretoFront( d.results).stream().map( r -> r.config.key).collect( Collectors.toSet());
			fronts.add( front);
			onAnyFront.addAll( front);
		}
		List<Config> all = allConfigs( runs);
		out.println( "=== Configs on the Pareto front of any machine (* = on this machine's front, ! = disturbed run,"
				+ " n/a = needs more threads)");
		writeComparisonTable( all.stream().filter( c -> onAnyFront.contains( c.key)).collect( Collectors.toList()), runs, fronts, out);
		out.flush();
		summary.print( sw);
		summary.flush();

		full.print( sw);
		full.println();
		full.println( "=== All configs");
		writeComparisonTable( all, runs, fronts, full);
		full.flush();
	}

	private static final String CELL_HEADER = String.format( "%8s %6s %8s %4s %6s   ", "Wall s", "rel", "CPU s", "Eff%", "RSS MB");
	private static final int CELL_WIDTH = CELL_HEADER.length();

	private static void writeComparisonTable( List<Config> configs, List<RunData> runs, List<Set<String>> fronts, PrintWriter out) {
		String rowFormat = "%-6s %4s %3s  %-45s %9s  ";
		StringBuilder h1 = new StringBuilder( String.format( rowFormat, "", "", "", "", ""));
		StringBuilder h2 = new StringBuilder( String.format( rowFormat, "Family", "Lvl", "Thr", "Params", "Ratio"));
		for ( RunData d : runs) {
			String l = "| " + d.label + " (" + d.threads() + " thr, " + d.get( "ramGB") + " GB)";
			h1.append( String.format( "%-" + CELL_WIDTH + "s", l.length() > CELL_WIDTH ? l.substring( 0, CELL_WIDTH) : l));
			h2.append( "|").append( CELL_HEADER.substring( 1));
		}
		out.println( h1);
		out.println( h2);
		RunData ref = runs.get( 0);
		for ( Config c : configs) {
			// Rate der ersten Maschine mit Ergebnis; ≠ wenn sie auf einer anderen abweicht (Version, gedrosselte Threads)
			double ratio = 0;
			boolean differs = false;
			for ( RunData d : runs) {
				Result r = d.byKey.get( c.key);
				if ( r != null && r.ok()) {
					if ( ratio == 0) {
						ratio = r.ratio();
					} else if ( Math.abs( r.ratio() / ratio - 1) > 0.0005) {
						differs = true;
			}   }   }
			StringBuilder row = new StringBuilder( String.format( Locale.US, rowFormat,
					c.family, c.level, c.threads, c.paramString(),
					ratio == 0 ? "-" : String.format( Locale.US, "%.3f%s", ratio, differs ? "≠" : " ")));
			Result refResult = ref.byKey.get( c.key);
			for ( int i = 0;  i < runs.size();  i++) {
				RunData d = runs.get( i);
				Result r = d.byKey.get( c.key);
				String cell;
				if ( r != null && r.ok()) {
					String rel = i > 0 && refResult != null && refResult.ok() && refResult.comp.wallMS > 0
							? String.format( Locale.US, "x%.2f", (double) r.comp.wallMS / refResult.comp.wallMS) : "";
					cell = String.format( Locale.US, "%8.2f %6s %8s %4s %6s%s%s ",
							r.comp.wallMS / 1e3, rel, r.cpuMS() < 0 ? "?" : String.format( Locale.US, "%.2f", r.cpuMS() / 1e3),
							pct( r.efficiency()), r.comp.maxRssKB < 0 ? "?" : String.valueOf( r.comp.maxRssKB / 1024),
							fronts.get( i).contains( c.key) ? "*" : " ", r.disturbed() ? "!" : " ");
				} else if ( r != null) {
					cell = "  " + r.status;
				} else if ( c.threads > d.threads()) {
					cell = "  n/a";
				} else {
					cell = "  -";
				}
				row.append( "|").append( String.format( "%-" + ( CELL_WIDTH - 1) + "s", cell));
			}
			out.println( row.toString().replaceAll( "\\s+$", ""));
		}
	}

	private static void writeComparisonTsv( List<RunData> runs, PrintWriter out) {
		List<String> header = new ArrayList<>( Arrays.asList( "Family", "Level", "Threads", "Params"));
		for ( RunData d : runs) {
			for ( String col : new String[] { "Ratio", "WallMs", "CpuMs", "EffPct", "MaxRssMB", "TestWallMs", "OtherPct",
					"SwapInMB", "Status", "Front", "Disturbed"}) {
				header.add( d.label + " " + col);
		}   }
		out.println( String.join( CSV_SEP, header));
		List<Set<String>> fronts = runs.stream()
				.map( d -> paretoFront( d.results).stream().map( r -> r.config.key).collect( Collectors.toSet()))
				.collect( Collectors.toList());
		for ( Config c : allConfigs( runs)) {
			List<String> row = new ArrayList<>( Arrays.asList(
					c.family.toString(), String.valueOf( c.level), String.valueOf( c.threads), c.paramString()));
			for ( int i = 0;  i < runs.size();  i++) {
				RunData d = runs.get( i);
				Result r = d.byKey.get( c.key);
				if ( r == null) {
					row.addAll( Arrays.asList( "", "", "", "", "", "", "", "", c.threads > d.threads() ? "n/a" : "", "", ""));
				} else {
					row.addAll( Arrays.asList(
							r.ok() ? String.format( Locale.US, "%.4f", r.ratio()) : "",
							String.valueOf( r.comp.wallMS), String.valueOf( r.cpuMS()), pct( r.efficiency()),
							String.valueOf( r.comp.maxRssKB < 0 ? -1 : r.comp.maxRssKB / 1024),
							String.valueOf( r.test != null ? r.test.wallMS : -1),
							pct( r.comp.otherPct), String.valueOf( r.comp.swapInMB),
							r.status, fronts.get( i).contains( c.key) ? "*" : "", r.disturbed() ? "!" : ""));
				}
			}
			out.println( String.join( CSV_SEP, row));
		}
	}

	private static void writeResult( Result r, PrintWriter out) {
		Config c = r.config;
		out.println( String.join( CSV_SEP,
				c.family.toString(), String.valueOf( c.level), String.valueOf( c.threads), c.paramString(), r.phase,
				String.valueOf( r.inputBytes), String.valueOf( r.size), String.format( Locale.US, "%.4f", r.ratio()),
				String.valueOf( r.comp.wallMS), String.valueOf( r.comp.userMS), String.valueOf( r.comp.sysMS),
				String.valueOf( r.comp.maxRssKB < 0 ? -1 : r.comp.maxRssKB / 1024),
				String.valueOf( r.test != null ? r.test.wallMS : -1),
				String.valueOf( r.test != null && r.test.maxRssKB >= 0 ? r.test.maxRssKB / 1024 : -1),
				r.status, r.command,
				String.valueOf( r.comp.majFaults), String.valueOf( r.comp.invCtxSw), String.valueOf( r.comp.otherCpuMS),
				String.format( Locale.US, "%.1f", r.comp.otherPct), String.valueOf( r.comp.swapInMB)));
	}

	/** Pareto-Fronten gesamt und je Familie, auf Konsole und in compressBench2-...-pareto.txt */
	private static void writeReport( Inputs in) {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter( sw);
		out.println( "Compress Bench " + df.format( new Date()) + ": " + in.files.size() + " files, "
				+ in.totalSize + " bytes, " + results.size() + " results, objective " + ( cpuObjective ? "cpu" : "wall")
				+ String.format( Locale.US, ", front tolerance %.1f%% time / %.2f%% size", frontTimeTol * 100, frontSizeTol * 100));
		out.println();
		out.println( "=== Pareto front, all families");
		writeFront( paretoFront( results), out);
		for ( Family f : families) {
			out.println();
			out.println( "=== Pareto front " + f);
			writeFront( paretoFront( resultsOf( f)), out);
			Set<String> flat = flatDims( f);
			if ( !flat.isEmpty()) {
				out.println( "no effect around " + f.anchor().key + ": " + flat);
			}
		}
		long disturbed = results.stream().filter( Result::disturbed).count();
		out.println();
		out.println( "! = disturbed (other processes > " + (int) DISTURBED_OTHER_PCT + "% of the machine or swap-in > "
				+ DISTURBED_SWAP_MB + " MB): " + disturbed + " of " + results.size() + " runs"
				+ ( disturbed > 0 ? ", re-measure with --rerun=disturbed" : ""));
		out.flush();
		System.out.println();
		System.out.println( sw);
		String reportName = outName.replace( CSV, "-pareto.txt");
		writeTo( reportName, w -> w.print( sw));
		System.out.println( "results: " + outName + ", report: " + reportName);
	}

	private static void writeFront( List<Result> front, PrintWriter out) {
		out.println( String.format( "%-6s %4s %3s  %-45s %9s %9s %9s %8s %9s %5s %8s %8s %6s %7s",
				"Family", "Lvl", "Thr", "Params", "Ratio", "Size MB", "Wall s", "MB/s", "CPU s", "Eff%", "RSS MB",
				"Test s", "Oth%", "SwapMB"));
		for ( Result r : front) {
			out.println( String.format( Locale.US, "%-6s %4d %3d  %-45s %9.3f %9.2f %9.2f %8.1f %9.2f %5s %8s %8.2f %6s %7s%s",
					r.config.family, r.config.level, r.config.threads, r.config.paramString(), r.ratio(),
					r.size / 1e6, r.comp.wallMS / 1e3, r.inputBytes / 1e3 / Math.max( 1, r.comp.wallMS), r.cpuMS() / 1e3,
					pct( r.efficiency()), r.comp.maxRssKB < 0 ? "?" : String.valueOf( r.comp.maxRssKB / 1024),
					r.test != null ? r.test.wallMS / 1e3 : 0.0, pct( r.comp.otherPct),
					r.comp.swapInMB < 0 ? "?" : String.valueOf( r.comp.swapInMB), r.disturbed() ? " !" : ""));
		}
	}

	private static String pct( double v) {
		return v < 0 ? "?" : String.format( Locale.US, "%.0f", v);
	}

	private static void writeTo( String filename, Consumer<PrintWriter> writeJob) {
		try ( PrintWriter out =
				      new PrintWriter( new BufferedWriter( new FileWriter( filename, true)))) {
			writeJob.accept( out);
		} catch ( IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return kompletter Pfad zum Executable
	 * @throws IOException
	 * @param binName Name des Binary
	 * @param installFolder Namensteil des Installverzeichnisses
	 */
	private static String findCommand(String binName, String installFolder) throws IOException {
		String osName = System.getProperty("os.name");
		if (osName.contains("Win")) {
			binName += ".exe";
		}
		// typischer Install-Pfad
		final String instFolder = "C:\\Program Files\\" + installFolder;
		final File instFolderF = new File( instFolder);
		if ( instFolderF.exists()&& instFolderF.isDirectory()) {
			String	instPath = instFolder + File.separator +  binName;
			final File instFile = new File( instPath);
			if ( instFile.exists() && instFile.canExecute()) {
				return instPath;
			}
		}

		Map<String, String> env = System.getenv();
		String envpath = env.get( "PATH");
		if ( envpath == null) {
			envpath = env.get( "Path");
		}
		if ( envpath == null) {
			return binName;
		}
		String[] pathA = envpath.split( File.pathSeparator);
		for ( String path : pathA) {
			String fullPath = path + File.separator + binName;
			File file = new File( fullPath);
			if ( file.exists() && file.canExecute()) {
				return file.getCanonicalPath();
			}
		}
		if ( new File( binName).canExecute()) {
			return binName;
		}
		return null;
	}

	private static List<File> findFiles() {
		final Path path = new File( System.getProperty( "user.dir")).toPath();
		try ( Stream<Path> files = Files.list( path)) {
			return files
					.map( p -> p.toFile())
					.filter( f -> f.getName().endsWith( ".log"))
					.sorted()
					.collect( Collectors.toList());
		} catch ( IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
