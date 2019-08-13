package perf.perfutils;

import org.junit.*;

import java.util.stream.*;

import static org.junit.Assert.*;

public class AxisLabelGeneratorTest {

	@Test
	public void computeLabels_1to10() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 10, 1, 10);
		double[] result = g.computeLabels();
		double[] expected = { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

	@Test
	public void computeLabels_Neg() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 10, -5, 5);
		double[] result = g.computeLabels();
		double[] expected = DoubleStream.iterate( -5.0, d -> d + 1.0).limit( 11).toArray();
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

	@Test
	public void computeLabels_Neg1() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 10, -5.1, 5.1);
		double[] result = g.computeLabels();
		double[] expected = DoubleStream.iterate( -5.0, d -> d + 1.0).limit( 11).toArray();
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

	@Test
	public void computeLabels_Neg1a() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 12, -5.1, 5.1);
		double[] result = g.computeLabels();
		double[] expected = DoubleStream.iterate( -5.0, d -> d + 1.0).limit( 11).toArray();
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

	@Test
	public void computeLabels_Neg1b() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 12, -4.6, 4.7);
		double[] result = g.computeLabels();
		double[] expected = DoubleStream.iterate( -4.0, d -> d + 1.0).limit( 9).toArray();
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

	@Test
	public void computeLabels_Neg2() {
		AxisLabelGenerator g;
		g = new AxisLabelGenerator( 23, -4.6, 4.7);
		double[] result = g.computeLabels();
		double[] expected = DoubleStream.iterate( -4.5, d -> d + 0.5).limit( 19).toArray();
		assertArrayEquals( "Feld weicht ab", expected, result, 0.000001);
	}

}