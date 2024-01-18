package misc;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public class ConvertChars {

	static Map<String, List<Character>> validChars = new HashMap<>();

	public static void main( String args[]) {
		printChars( "iso-8859-15");
		printChars( "cp1252");

		validChars.forEach( ( name, list) -> listCodePoints( name, list));
	}

	private static void listCodePoints(String charSetName, List<Character> charList) {
		System.out.println( charSetName + ":");
		for ( int i = 0;  i < charList.size();  i++) {
			char c = charList.get( i);
			System.out.print( String.format( "%5d=", i) + c);
			System.out.print( String.format( "(%4x)", ( int) c));
			if ( i % 16 == 15) {
				System.out.println();
			}
		}
		System.out.println();
	}

	private static void printChars( String charSetName) {
		System.out.println( charSetName + ":");
		Charset cs = Charset.forName( charSetName);
		CharsetEncoder enc = cs.newEncoder();
		CharsetDecoder dec = cs.newDecoder();
		ByteBuffer bb = ByteBuffer.allocate( 3000);
		for ( int b = 0;  b < 256;  b++) {
			byte asByte = (byte) (b & 0xff);
//			System.out.println( "int=" + Integer.toHexString(b)
//					+ " byte=" + Integer.toHexString( asByte));
			// decode byte as char
			if ( cs == Charset.forName( "cp1252")
				&& ( asByte == -127
					|| asByte == -115
					|| asByte == -113
					|| asByte == -112
					|| asByte == -99
			)) {
				bb.put( (byte) 0);
			} else {
				bb.put( asByte);
			}
		}
		bb.flip();
		CharBuffer cb = null;
		try {
			cb = dec.decode(bb);
		} catch ( Exception e) {
			System.out.flush();
			System.err.println( "cannot decode " + bb.get( bb.position()) + " for " + charSetName);
			e.printStackTrace();
			System.err.flush();
			return;
		}

		List<Character> vc = validChars.computeIfAbsent(charSetName, dummy -> new ArrayList<>());

		int indexA[] = { 0};
		IntConsumer printChar = c -> {
			char cc = (char) c;
			int index = indexA[ 0];
			indexA[ 0]++;
			if (index % 16 == 0) {
				System.out.print( String.format( "%2x   ", index));
			}
//			if ( c == '\r' || c == '\n' || c == '\t' || c == '\b') {
			if ( Character.isISOControl( cc)) {
				System.out.print( "× ");
			} else {
				System.out.print( cc + " ");
				vc.add( cc);
			}
			if (index % 16 == 8) {
				System.out.print( " ");
			}
			if (index % 16 == 15) {
				System.out.println();
			}
		};
		for ( char c: cb.array()) {
			printChar.accept( c);
		}
	}
}
