package de.icubic.mm.communication.util;

import org.HdrHistogram.Histogram;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TestOffsetHistogram {

	static int offsetBig = 1000_000;
	static int offsetSmall = 1000;

	Histogram	histogram;
	OffsetHistogram	histogramOffset0;
	OffsetHistogram	histogramOffsetSmall;
	OffsetHistogram	histogramOffsetBig;

	List<Histogram> histograms;
	List<OffsetHistogram> offsetHistograms;
	final int[] values;

	final int minValue;

	public TestOffsetHistogram(int[] valuesParam) {
		values = valuesParam;
		Arrays.sort( values);
		minValue = IntStream.of( values).min().getAsInt();
	}

	@Parameterized.Parameters
	public static Collection<int[]> data() {
		List<int[]> data = new ArrayList<>();

		int[] values = { -5, 1, 2, 3, 10, 11, 12};
		data.add( values);

		addDataSet( data, 100, offsetBig);
		addDataSet( data, 100, offsetSmall);

		addDataSet( data, 10_000, offsetBig);

		System.out.println( "data: "
			+ data.stream().map( arr -> Arrays.toString( arr)).collect(Collectors.joining( ", "))
		);

		return data;
	}

	private static void addDataSet(List<int[]> data, int size, int offsetBig) {
		Random rnd = new Random();
		int[] values = new int[ size];
		int boundHalf = offsetBig / 2;

		for ( int i = 0;  i < size;  i++) {
			values[ i] = rnd.nextInt(offsetBig) - boundHalf;
		}
		data.add( values);
	}

	@Before
	public void setUp() throws Exception {
		int digits = 5;
		histogram = new Histogram(digits);
		histogramOffset0 = new OffsetHistogram( 0, digits);
		histogramOffsetSmall = new OffsetHistogram(offsetSmall, digits);
		histogramOffsetBig = new OffsetHistogram(offsetBig, digits);

		histogram.setTag( "Base");
		histogramOffset0.setTag( "Offset " + histogramOffset0.offset);
		histogramOffsetSmall.setTag( "Offset " + histogramOffsetSmall.offset);
		histogramOffsetBig.setTag( "Offset " + histogramOffsetBig.offset);

		offsetHistograms = new ArrayList<>();
		offsetHistograms.add( histogramOffsetSmall);
		offsetHistograms.add( histogramOffsetBig);

		histograms = new ArrayList<>( offsetHistograms);

		offsetHistograms.removeIf( h -> h.offset + minValue < 0);

		offsetHistograms.forEach( h -> {
			for ( long v : values) {
				h.recordValue(v);
			}
		});
	}

	@Test
	public void getLowestDiscernibleValue() {
		long lowestDiscernibleValue = histogram.getLowestDiscernibleValue();
		offsetHistograms.forEach( oh -> {
			assertEquals( "wrong lowest discernible for " + oh.getTag() + " values: " + printValues( values),
					lowestDiscernibleValue, oh.getLowestDiscernibleValue() + oh.offset
			);
		});
	}

	private String printValues( int[] values) {
		if ( values.length <= 100) {
			return Arrays.toString( values);
		}
		return "[" + values[ 0]
			+ ".."
			+ values[ values.length - 1] + "]";
	}

	@Test
	public void getMinValue() {
		long min = values[ 0];
		offsetHistograms.forEach( h -> {
			if ( min != h.getMinValue()) {
				h.getMinValue();
			}
			OffsetHistogram.assertLongEquivalent( "wrong min value for " + h.getTag() + " values: " + printValues( values),
				min, h, Histogram::getMinValue);
//			assertEquals( "wrong min value for " + h.getTag() + " values: " + Arrays.toString( values),
//					min, h.getMinValue()
//			);
		});
	}

	@Test
	public void getMaxValue() {
		long max = values[ values.length - 1];
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertLongEquivalent( "wrong max value for " + h.getTag() + " values: " + printValues( values),
				max, h, Histogram::getMaxValue);
//			assertEquals( "wrong max value for " + h.getTag() + " values: " + Arrays.toString( values),
//					max, h.getMaxValue()
//			);
		});
	}

	@Test
	public void getMinNonZeroValue() {
		long minv = 0;
		for ( int v: values) {
			if ( v != 0) {
				minv = v;
				break;
		}	}
		long min = minv;
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertLongEquivalent( "wrong min non zero value for " + h.getTag() + " values: " + printValues( values),
				min, h, Histogram::getMinNonZeroValue
			);
//			assertEquals( "wrong min non zero value for " + h.getTag() + " values: " + Arrays.toString( values),
//					min, h.getMinNonZeroValue()
//			);
		});
	}

	@Test
	public void getMaxValueAsDouble() {
		double max = values[ values.length - 1];
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertDoubleEquivalent( "wrong max value for " + h.getTag() + " values: " + printValues( values),
				max, h, Histogram::getMaxValueAsDouble
			);
//			assertEquals( "wrong max value for " + h.getTag() + " values: " + Arrays.toString( values),
//					max, h.getMaxValueAsDouble(), 1e-8
//			);
		});
	}

	@Test
	public void getMean() {
		double sum = 0;
		for ( int v: values) {
			sum += v;
		}
		double mean = sum / values.length;
		offsetHistograms.forEach( h -> {
			OffsetHistogram.assertDoubleEquivalent( "wrong mean for " + h.getTag() + " values: " + printValues( values),
				mean, h, Histogram::getMean);
//			assertEquals( "wrong mean for " + h.getTag() + " values: " + Arrays.toString( values),
//					mean, h.getMean(), 1e-8
//			);
		});
	}

	@Test
	public void getValueAtPercentile() {
		offsetHistograms.forEach( h -> {
			if (h.getTotalCount() == 0) {
				return;
			}
			double[] percentiles = {0, 0.01, 0.1, 1, 10, 50, 90, 99, 99.9, 99.99, 100};
			double[] valuesAtPercentile = new double[percentiles.length];
			StringBuilder sb = new StringBuilder();
			sb.append("percentiles " + Arrays.toString(percentiles));
			sb.append(" for values " + printValues(values) + "  ");
			for (int i = 0; i < percentiles.length - 1; i++) {
				valuesAtPercentile[i] = h.getValueAtPercentile(percentiles[i]);
				sb.append(percentiles[i]).append(": ");
				sb.append(valuesAtPercentile[i]).append(",  ");
			}
			System.out.println(sb);
		});
	}

	@Test
	public void getCountBetweenValues() {
		offsetHistograms.forEach( h -> {
			if ( h.getTotalCount() == 0) {
				return;
			}
			Random rnd = new Random();
			for (int i = 0; i < 100; i++) {
				int max = 1 + values[values.length - 1];
				long a = rnd.nextInt(max);
				long b = rnd.nextInt(max);
				long low = Math.min(a, b);
				long high = Math.max(a, b);
				long count = 0;
				for (int v : values) {
					if (v >= low && v <= high) {
						count++;
					}
				}
				long actual = h.getCountBetweenValues(low, high);
				Assert.assertTrue("wrong count between " + low + " and " + high
						+ " for " + h.getTag() + " values: " + printValues(values) + "\n"
					+ "Expected: " + count + "\nActual: " + actual,
					Math.abs( count - actual) < 3);
			}
		});
	}

}
