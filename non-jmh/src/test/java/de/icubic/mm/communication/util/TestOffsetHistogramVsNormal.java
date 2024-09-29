package de.icubic.mm.communication.util;

import org.HdrHistogram.Histogram;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class TestOffsetHistogramVsNormal {

	Histogram	histogram;
	OffsetHistogram	histogramOffset0;
	OffsetHistogram	histogramOffset100;

	List<Histogram> histograms;
	List<OffsetHistogram> offsetHistograms;
	final int[] values;

	public TestOffsetHistogramVsNormal(int[] valuesParam) {
		values = valuesParam;
		Arrays.sort( values);
	}

	@Parameterized.Parameters
	public static Collection<int[]> data() {
		List<int[]> data = new ArrayList<>();

		int[] values = { 0, 1, 2, 3, 10, 11, 12};
		data.add( values);

		int[] emptyValues = { };
		data.add( emptyValues);

		int[] values0 = { 0};
		data.add( values0);

		int[] values1 = { 1};
		data.add( values1);

		SortedSet<Integer> bigSet = new TreeSet<>();
		Random rnd = new Random();
		int baseA[] = { 0, 1_000, 1_000_000, 1_000_000_000};

		for ( int base: baseA) {
			for ( int i = 0;  i < 100;  i++) {
				bigSet.add( base + rnd.nextInt( 1000000));
			}
			int[] bigValues = bigSet.stream().mapToInt(Integer::intValue).toArray();
			data.add( bigValues);
			bigSet.clear();
		}

		System.out.println( "data: "
			+ data.stream().map( arr -> Arrays.toString( arr)).collect(Collectors.joining( ", "))
		);

		return data;
	}

	@Before
	public void setUp() throws Exception {
		int digits = 5;
		histogram = new Histogram(digits);
		histogramOffset0 = new OffsetHistogram( 0, digits);
		histogramOffset100 = new OffsetHistogram( 100, digits);

		histogram.setTag( "Base");
		histogramOffset0.setTag( "Offset 0");
		histogramOffset100.setTag( "Offset 100");

		offsetHistograms = new ArrayList<>();
		offsetHistograms.add( histogramOffset0);
		offsetHistograms.add( histogramOffset100);

		histograms = new ArrayList<>( offsetHistograms);
		histograms.add( histogram);

		histograms.forEach( h -> {
			for ( long v : values) {
				h.recordValue(v);
			}
		});
	}

	@Test
	public void getLowestDiscernibleValue() {
		long lowestDiscernibleValue = histogram.getLowestDiscernibleValue();
		offsetHistograms.forEach( oh -> {
			assertEquals( "wrong lowest discernible for " + oh.getTag() + " values: " + Arrays.toString( values),
					lowestDiscernibleValue, oh.getLowestDiscernibleValue() + oh.offset
			);
		});
	}

	@Test
	public void getMinValue() {
		long min = histogram.getMinValue();
		offsetHistograms.forEach( h -> {
			if ( min != h.getMinValue()) {
				h.getMinValue();
			}
			assertEquals( "wrong min value for " + h.getTag() + " values: " + Arrays.toString( values),
					min, h.getMinValue()
			);
		});
	}

	@Test
	public void getMaxValue() {
		long max = histogram.getMaxValue();
		offsetHistograms.forEach( h -> {
			assertTrue( "wrong max value for " + h.getTag() + " values: " + Arrays.toString( values),
					OffsetHistogram.isLongEquivalentFor( max, h, Histogram::getMaxValue)
			);
		});
	}

	@Test
	public void getMinNonZeroValue() {
		long min = histogram.getMinNonZeroValue();
		offsetHistograms.forEach( h -> {
			assertTrue( "wrong min non zero value for " + h.getTag() + " values: " + Arrays.toString( values),
					OffsetHistogram.isLongEquivalentFor( min, h, Histogram::getMinNonZeroValue)
			);
		});
	}

	@Test
	public void getMaxValueAsDouble() {
		double max = histogram.getMaxValueAsDouble();
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertDoubleEquivalent(
				"wrong max value for " + h.getTag() + " values: " + Arrays.toString( values),
				max, h, Histogram::getMaxValueAsDouble);
		});
	}

	@Test
	public void getMean() {
		double mean = histogram.getMean();
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertDoubleEquivalent(
				"wrong mean for " + h.getTag() + " values: " + Arrays.toString( values),
				mean, h, Histogram::getMean);
		});
	}

	@Test
	public void getValueAtPercentile() {
		if ( histogram.getTotalCount() == 0) {
			return;
		}
		double[] percentiles = { 0, 0.01, 0.1, 1, 10, 50, 90, 99, 99.9, 99.99, 100};
		double[] values = new double[ percentiles.length];
		for ( int i = 0;  i < percentiles.length - 1;  i++) {
			values[ i] = histogram.getValueAtPercentile( percentiles[ i]);
		}
		offsetHistograms.forEach( h -> {
			for ( int i = 0;  i < percentiles.length - 1;  i++) {
				assertEquals( "wrong value at " + percentiles[ i]
								+ " for " + h.getTag() + " values: " + Arrays.toString( this.values),
						values[ i], h.getValueAtPercentile( percentiles[ i]), 1e-8);
			}
		});
	}

	@Test
	public void getPercentileAtOrBelowValue() {
		double[] percentiles = new double[ values.length];
		for ( int i = 0;  i < percentiles.length - 1;  i++) {
			percentiles[ i] = histogram.getPercentileAtOrBelowValue( values[ i]);
		}
		offsetHistograms.forEach( h -> {
			for ( int i = 0;  i < percentiles.length - 1;  i++) {
				assertEquals( "wrong value at " + values[ i] + " for " + h.getTag() + " values: " + Arrays.toString( values),
						percentiles[ i], h.getPercentileAtOrBelowValue( values[ i]), 1e-8);
			}
		});
	}

	@Test
	public void getCountBetweenValues() {
		if ( histogram.getTotalCount() == 0) {
			return;
		}
		Random rnd = new Random();
		for ( int i = 0;  i < 100;  i++) {
			int max = 1 + values[values.length - 1];
			long a = rnd.nextInt( max);
			long b = rnd.nextInt( max);
			long low = Math.min( a, b);
			long high = Math.max( a, b);
			long count = histogram.getCountBetweenValues(low, high);
			offsetHistograms.forEach( h -> {
				boolean ok = OffsetHistogram.isLongEquivalentFor(count, h, h0 -> h0.getCountBetweenValues(low, high));
				assertTrue( "wrong count between "
					+ low + " and " + high + " for " + h.getTag() + " values: " + Arrays.toString( values),
					ok);
			});
		}
	}

	@Test
	public void getCountAtValue() {
		if ( histogram.getTotalCount() == 0) {
			return;
		}
		Random rnd = new Random();
		for ( int i = 0;  i < 100;  i++) {
			int max = 1 + values[values.length - 1];
			long a = rnd.nextInt( max);
			long count = histogram.getCountAtValue( a);
			offsetHistograms.forEach( h -> {
				assertEquals( "wrong count at " + a + " for " + h.getTag() + " values: " + Arrays.toString( values),
						count, h.getCountAtValue( a));
			});
		}
	}
}
