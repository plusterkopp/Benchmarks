package misc;

import java.util.*;

public class ListProperties {

	public static void main( String args[]) {
		Properties props = System.getProperties();
		Map<Object, Object> map = new TreeMap<>();
		props.entrySet().forEach( entry -> map.put( entry.getKey(), entry.getValue()));
		for ( Object key : map.keySet()) {
			Object value = map.get( key);
			System.out.println( "  " + key + ": " + value);
		}
	}
}
