package de.tsp;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Random;

public class Tsp9 {

    static class Point {
        public double x;
        public double y;
    }

    static double sqr(double x) {
        return x * x;
    }

    static void tsp(Point cities[], Point tour[], int ncities) {
        int i, j;
        int ClosePt = 0;
        double CloseDist;
        int endtour = 0;

        for (i = 1; i < ncities; i++) {
            tour[i] = cities[i - 1];
        }
        tour[0] = cities[ncities - 1];

        for (i = 1; i < ncities; i++) {
            double ThisX = tour[i - 1].x;
            double ThisY = tour[i - 1].y;
            CloseDist = Double.MAX_VALUE;
            for (j = ncities - 1;; j--) {
                double ThisDist = sqr(tour[j].x - ThisX);
                if (ThisDist <= CloseDist) {
                    ThisDist += sqr(tour[j].y - ThisY);
                    if (ThisDist <= CloseDist) {
                        if (j < i)
                            break;
                        CloseDist = ThisDist;
                        ClosePt = j;
                    }
                }
            }
            swap(i, ClosePt, tour); // &tour[i],&tour[ClosePt]);
        }
    }

    private static void swap(int i, int j, Point[] tour) {
        Point t = tour[i];
        tour[i] = tour[j];
        tour[j] = t;
    }

    public static void main(String[] args) {
        long start0NS = System.nanoTime();
        int i, ncities;
        double sumdist = 0.0;

        if (args.length != 1) {
            System.err.println("usage: <ncities>");
            System.exit(1);
        }
        ncities = Integer.valueOf(args[0]);
        Point cities[] = new Point[ncities];
        Arrays.setAll(cities, ii -> new Point());
        Point tour[] = new Point[ncities];
        Random rnd = new Random(0);
        for (i = 0; i < ncities; i++) {
            cities[i].x = rnd.nextDouble();
            cities[i].y = rnd.nextDouble();
        }
        FileWriter psfile;
        try {
            psfile = new FileWriter("tspj.eps");
            long start1NS = System.nanoTime();
            tsp(cities, tour, ncities);
            long end1NS = System.nanoTime();
            psfile.write("%%!PS-Adobe-2.0 EPSF-1.2\n%%%%BoundingBox: 0 0 300 300\n");
            psfile.write("1 setlinejoin\n0 setlinewidth\n");
            psfile.write("" + (300.0 * tour[0].x) + " " + (300.0 * tour[0].y) + " moveto\n");
            for (i = 1; i < ncities; i++) {
                psfile.write("" + (300.0 * tour[i].x) + " " + (300.0 * tour[i].y) + " lineto\n");
                sumdist += dist(tour, i - 1, i);
            }
            psfile.write("stroke\n");
            psfile.close();
            long end0NS = System.nanoTime();
            double dur0S = 1e-9 * ( end0NS - start0NS);
            double dur1S = 1e-9 * ( end1NS - start1NS);
            System.out.println("sumdist = " + sumdist);
            NumberFormat nf = DecimalFormat.getNumberInstance();
            nf.setMaximumFractionDigits(3);
            System.out.println("took " + nf.format( dur0S) + " s, raw = " + nf.format( dur1S) + " s");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static double dist(Point[] tour, int i, int j) {
        Point p = tour[i];
        Point q = tour[j];
        return Math.sqrt(sqr(p.x - q.x) + sqr(p.y - q.y));
    }

}