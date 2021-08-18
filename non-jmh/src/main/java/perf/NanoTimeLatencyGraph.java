package perf;

import org.HdrHistogram.*;
import org.apache.commons.math3.distribution.*;
import perf.perfutils.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class NanoTimeLatencyGraph {

	static class HistogramPanel extends GraphPanel {

		private final DoubleHistogram histogram;

		public HistogramPanel(DoubleHistogram h) {
			super(Collections.emptyList(), 0, 1);
			histogram = h;
		}

		public void paintComponent(Graphics g) {
			// zeichne Histogramm ein
			int uWidth = getUsableWidth();
			List<Double> scores = new ArrayList<>( uWidth);
			double lastX = 0;
			for ( int i = 0;  i < uWidth;  i++) {
				double x = ( double) i / uWidth;
//				double x = ( double) i / uWidth * histogram.getMaxValue();
				double y =
//						 histogram.getValueAtPercentile( x * 100);
						histogram.getCountBetweenValues( lastX, x);
//						histogram.getPercentileAtOrBelowValue( x);
				scores.add( y);
				lastX = x;
			}
			setScores( scores);
			super.paintComponent(g);
		}
	}

	public static void main( String... args) {
		DoubleHistogram histogram = new DoubleHistogram( 4);

		int maxCount = 10000;
		long    then = System.nanoTime();
		long    now;
		long    latency = -1;
		for ( int i = 0;  i < maxCount;  i++) {
			try {
				now = System.nanoTime();
				latency = now - then;
				histogram.recordValue( latency);
				then = now;
			} catch ( ArrayIndexOutOfBoundsException e) {
				throw new IllegalArgumentException( "Sample(" + i + ") = " + latency, e);
			}
		}
		SwingUtilities.invokeLater(
				() -> createAndShowGUI( histogram));
	}

	private static void createAndShowGUI(DoubleHistogram histogram) {
//		System.out.println("Created GUI on EDT? " + SwingUtilities.isEventDispatchThread());
		JFrame f = new JFrame("NanoTimeLatencyGraph");
		f.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);

		HistogramPanel panel = new HistogramPanel(histogram);
		f.setSize( panel.getPreferredSize());
		f.add( panel);
		f.pack();
		f.setVisible(true);

	}
}
