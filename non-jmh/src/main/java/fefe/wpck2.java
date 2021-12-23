package fefe;

import gnu.trove.iterator.*;
import gnu.trove.map.hash.*;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;


public class wpck2 {
	public static void main(final String[] args) throws Exception {

		impl2();
	}

	private static void impl1() throws IOException {
		final long start = System.currentTimeMillis();

		final Map<String, Integer> map = new ConcurrentHashMap<>(1048576);

		try (final FileInputStream fis = new FileInputStream(FileDescriptor.in)) {
			final FileChannel channel = fis.getChannel();
			CharsetDecoder decoder = Charset.forName("ISO-8859-1").newDecoder();
			Reader reader = Channels.newReader(channel, decoder, 1048576);
			final BufferedReader bufferedReader = new BufferedReader(reader, 262144);
			final Stream<String> stringStream = bufferedReader.lines();

			stringStream.parallel()
					.map( line -> line.split( " "))
					.forEach( strings -> {
						for ( String s: strings) {
							map.merge( s, 1, (oldVal, val) -> oldVal + 1);
						}
					});
		}

		final long finishMapping = System.currentTimeMillis();
		System.err.println("mapping time: " + ( finishMapping - start));

		final StringBuilder sb = new StringBuilder();
		map.entrySet().stream().parallel()
				.sorted((a, b) -> {
					int valueCompare = b.getValue() - a.getValue();
					if ( valueCompare != 0) {
						return valueCompare;
					}
					return b.getKey().compareTo( a.getKey());
				})
				.sequential()
				.forEach(e -> sb
						.append(e.getValue())
						.append(' ')
						.append(e.getKey())
						.append('\n'));

		final long finishSorting = System.currentTimeMillis();
		System.err.println("sorting time: " + ( finishSorting - finishMapping));

		System.out.println(sb.toString());

		final long finishPrinting = System.currentTimeMillis();
		System.err.println("printing time: " + ( finishPrinting - finishSorting));
		System.err.println("total time: " + ( finishPrinting - start));
	}

	private static void impl2() throws IOException, InterruptedException {
		final long start = System.currentTimeMillis();

		int capacity = 1048576;
		final Map<String, Integer> map = new ConcurrentHashMap<>( capacity);
		final int nThreads = Runtime.getRuntime().availableProcessors();
		ArrayList[] stringLists = new ArrayList[nThreads];
		TObjectIntHashMap[] maps = new TObjectIntHashMap[nThreads];
		for (int i = 0; i < nThreads; i++) {
			stringLists[i] = new ArrayList(capacity);
			maps[i] = new TObjectIntHashMap(capacity);
		}

		try (final FileInputStream fis = new FileInputStream(FileDescriptor.in)) {
			final FileChannel channel = fis.getChannel();
			CharsetDecoder decoder = Charset.forName("ISO-8859-1").newDecoder();
			Reader reader = Channels.newReader(channel, decoder, capacity);
			final BufferedReader bufferedReader = new BufferedReader(reader, 262144);

			String line;
			int index = 0;
			while ((line = bufferedReader.readLine()) != null) {
				stringLists[index].add(line);
				if (++index == nThreads) {
					index = 0;
				}
			}
		}
		final long finishedReading = System.currentTimeMillis();
		System.err.println("reading time: " + ( finishedReading - start));

		ExecutorService es = Executors.newFixedThreadPool( nThreads);
		for ( int i = 0;  i < nThreads;  i++) {
			int fi = i;
			es.execute( () -> {
				ArrayList<String> list = stringLists[fi];
				TObjectIntHashMap<String> mapL = maps[fi];
				for (String s : list) {
					String[] splitA = s.split(" ");
					for (String token : splitA) {
						mapL.adjustOrPutValue(token, 1, 1);
					}
				}
			});
		}
		es.shutdown();
		es.awaitTermination( 10, TimeUnit.MINUTES);
		int totalSize = 0;
		for ( TObjectIntHashMap m: maps) {
			totalSize += m.size();
		}

		final long finishMapping = System.currentTimeMillis();
		System.err.println("mapping time: " + ( finishMapping - finishedReading));

		Iterator<Map.Entry<String, Integer>> iterator = new Iterator<Map.Entry<String, Integer>>() {
			int index = 0;
			TObjectIntIterator<String> subIterator = maps[ index].iterator();
			@Override
			public boolean hasNext() {
				boolean hasNext = subIterator.hasNext();
				if (hasNext) {
					return true;
				}
				if ( ++index == nThreads) {
					return false;
				}
				subIterator = maps[ index].iterator();
				return subIterator.hasNext();
			}

			@Override
			public Map.Entry<String, Integer> next() {
				subIterator.advance();
				return new Map.Entry<String, Integer>() {
				    String key = subIterator.key();
				    int value = subIterator.value();
					@Override
					public String getKey() {
						return key;
					}

					@Override
					public Integer getValue() {
						return value;
					}

					@Override
					public Integer setValue(Integer value) {
						return subIterator.setValue( value);
					}
				};
			}
		};
		int characteristics = Spliterator.ORDERED | Spliterator.SIZED | Spliterator.NONNULL;
		Spliterator spliterator = Spliterators.spliterator( iterator, totalSize, characteristics);
		Stream<Map.Entry<String, Integer>> stream = StreamSupport.stream( spliterator, false);

		final StringBuilder sb = new StringBuilder();
		stream
				.parallel()
				.sorted((a, b) -> {
					int valueCompare = b.getValue() - a.getValue();
					if ( valueCompare != 0) {
						return valueCompare;
					}
					return b.getKey().compareTo( a.getKey());
				})
				.sequential()
				.forEach(e -> sb
						.append(e.getValue())
						.append(' ')
						.append(e.getKey())
						.append('\n'));

		final long finishSorting = System.currentTimeMillis();
		System.err.println("sorting time: " + ( finishSorting - finishMapping));

		System.out.println(sb.toString());

		final long finishPrinting = System.currentTimeMillis();
		System.err.println("printing time: " + ( finishPrinting - finishSorting));
		System.err.println("total time: " + ( finishPrinting - start));
	}
}
