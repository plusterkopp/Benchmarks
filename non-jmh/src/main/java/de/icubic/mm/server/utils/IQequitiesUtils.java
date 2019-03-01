package de.icubic.mm.server.utils;

import java.util.*;

/**
 *	o	Alle Utils die so bei Equities anfallen
 *	o	eventuell setzt sichs ja durch
 *
 * @author	rschott
 * @since	13.03.2007
 */
public class IQequitiesUtils {

	public static <T> List<T> List( T...elements) {
		return Arrays.asList( elements);
	}

	public static <T> Set<T> Set( T...elements) {
		return new HashSet<T>( List( elements));
	}
}
