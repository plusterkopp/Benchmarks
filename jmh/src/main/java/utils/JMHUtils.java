package utils;

import org.openjdk.jmh.results.*;
import org.openjdk.jmh.util.*;

import java.io.*;
import java.util.*;

public class JMHUtils {

	/**
	 * use only with AvgTime results
	 * @param results
	 * @param baselineName
	 */
	public static void reportWithBaseline(Collection<RunResult> results, String baselineName) {

		PrintWriter pw = new PrintWriter(System.out, true);

		Result baselineResult = null;
		for (RunResult r : results) {
			String name = simpleName(r.getParams().getBenchmark());
			if ( name.equalsIgnoreCase( baselineName)) {
				baselineResult = r.getPrimaryResult();
			}
		}
		if ( baselineResult == null) {
			return;
		}

		double baselineScore = baselineResult.getScore();
		pw.printf("removed baseline result\n");
		for (RunResult r : results) {
			String name = simpleName(r.getParams().getBenchmark());
			Result primaryResult = r.getPrimaryResult();
			double score = primaryResult.getScore() - baselineScore;
			Statistics statistics = primaryResult.getStatistics();
			double scoreError = statistics.getMeanErrorAt(0.99);
			pw.printf("%30s: %11.3f ± %10.3f ns%n", name, score, scoreError);
		}
	}

	private static String simpleName(String qName) {
		int lastDot = Objects.requireNonNull(qName).lastIndexOf('.');
		return lastDot < 0 ? qName : qName.substring(lastDot + 1);
	}


}
