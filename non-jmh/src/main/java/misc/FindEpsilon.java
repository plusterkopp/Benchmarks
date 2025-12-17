package misc;

import java.text.NumberFormat;
import java.text.ParseException;

public class FindEpsilon {

	public static void main(String[] args) {
		findStep();
//		findStringMismatch();
	}

	private static void findStringMismatch() {
		NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setMinimumFractionDigits( 16);
		nf.setGroupingUsed( false);

		double start = 60.1;
		double inc = 0.01;
		long loop = 0;
		double next = start + ( loop * inc);
		String valueOf = String.valueOf( next);
		String nfNoZeros = format( nf, next, valueOf);
		while ( valueOf.equals( nfNoZeros)) {
			loop++;
			next = start + ( loop * inc);
			valueOf = String.valueOf( next);
			nfNoZeros = format( nf, next, valueOf);
		}
		double dNF = 0;
		try {
			dNF = nf.parse( nfNoZeros).doubleValue();
			System.out.println( "loops: " + loop + " v=" + valueOf + " nf=" + nfNoZeros + " dNF=" + dNF);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static String format( NumberFormat nf, double d, String valueOf) {
		String s = nf.format( d);
		int end;
		int vLen = valueOf.length();
		for ( end = s.length() - 1;  end > ( vLen-1) && s.charAt( end) == '0' && s.charAt( end-1) != '.';  end--);
		return s.substring( 0, end+1);
	}

	private static void findStep() {
		NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setMinimumFractionDigits( 17);
		NumberFormat nfi = NumberFormat.getIntegerInstance();
		nfi.setGroupingUsed( true);

		double d = 3000;
		double e = d;
		String dS = nf.format( d);
		String eS = dS;
		long l = 0;
		double ulp = 0;

		while ( dS.equals( eS)) {
			dS = eS;
			d = e;

			ulp = Math.ulp(d);
			e = d + ulp;

			l++;
			eS = nf.format( e);
			if ( l % 1000_000 == 0) {
				System.out.println( "loops: " + l + " d=" + dS + " e=" + eS + " ulp=" + ulp);
			}
		}
		System.out.println( "loops: " + l + " d=" + dS + " e=" + eS + " ulp=" + ulp);
	}
}
