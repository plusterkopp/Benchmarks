package misc;

import java.util.SortedMap;
import java.util.TreeMap;

public class Powers {
	public static void main(String[] args) {
		int maxResult = 1000;

		int maxBase = (int) (Math.sqrt( maxResult) + 1);
		SortedMap<Integer, String> powers = new TreeMap<>();
		for ( int base = 2;  base < maxBase;  base++) {
			int exponent = 2;
			int result = (int) Math.round( Math.pow( base, exponent));
			while (result <= maxResult) {
				String term = base + "^" + exponent;
				powers.putIfAbsent( result, term);

				exponent++;
				result = (int) Math.round( Math.pow( base, exponent));
			}
		}
		System.out.println( "powers: " + powers);
	}
}
