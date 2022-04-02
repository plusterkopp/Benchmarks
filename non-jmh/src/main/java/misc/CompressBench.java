/**
 *
 */
package misc;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 *
 * versucht, einen brauchbare Parameterkombination zu finden, die Kompressionsrate und Ressourcenbedarf (RAM, Zeit)
 * unter einen Hut bringt.
 *
 * @author rhelbing
 *
 */
public class CompressBench {

	private static final String CSV_SEP = "\t";
//	private static final String LZMA2 = "LZMA2";
//	private static final String PPMD = "PPMd";
//	private static final String RAR = "RAR";
//	private static final String JZIP = "JZIP";
//	private static final String ZIP7 = "ZIP7";
	private static final String levelPrefix = "-mx=";
	private static final String threadPrefix = "-mmt=";
	private static final String typePrefix = "-t";
	private static final String mainArgsPreArchive = "a";
	/** solid Blockgröße 1TB */
	private static final String solidArg = "-ms=1t";
	/** disable StdOut */
	private static final String loggingArg = "-bso0";

	private static final int	maxThreads = Runtime.getRuntime().availableProcessors();

	private static final DateFormat	df = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	private static final DateFormat outFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
	private static final DecimalFormat nfI = new DecimalFormat( "#,###", new DecimalFormatSymbols( Locale.US));
	public static final String COMPRESS_BENCH = "compressBench-";
	public static final String CSV = ".csv";

	private static enum EMethod {
		LZMA2 {
			@Override
			public String getSuffix() {
				return ".7z";
			}
			@Override
			protected String initCommandPath() {
				return findZipCommand();
			}
			@Override
			public List<String> createArgsList(RunSpec spec,String archivePath, List<String> sources) {
				List<String> argList = new ArrayList<>();
				argList.add( commandPath);
				argList.add( mainArgsPreArchive);
				argList.add( archivePath);
				argList.add( solidArg);
				argList.add( loggingArg);
				argList.add( levelPrefix + spec.level);
				argList.add( typePrefix + "7z");
				argList.add( threadPrefix + spec.threads);
				// dict als -md
				String	methodSpec = "-m0=" + LZMA2;
				if ( spec.dictExp != -1) {	// Dict nicht nur default
					methodSpec += ":d=" + spec.dictExp;
				}
//				methodSpec += ":fb=" + 64;
				argList.add( methodSpec);
				argList.addAll( sources);
				return argList;
			}
		}, PPMd {
			@Override
			public String getSuffix() {
				return ".7z";
			}
			@Override
			protected String initCommandPath() {
				return findZipCommand();
			}
			@Override
			public List<String> createArgsList(RunSpec spec,String archivePath, List<String> sources) {
				List<String> argList = new ArrayList<>();
				argList.add( commandPath);
				argList.add( mainArgsPreArchive);
				argList.add( archivePath);
				argList.add( solidArg);
				argList.add( loggingArg);
				argList.add( levelPrefix + spec.level);
				argList.add( typePrefix + "7z");
				// dict als -md
				String	methodSpec = "-m0=" + PPMd;
				if ( spec.dictExp != -1) {	// Dict nicht nur default
					methodSpec += ":mem=" + spec.dictExp;
				}
//				methodSpec += ":fb=" + 64;
				argList.add( methodSpec);
				argList.addAll( sources);
				return argList;
			}
		}, ZIP7 {
			@Override
			public String getSuffix() {
				return ".zip";
			}
			@Override
			protected String initCommandPath() {
				return findZipCommand();
			}
			@Override
			public List<String> createArgsList(RunSpec spec,String archivePath, List<String> sources) {
				List<String> argList = new ArrayList<>();
				argList.add( commandPath);
				argList.add( mainArgsPreArchive);
				argList.add( archivePath);
//				argList.add( solidArg); // kein solid für ZIP
				argList.add( loggingArg);
				argList.add( levelPrefix + spec.level);
				argList.add( typePrefix + "zip");
				argList.add( threadPrefix + spec.threads);
				argList.addAll( sources);
				return argList;
			}
		}, RAR {
			@Override
			public String getSuffix() {
				return ".rar";
			}
			@Override
			protected String initCommandPath() {
				try {
					return findCommand( "rar", "WinRAR");
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			}
			@Override
			public List<String> createArgsList(RunSpec spec,String archivePath, List<String> sources) {
				List<String> argList = new ArrayList<>();
				argList.add( commandPath);
				argList.add( mainArgsPreArchive);
				argList.add( "-s");
				argList.add( "-m" + spec.level);
				argList.add( "-ma5");
				argList.add( "-mt" + spec.threads);
				if ( spec.dictExp > 29) {
					argList.add( "-md" + ( 1 << (spec.dictExp-30)) + "g");
				} else if ( spec.dictExp > 19) {
					argList.add( "-md" + ( 1 << (spec.dictExp-20)) + "m");
				} else if ( spec.dictExp > 9) {
					argList.add( "-md" + ( 1 << (spec.dictExp-10)) + "k");
				}
				argList.add( archivePath);
				argList.addAll( sources);
				return argList;
			}
		}, JZIP {
			@Override
			public String getSuffix() {
				return ".zip";
			}
			@Override
			protected String initCommandPath() {
				return null;
			}
			@Override
			public List<String> createArgsList(RunSpec spec, String archivePath, List<String> sources) {
				return null;
			}
		};

		final String  commandPath;

		EMethod() {
			commandPath = initCommandPath();
		}

		public abstract String getSuffix();
		protected abstract String initCommandPath();

		/**
		 * erzeugt Pattern mit #0: EXE, #1: SRC, #2: DEST
		 * */
		public abstract List<String> createArgsList( RunSpec spec, String archivePath, List<String> sources);
	};

	private static class RunSpec implements Comparable<RunSpec> {
		final EMethod method;
		final int	threads;
		final int	dictExp;
		final int	level;

		private RunSpec(@NotNull EMethod method, int threads, int dictExp, int level) {
			this.method = method;
			this.threads = threads;
			this.dictExp = dictExp;
			this.level = level;
		}

		public static RunSpec getInstance( String methodName, String levelS, String threadsS, String dictS) {
			try {
				EMethod method = EMethod.valueOf( methodName);
				int l = Integer.parseInt( levelS);
				int t = Integer.parseInt( threadsS);
				int d = Integer.parseInt( dictS);
				RunSpec spec = new RunSpec(method, t, d, l);
				return spec;
			} catch ( Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public int compareTo(@NotNull RunSpec other) {
			if ( method != other.method) {
				return method.compareTo( other.method);
			}
			if ( level != other.level) {
				return Integer.compare( level, other.level);
			}
			if ( threads != other.threads) {
				return Integer.compare( threads, other.threads);
			}
			if ( dictExp != other.dictExp) {
				return Integer.compare( dictExp, other.dictExp);
			}
			return 0;
		}

		public List<String> createArgsList(String archive, List<String> sources) {
			return method.createArgsList( this, archive, sources);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			RunSpec spec = (RunSpec) o;

			if (threads != spec.threads) return false;
			if (dictExp != spec.dictExp) return false;
			if (level != spec.level) return false;
			return method == spec.method;
		}

		@Override
		public int hashCode() {
			int result = method != null ? method.hashCode() : 0;
			result = 31 * result + threads;
			result = 31 * result + dictExp;
			result = 31 * result + level;
			return result;
		}

		@Override
		public String toString() {
			return "RunSpec{" +
					" " + method +
					", lvl=" + level +
					", thr=" + threads +
					", dict=" + dictExp +
					'}';
		}
	}

	private static class Result {
		RunSpec runSpec;
		long	durMS;
		long	size;
		double	ratio;
		public Result( RunSpec runSpec, long durMS, long size, double ratio) {
			this.runSpec = runSpec;
			this.durMS = durMS;
			this.size = size;
			this.ratio = ratio;
		}
	}

	public static void main( String args[]) {
		// Dateien zum Komprimieren suchen: aktuelles Verzeichnis, alle Logs
		try {
			List<File> files = findFiles();
			runBenchmarks( files);
		} catch ( Exception e) {
			e.printStackTrace();
		}
	}

	private static List<RunSpec> generateSpecs() {
		final int threadsExpMin = 0;
		final int threadsExpMax = 7;
		final int dictExpMin = 24;
		final int dictExpMax = 33;  // wahrscheinlich nur bis 32 (4G) möglich
		final int levelMin = 0;
		final int levelMax = 9;

		List<Integer> threadsList = new ArrayList<>();
		for ( int t = 1;  t <= maxThreads;  t *= 2) {
			threadsList.add( t);
		}

		int levelsA[] = { 0, 1, 3, 5, 7, 9};

		List<RunSpec> specs = new ArrayList<>();
		RunSpec spec;
		// 7z LZMA2: erstmal ohne dict
		for ( int level: levelsA) {
			for (int threads : threadsList) {
				spec = new RunSpec(EMethod.LZMA2, threads, -1, level);
				specs.add(spec);
			}
		}
		// 7z LZMA2: dicts nur auf Level 9
		for ( int dictExp = dictExpMin;  dictExp <= dictExpMax; dictExp++) {
			for (int threads : threadsList) {
				spec = new RunSpec(EMethod.LZMA2, threads, dictExp , 9);
				specs.add(spec);
			}
		}
		// 7z PPMd: nur level und dict (single thread)
		for ( int level: levelsA) {
			for ( int dictExp = dictExpMin;  dictExp <= dictExpMax; dictExp++) {
				spec = new RunSpec(EMethod.PPMd, 1, dictExp, level);
				specs.add(spec);
			}
		}
		// 7z ZIP: erstmal ohne dict
		for ( int level: levelsA) {
			for (int threads : threadsList) {
				spec = new RunSpec(EMethod.ZIP7, threads, -1, level);
				specs.add(spec);
			}
		}
		// RAR auto
		for ( int level = 0;  level <= 5;  level++) {
			for (int threads : threadsList) {
				spec = new RunSpec(EMethod.RAR, threads, -1, level);
				specs.add(spec);
			}
		}
		// RAR dict
		for ( int dictExp = dictExpMin;  dictExp <= dictExpMax; dictExp++) {
			for (int threads : threadsList) {
				spec = new RunSpec(EMethod.RAR, threads, dictExp, 5);
				specs.add(spec);
			}
		}
		// als SortedSet (damit gleiche rausfliegen), dann wieder als Liste
		SortedSet<RunSpec>  specSet = new TreeSet<>( specs);
		specs = new ArrayList<>( specSet);
		return specs;
	}

	private static void runBenchmarks(List<File> files) throws Exception {
		List<String>	namesList = files.stream()
				.map( f -> f.getName())
				.collect( Collectors.toList());
		final long totalSize = files.stream()
				.map( f -> f.length())
				.reduce( 0L, ( s, l) -> s + l);

		List<RunSpec> specs = generateSpecs();
		File oldCSV = findOldCSV( files.get( 0).getParentFile());
		if ( oldCSV != null) {
			removeOldSpecs( oldCSV, specs);
		}

		String outName = buildOutName();
		writeHeaders( outName);

		StringBuilder	sb = new StringBuilder( "Compress Bench on " + namesList);
		for ( RunSpec spec: specs) {
			Result result = new Result( spec, 0, 0, 0);
			long then = System.currentTimeMillis();
			System.out.print( "\n" + "started " + df.format( new Date( then)));
			try {
				runBenchmark(result, namesList);
				long durMS = System.currentTimeMillis() - then;
				System.out.print("\n" + "finished " + df.format(new Date(then + durMS)) + " (" + nfI.format(durMS) + " ms)");
				result.durMS = durMS;
				result.ratio = ((double) totalSize) / result.size;
				writeTo(outName, out -> writeResult(result, out));
			} catch ( Throwable t) {
				System.err.println( "error running spec: " + spec);
				t.printStackTrace();
			}
		}
	}

	private static void removeOldSpecs(File csv, List<RunSpec> specs) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(csv));
			String  line;
			System.out.println( "looking for old results in: " + csv);
			while ( ( line = reader.readLine()) != null) {
				String[] contentA = line.split("\t");
				if ( contentA != null && contentA.length >= 4) {
					RunSpec spec = RunSpec.getInstance(contentA[0], contentA[1], contentA[2], contentA[3]);
					if ( spec == null) {
						System.out.println( "not a run spec: " + line);
					} else {
						boolean removed = specs.remove(spec);
						if ( removed) {
							System.out.println( "not repeating spec: " + line);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static File findOldCSV( File folder) {
		String[] fileNames = folder.list();
		if ( fileNames == null) {
			System.err.println( "not a directory: " + folder);
			return null;
		}
		SortedSet<String> csvNamesSorted = new TreeSet<>();
		for (String fileName : fileNames) {
			File file = new File(fileName);
			if ( file.isFile()) {
				if (fileName.startsWith(COMPRESS_BENCH) && fileName.endsWith(CSV)) {
					csvNamesSorted.add(fileName);
		}   }   }
		if ( csvNamesSorted.isEmpty()) {
			return null;
		}
		String latest = csvNamesSorted.last();
		return new File( latest);
	}

	private static String buildOutName() {
		Date d = new Date();
		String	dateS = outFormat.format( d);
		return COMPRESS_BENCH + dateS + CSV;
	}

	private static void writeResult( Result r, PrintWriter out) {
		DecimalFormat nf = new DecimalFormat( "#,###.#######", new DecimalFormatSymbols( Locale.US));
		DecimalFormat nfI = new DecimalFormat( "#,###", new DecimalFormatSymbols( Locale.US));
		out.print( r.runSpec.method + CSV_SEP);
		out.print( r.runSpec.level + CSV_SEP);
		out.print( r.runSpec.threads + CSV_SEP);
		out.print( r.runSpec.dictExp + CSV_SEP);
		out.print( nfI.format( r.size) + CSV_SEP);
		out.print( nf.format( r.ratio) + CSV_SEP);
		out.print( nfI.format( r.durMS) + CSV_SEP);
		out.println();
		out.flush();
	}

	private static void writeHeaders( String filename) {
		writeTo( filename, out -> {
			String headersA[] = { "Method", "Level", "Threads", "Dict", "Size", "Ratio", "Time"};
			for ( String h : headersA) {
				out.print( h + CSV_SEP);
			}
			out.println();
		});
	}

	private static void writeTo( String filename, Consumer<PrintWriter> writeJob) {
		try ( PrintWriter out =
				      new PrintWriter( new BufferedWriter( new FileWriter( filename, true)))) {
			writeJob.accept( out);
		} catch ( IOException e) {
			e.printStackTrace();
		}
	}

	private static void runBenchmark( Result result,
	                                    List<String> namesList) throws Exception {
		RunSpec spec = result.runSpec;
		// Archivname
		String tempArchName = "tempArch-" + System.currentTimeMillis() + spec.method.getSuffix();
		List<String> argList = spec.createArgsList( tempArchName, namesList);
				new ArrayList<>();
		System.out.print( "\n" + "Args = " + argList);
		// Prozeß erzeugen und starten
		ProcessBuilder pb = new ProcessBuilder( argList);
		pb.redirectErrorStream( true);
		pb.inheritIO();
		Process process = pb.start();
		process.waitFor();
		// Ausgabe holen, Ende abwarten
		// String outputString = captureOutput( process);
//		outputString += "\n" + "Args = " + argList;
		// Größe holen
		File archive = new File( tempArchName);
//		outputString += "\n" + "Archive Size = " + archive.length();
		System.out.print( "\n" + "Archive Size = " + archive.length());
		result.size = archive.length();
		archive.delete();
	}

	private static String captureOutput( Process process) throws InterruptedException {
		// fange Ausgaben ein
		BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream()));
		StringBuilder sb = new StringBuilder();
		Thread watch = new Thread( "io capture") {
			@Override
			public void run() {
				String line = null;
				try {
					while ( (line = reader.readLine()) != null) {
						sb.append(line);
						sb.append(System.getProperty("line.separator"));
					}
				} catch ( IOException e) {
					e.printStackTrace();
				}
			}
		};
		watch.start();
		watch.join();
//		process.waitFor();
		return sb.toString();
	}

	private static String findZipCommand() {
		try {
			return findCommand( "7z", "7-Zip");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return kompletter Pfad zum 7z-Executable
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
			List<File>	result = files
					.map( p -> p.toFile())
					.filter( f -> f.getName().endsWith( ".log"))
					.collect( Collectors.toList());
			files.close();
			return result;
		} catch ( IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
