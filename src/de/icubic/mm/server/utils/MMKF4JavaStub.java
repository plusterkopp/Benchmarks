package de.icubic.mm.server.utils;

import java.util.*;

/**
 * Created by rhelbing on 30.05.2017.
 */
public class MMKF4JavaStub {

	private static final int MillisPerDay_I = 1000 * 60 * 60 * 24;
	private static final double MillisPerDay_D = MillisPerDay_I;

	private static long zeroDate;
	private static long utcOffset0;

	/**
	 * Liefert das 0 Uhr Excel-Datum, des mitgelieferten Datums. Dabei spielt die mitgegebene
	 * Uhrezeit keine Rolle. Wir rechnen mit GMT Zeiten.
	 * @param date
	 * @return
	 */
	public static int toExcelDate( Date date) {
		return ( int) toAccurateExcelDate( date);
	} // date

	/**
	 * Liefert das exakte Excel-Datum, inclusive Uhrzeit. Wir rechnen mit GMT Zeiten.
	 * @param date
	 * @return
	 */
	public static double toAccurateExcelDate( Date date) {
		if (date == null)
			throw new IllegalArgumentException ("date can't be null");
		long timeStampMillis = utcTimeStampFromDate( date) - zeroDate;
		double d = timeStampMillis * (1.0 / MillisPerDay_D);
//		System.out.println( "accExcelDate: " + d + " for " + date);
		// Warum dieses +3 ????  weil wir nicht das ECHTE Zerodate hatten
		// wir tun aber +1 dazu, weil Windows einen Bug hat, der verkennt, daß 1900 KEIN Schaltjahr war.
		// wir emulieren diesen BUG
		if ( d > 60)	// das machen wir natürlich erst NACH dem falschen Schalttag
			d++;
		return d;
	} // date

	/**
	 * wir könnten das auch auf {@link #fromAccurateExcelDate(double)} abbilden, aber es läuft vielleicht minimal schneller in int/long
	 * @param excelDate
	 * @return
	 */
	public static Date fromExcelDate( int excelDate) {
		if (excelDate > 61) {
			excelDate--;
		}
//		System.out.println( "fromExcelDate: " + excelDate);
		// we don't have dates before year 1900, so we can assume positive values here and cast
		return dateFromUTCTimeStamp(zeroDate + MillisPerDay_I * (long) excelDate);
	}

	public static Date fromAccurateExcelDate( double excelDate) {
		if (excelDate > 61) {
			excelDate--;
		}
//		System.out.println( "fromAccExcelDate: " + excelDate);
		long timeStampMillis = zeroDate + (long) (MillisPerDay_D * excelDate);
		Date	result = dateFromUTCTimeStamp( timeStampMillis);
		return result;
	}

	public static Date dateFromUTCTimeStamp( long timeStampMillis) {
		TimeZone tzDefault = TimeZone.getDefault();
		long	utcOffset = tzDefault.getOffset( timeStampMillis);
		Date	utcDate = new Date( timeStampMillis);
		Date date = new Date(timeStampMillis - utcOffset + utcOffset0);
//		System.out.println( "took " + utcOffset + " ms from utcDate " + utcDate + " to get " + date);
		return date;
	}

	public static long utcTimeStampFromDate( Date date) {
		long	ts = date.getTime();
		TimeZone tzDefault = TimeZone.getDefault();
		long	utcOffset = tzDefault.getOffset( ts);
		long utcTimeStamp = ts+ utcOffset - utcOffset0;
//		System.out.println( "added " + utcOffset + " ms to date " + date + " to get utc timestamp for " + new Date( utcTimeStamp));
		return utcTimeStamp;
	}

}
