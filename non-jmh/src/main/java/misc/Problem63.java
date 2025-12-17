package misc;

public class Problem63 {

	public static void main(String[] args) {
		Problem63 p = new Problem63();
		p.run();
	}


	private void run() {
		int aM = 0;
		int bM = 0;
		int cM = 0;
		int dM = 0;
		int maxSum = 0;
		long loops = 0;
		long loopM = 0;
		for ( int a = 63;  a > 0;  a-- ) {
			for ( int b = 63 - a;  b > 0;  b--) {
				if ( a + b >= 63) {
					continue;
				}
				for ( int c = 63 - a - b;  c > 0;  c--) {
					int abc = a+b+c;
					if ( abc >= 63) {
						continue;
					}
					loops++;
					int d = 63 - abc;

					int sum = a * b + b * c + c * d;
					if ( sum > maxSum) {
						maxSum = sum;
						aM = a;
						bM = b;
						cM = c;
						dM = d;
						loopM = loops;
					}
				}
			}
		}
		System.out.println(
			"a=" + aM + " b=" + bM + " c=" + cM + " d=" + dM
			+ "   ab=" + aM*bM
			+ " bc=" + bM*cM
			+ " cd=" + cM*dM
			+ "   sum=" + maxSum
			+ "   loop=" + loopM
		);
	}
}
