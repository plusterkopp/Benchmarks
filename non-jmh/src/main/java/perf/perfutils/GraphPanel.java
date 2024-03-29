package perf.perfutils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.text.*;
import java.util.List;
import java.util.*;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Rodrigo
 */
public class GraphPanel extends JPanel {

	private int width = 800;
	private int heigth = 400;
	private int padding = 25;
	private int labelPadding = 25;
	private Color lineColor = new Color(44, 102, 230, 40);
	private Color pointColor = new Color(100, 100, 100, 180);
	private Color gridColor = new Color(200, 200, 200, 200);
	private static final Stroke GRAPH_STROKE = new BasicStroke(1f);
	private int pointWidth = 4;
	private int numberYDivisions = 10;
	private List<Double> scores;

	double xMin;
	double xMax;

	public GraphPanel(List<Double> scores, double xMin, double xMax) {
		this.scores = scores;
		this.xMin = xMin;
		this.xMax = xMax;
	}

	protected int getUsableWidth() {
		return getWidth() - (2 * padding) - labelPadding;
	}
	protected int getUsableHeight() {
		return getHeight() - (2 * padding) - labelPadding;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int uWidth = getUsableWidth();
		double xScale = (double) uWidth / (scores.size() - 1);
		int uHeight = getUsableHeight();
		double yMax = getMaxScore();
		double yMin = getMinScore();
		double yScale = (double) uHeight / (yMax - yMin);

		List<Point> graphPoints = new ArrayList<>();
		for (int i = 0; i < scores.size(); i++) {
			int x1 = (int) (i * xScale + padding + labelPadding);
			int y1 = (int) ((yMax - scores.get(i)) * yScale + padding);
			graphPoints.add(new Point(x1, y1));
		}

		// draw white background
		g2.setColor(Color.WHITE);
		g2.fillRect(padding + labelPadding, padding, uWidth, uHeight);
		g2.setColor(Color.BLACK);

		// create hatch marks and grid lines for y axis.
		FontMetrics metrics = g2.getFontMetrics();
		Rectangle2D bounds = metrics.getStringBounds("1", g2);
		int labelHeight = (int) bounds.getHeight();
		AxisLabelGenerator alg = new AxisLabelGenerator(getUsableWidth() / ( 5 * labelHeight), yMin, yMax);
		double[] yLabels = alg.computeLabels();
		NumberFormat nf = DecimalFormat.getNumberInstance();
		nf.setMaximumFractionDigits( 5);

		for (int i = 0; i < yLabels.length; i++) {
			double yLabel = yLabels[i];
			int x0 = padding + labelPadding;
			int x1 = pointWidth + padding + labelPadding;
			int y = (int) Math.round( ( yLabel - yMin) / ( yMax - yMin) * uHeight);
//			int y0 = getHeight() - ((i * uHeight) / numberYDivisions + padding + labelPadding);
			int y0 = getHeight() - (y + padding + labelPadding);
			int y1 = y0;
			if (scores.size() > 0) {
				g2.setColor(gridColor);
				g2.drawLine(padding + labelPadding + 1 + pointWidth, y0, getWidth() - padding, y1);
				g2.setColor(Color.BLACK);
//				String yLabel = ((int) ((yMin + (yMax - yMin) * ((i * 1.0) / numberYDivisions)) * 100)) / 100.0 + "";
				String label = nf.format( yLabel);
				int labelWidth = metrics.stringWidth( label);
				g2.drawString( label, x0 - labelWidth - 5, y0 + (metrics.getHeight() / 2) - 3);
			}
			g2.drawLine(x0, y0, x1, y1);
		}

		// and for x axis
		// x-Label Breite schätzen
		int labelWidth = estimateLabelWidth( g2);
		alg = new AxisLabelGenerator(getUsableWidth() / ( 3 * labelWidth), xMin, xMax);
		double[] xLabels = alg.computeLabels();
		for (int i = 0; i < xLabels.length; i++) {
			if (scores.size() > 1) {
				double  xLabel = xLabels[ i];
				int x0 = (int) Math.round(( xLabel - xMin) * uWidth) + padding + labelPadding;
//				int x0 = i * uWidth / (scores.size() - 1) + padding + labelPadding;
				int x1 = x0;
				int y0 = getHeight() - padding - labelPadding;
				int y1 = y0 - pointWidth;
//				if ((i % ((int) ((scores.size() / 20.0)) + 1)) == 0) {
					g2.setColor(gridColor);
					g2.drawLine(x0, getHeight() - padding - labelPadding - 1 - pointWidth, x1, padding);
					g2.setColor(Color.BLACK);
					String label = nf.format( xLabel);
					labelWidth = metrics.stringWidth( label);
					g2.drawString( label, x0 - labelWidth / 2, y0 + metrics.getHeight() + 3);
//				}
				g2.drawLine(x0, y0, x1, y1);
			}
		}

		// create x and y axes
		g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, padding + labelPadding, padding);
		g2.drawLine(padding + labelPadding, getHeight() - padding - labelPadding, getWidth() - padding, getHeight() - padding - labelPadding);

		Stroke oldStroke = g2.getStroke();
		g2.setColor(lineColor);
		g2.setStroke(GRAPH_STROKE);
		for (int i = 0; i < graphPoints.size() - 1; i++) {
			int x1 = graphPoints.get(i).x;
			int y1 = graphPoints.get(i).y;
			int x2 = graphPoints.get(i + 1).x;
			int y2 = graphPoints.get(i + 1).y;
			g2.drawLine(x1, y1, x2, y2);
		}

		g2.setStroke(oldStroke);
		g2.setColor(pointColor);
		for (int i = 0; i < graphPoints.size(); i++) {
			int x = graphPoints.get(i).x - pointWidth / 2;
			int y = graphPoints.get(i).y - pointWidth / 2;
			int ovalW = pointWidth;
			int ovalH = pointWidth;
			g2.fillOval(x, y, ovalW, ovalH);
		}
	}

	private int estimateLabelWidth(Graphics2D g2) {
		FontMetrics metrics = g2.getFontMetrics();
		// erster Versuch: generiere Wert in der Mitte des Intervalls, sieh, wieviele NKS wir dort bekommen
		AxisLabelGenerator alg = new AxisLabelGenerator(3, xMin, xMax);
		double[] xLabels = alg.computeLabels();
		NumberFormat nf = DecimalFormat.getNumberInstance();
		nf.setMaximumFractionDigits( 5);
		// formatiere mit max 5 NKS, die wir hoffentlich nicht ausschöpfen
		String midLabel = nf.format( xLabels[1]);
		int labelWidth = metrics.stringWidth( midLabel);
		return labelWidth;
	}

	@Override
    public Dimension getPreferredSize() {
        return new Dimension(width, heigth);
    }

	private double getMinScore() {
		double minScore = Double.MAX_VALUE;
		for (Double score : scores) {
			minScore = Math.min(minScore, score);
		}
		return minScore;
	}

	private double getMaxScore() {
		double maxScore = Double.MIN_VALUE;
		for (Double score : scores) {
			maxScore = Math.max(maxScore, score);
		}
		return maxScore;
	}

	public void setScores(List<Double> scores) {
		this.scores = scores;
		invalidate();
		this.repaint();
	}

	public List<Double> getScores() {
		return scores;
	}

	private static void createAndShowGui() {
		List<Double> scores = new ArrayList<>();
		Random random = new Random();
		int maxDataPoints = 40;
		int maxScore = 10;
		for (int i = 0; i < maxDataPoints; i++) {
			scores.add((double) random.nextDouble() * maxScore);
            scores.add((double) i);
		}
		GraphPanel mainPanel = new GraphPanel(scores, 0, 1);
		mainPanel.setPreferredSize(new Dimension(800, 600));
		JFrame frame = new JFrame("DrawGraph");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(mainPanel);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGui();
			}
		});
	}
}