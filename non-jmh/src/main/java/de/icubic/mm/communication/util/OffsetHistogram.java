package de.icubic.mm.communication.util;

import org.HdrHistogram.Histogram;
import org.junit.Assert;

import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class OffsetHistogram extends Histogram {

	/**
	 * Test Support
	 * @param value  value to test for
	 * @param h histogram to test in and to call getter with
	 * @param getter obtains the reference value whose lower and upper equivalents are used
	 * @return true if value lies within lower and upper equivalent values in h for what getter returns for h
	 */
	public static boolean isLongEquivalentFor(long value, Histogram h, ToLongFunction<Histogram> getter) {
		long ref = getter.applyAsLong(h);
		if ( value < h.lowestEquivalentValue( ref)) {
			return false;
		}
		if ( value > h.highestEquivalentValue( ref)) {
			return false;
		}
		return true;
	}

	public static void assertLongEquivalent( String message, long value, Histogram h, ToLongFunction<Histogram> getter) {
		long actual = getter.applyAsLong(h);

		long lowEqActual = h.lowestEquivalentValue( actual);
		long lowEqValue = h.lowestEquivalentValue( value);
		long upperEqActual = h.highestEquivalentValue( actual);
		long upperEqValue = h.highestEquivalentValue( value);

		if ( lowEqActual != lowEqValue || upperEqValue != upperEqActual) {
			for ( long l = Math.min( value, actual) - 1;  l < Math.max( value, actual) + 2;  l++) {
				System.out.println( l + " in " + h.lowestEquivalentValue( l) + " - " + h.highestEquivalentValue( l));
			}
		}

		Assert.assertTrue( message
			+ "\n" + "expected: " + value
			+ " actual: " + actual
				+ " eq in [" + lowEqValue + ", " + upperEqValue + "]"
				+ " act in [" + lowEqActual + ", " + upperEqActual + "]"
			, lowEqActual == lowEqValue
				|| upperEqValue == upperEqActual
			);
	}

	/**
	 * Test Support
	 * @param value  value to test for
	 * @param h histogram to test in and to call getter with
	 * @param getter obtains the reference value whose lower and upper equivalents are used
	 * @return true if value lies within lower and upper equivalent values in h for what getter returns for h
	 */
	public static boolean isDoubleEquivalentFor(double value, Histogram h, ToDoubleFunction<Histogram> getter) {
		long ref = (long) getter.applyAsDouble( h);
		if ( value < h.lowestEquivalentValue( ref)) {
			return false;
		}
		if ( value > h.highestEquivalentValue( ref)) {
			return false;
		}
		return true;
	}

	public static void assertDoubleEquivalent( String message, double value, Histogram h, ToDoubleFunction<Histogram> getter) {
		long actual = (long) getter.applyAsDouble( h);

		long lowEqActual = h.lowestEquivalentValue( actual);
		long lowEqValue = h.lowestEquivalentValue((long) value);
		long upperEqActual = h.highestEquivalentValue( actual);
		long upperEqValue = h.highestEquivalentValue((long) value);

		Assert.assertTrue( message
				+ "\n" + "expected: " + value
				+ " actual: " + actual
				+ " eq in [" + lowEqValue + ", " + upperEqValue + "]"
				+ " act in [" + lowEqActual + ", " + upperEqActual + "]"
			, lowEqActual == lowEqValue
				|| upperEqValue == upperEqActual
		);
	}


	/**
	 * should be large enough to avoid negative values
	 */
	final long offset;

	public OffsetHistogram( long offset, int numberOfSignificantValueDigits) {
		super(numberOfSignificantValueDigits);
		this.offset = offset;
	}

	public OffsetHistogram( long offset, long highestTrackableValue, int numberOfSignificantValueDigits) {
		super(highestTrackableValue, numberOfSignificantValueDigits);
		this.offset = offset;
	}

	public OffsetHistogram( long offset, long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
		super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
		this.offset = offset;
	}

	@Override
	public void recordValue(long value) throws ArrayIndexOutOfBoundsException {
		super.recordValue(value + offset);
	}

	@Override
	public void recordValueWithCount(long value, long count) throws ArrayIndexOutOfBoundsException {
		super.recordValueWithCount(value + offset, count);
	}

	@Override
	public long getLowestDiscernibleValue() {
		return super.getLowestDiscernibleValue() - offset;
	}

	@Override
	public long getMaxValue() {
		if (getTotalCount() == 0) {
			return super.getMaxValue();
		}

		return super.getMaxValue() - offset;
	}

	@Override
	public long getMinNonZeroValue() {
		long superMinNZ = super.getMinNonZeroValue();
		if (getTotalCount() < 1) {
			return superMinNZ;
		}

		if (superMinNZ == offset) {	// meaning 0
			if (getTotalCount() < 2) {
				return Long.MAX_VALUE;
			}
			return nextNonEquivalentValue( offset) - offset;
		}
		return superMinNZ - offset;
	}

	@Override
	public long getMinValue() {
		long superMin = super.getMinValue();
		if (getTotalCount() == 0) {
			return superMin;
		}

		if ( superMin == 0) {
			return -offset;
		}
		long minNZ = super.getMinNonZeroValue();
		if ( offset > 0 && minNZ == superMin) {
			return 0;
		}
		return minNZ - offset;
	}

	@Override
	public double getMean() {
		if (getTotalCount() == 0) {
			return super.getMean();
		}

		return super.getMean() - offset;
	}

	@Override
	public long getValueAtPercentile(double percentile) {
		long superVaP = super.getValueAtPercentile(percentile);
//		if ( superVaP == 0) {
//			return -offset;
//		}
		// uses lowest/highestEquivalentValue
		return superVaP - offset;
	}

	@Override
	public double getPercentileAtOrBelowValue(long value) {
		return super.getPercentileAtOrBelowValue(value + offset);
	}

	@Override
	public long getCountBetweenValues(long lowValue, long highValue) throws ArrayIndexOutOfBoundsException {
		return super.getCountBetweenValues(lowValue + offset, highValue + offset);
	}

	@Override
	public long getCountAtValue(long value) throws ArrayIndexOutOfBoundsException {
		return super.getCountAtValue(value + offset);
	}

	@Override
	public long lowestEquivalentValue(long value) {
		return super.lowestEquivalentValue(value + offset) - offset;
	}

	@Override
	public long nextNonEquivalentValue(long value) {
		return super.nextNonEquivalentValue(value + offset) - offset;
	}

	@Override
	public long sizeOfEquivalentValueRange(long value) {
		return super.sizeOfEquivalentValueRange( value + offset);
	}
}
