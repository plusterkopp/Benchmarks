package misc;

public class FloatingDiffs {

	public static void main(String[] args) {
		double offsetsA[] = { 0.1, 0.5, 0.61, 0.7244};
		for ( double offset: offsetsA) {
			findMismatch( offset);
		}
	}

	private static void findMismatch(double fractionalPart) {
		int hits = 0;
		int maxHits = 3;
		for ( long aI = 0;  hits <= maxHits && aI < Integer.MAX_VALUE;  aI++) {
			long bI = aI + 1;
			double aD = aI + fractionalPart;
			double bD = bI + fractionalPart;
			double b_a = bD - aD;
			if (b_a != 1) {
				System.out.println( "a = " + aD + ", b = " + bD
					+ " b-a-1 = " + ( b_a - 1) + ", rel: " + ( ( b_a - 1) / bD));
				hits++;
			}
		}
	}
}
