package perf.perfutils;

import java.util.*;

public class AxisLabelGenerator {

	int numLabels;
	double min;
	double max;

	public AxisLabelGenerator(int numLabels, double min, double max) {
		this.numLabels = numLabels;
		this.min = min;
		this.max = max;
	}

	public double[] computeLabels() {
		double  width = max - min;
		double  step = computeStep( width);
		int steps = 1 + (int) Math.round( width / step);
		List<Double> list = new ArrayList<>( steps);
		double v = roundTo( min, step);
		if ( v >= min) {
			list.add( v);
		}
		double max2 = max + step / 2;
		do {
			v = v + step;
			if ( v <= max2) {
				list.add( v);
			}
		} while( v <= max2);
//		// fehlt der letzte Wert?
//		if ( Math.abs( v - max) > step / 1000) {
//			list.add( v);
//		}

		double labels[] = new double[ list.size()];
		for ( int i = 0;  i < labels.length;  i++) {
			labels[ i] = list.get( i);
		}
//		System.out.println( min + " - " + max + " -> " + Arrays.toString( labels));
		return labels;
	}

	private double roundTo(double v, double step) {
		double vScaled = v / step;
		double vScaledR = Math.round( vScaled);
		double vR = vScaledR * step;
		return vR;
	}

	private double computeStep(double width) {
		double  widthDivided = width / numLabels;
		double  logWidth = Math.log10( widthDivided);
		double  logWidthFloor = Math.floor( logWidth);
		double  logWidthFrac = logWidth - logWidthFloor;
		double  logWidthFrac3 = logWidthFrac * 3;
		int  logWidthFrac3R = (int) Math.round( logWidthFrac3);
		double  step;
		switch ( logWidthFrac3R) {
			case 0:
				step = Math.pow(10, logWidthFloor); break;
			case 1:
				step = 2 * Math.pow(10, logWidthFloor); break;
			case 2:
				step = 5 * Math.pow(10, logWidthFloor); break;
			case 3:
				step = 10 * Math.pow(10, logWidthFloor); break;
			default:
				throw new IllegalStateException("width = " + width
						+ " divided = " + widthDivided
						+ " log = " + logWidth
						+ " logFloor = " + logWidthFloor
						+ " logFrac = " + logWidthFrac
						+ " logFrac3 = " + logWidthFrac3
						+ " logFrac3R = " + logWidthFrac3R
				);
		}
		return step;
	}

}
