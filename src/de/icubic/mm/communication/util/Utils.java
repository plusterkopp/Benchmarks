package de.icubic.mm.communication.util;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Diverse Hilfsmethoden
 *
 * @author tfunke
 */
public class Utils {

	/**
	 * Epsilon für Intervallarithmetik
	 */
	public final static double EPSILON_PRICES = 1.0E-9;

	public final static double EPSILON_NOMINALS = 1.0E-3;

	public final static int	SECONDS_PER_DAY = 24 * 60 * 60;
	public final static int	MILLIS_PER_DAY = SECONDS_PER_DAY * 1000;

	/** Zeichensatz fuer den Dekoder */
	static Charset charSet = Charset.forName("ISO-8859-1");

	/**
	 * String in ByteBuffer kodieren
	 *
	 * @param message
	 * @return kodierter ByteBuffer
	 * @throws CharacterCodingException
	 */
	public static ByteBuffer encode(String message) throws CharacterCodingException {
		CharsetEncoder encoder = charSet.newEncoder();
		ByteBuffer buf = encoder.encode(CharBuffer.wrap(message));
		return (buf);
	}

	/**
	 * ByteBuffer in String dekodieren
	 *
	 * @param byteBuffer
	 * @return dekodierter String
	 * @throws CharacterCodingException
	 */
	public static String decode(ByteBuffer byteBuffer) throws CharacterCodingException {
		CharsetDecoder decoder = charSet.newDecoder();
		CharBuffer charBuffer = decoder.decode(byteBuffer);
		String result = charBuffer.toString();
		return (result);
	}

	/**
	 * @param msgBuffer
	 * @return in seiner Kapazität um 50% erhöhten ByteBuffer gleichen Inhalts
	 */
	public static ByteBuffer extendByteBuffer(ByteBuffer msgBuffer) {
		msgBuffer.flip();
		int newSize = (int)(msgBuffer.capacity() * 1.5);
		ByteBuffer tmp = ByteBuffer.allocate(newSize);
		tmp.put(msgBuffer);
		return (tmp);
	}

	/**
	 * Umwandlung der Elemente eines Zeichenkettenfeldes in Integer
	 *
	 * @param s
	 *            Zeichenketten-Feld
	 * @return Integer-Feld
	 */
	public static Integer[] stringsToIntegers(String[] s) {
		if (s == null)
			return (null);
		Integer[] ret = new Integer[s.length];
		int i = 0;
		while (i < ret.length) {
			try {
				ret[i] = new Integer(Integer.parseInt(s[i]));
			} catch (NumberFormatException nfe) {
			}
			i++;
		}
		return (ret);
	}

	/**
	 * Umwandlung der Elemente eines Zeichenkettenfeldes in Double
	 *
	 * @param s
	 *            Zeichenketten-Feld
	 * @return Double-Feld
	 */
	public static Double[] stringsToDoubles(String[] s) {
		if (s == null)
			return (null);
		Double[] ret = new Double[s.length];
		int i = 0;
		while (i < ret.length) {
			try {
				ret[i] = new Double(Double.parseDouble(s[i]));
			} catch (NumberFormatException nfe) {
			}
			i++;
		}
		return (ret);
	}

	/**
	 * Umwandlung der Elemente eines Zeichenkettenfeldes in Boolean
	 *
	 * @param s
	 *            Zeichenketten-Feld
	 * @return Boolean-Feld
	 */
	public static Boolean[] stringsToBooleans(String[] s) {
		if (s == null)
			return (null);
		Boolean[] ret = new Boolean[s.length];
		int i = 0;
		while (i < ret.length) {
			try {
				ret[i] = Boolean.valueOf(s[i]);
			} catch (Exception e) {
			}
			i++;
		}
		return (ret);
	}

	/**
	 * Returns all the classes inheriting or implementing a given class in the currently
	 * loaded packages.
	 *
	 * @see http://www.javaworld.com/javaworld/javatips/jw-javatip113.html
	 * @param tosubclassname
	 *            the name of the class to inherit from
	 * @param includeSuperClass
	 *            whether to include tosubclass in resultset or not
	 * @return array of found classes
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T>[] findDescendants(String tosubclassname, boolean includeSuperClass) {
		ArrayList<Class<T>> result = new ArrayList<Class<T>>();
		try {
			Class<?> tosubclass = Class.forName(tosubclassname);
			Package[] pcks = Package.getPackages();
			for (int i = 0; i < pcks.length; ++i) {
				Class<T>[] r = findDescendants(pcks[i].getName(), (Class<T>)tosubclass,
					includeSuperClass);
				for (int c = 0; c < r.length; ++c) {
					result.add(r[c]);
				}
			}
		} catch (ClassNotFoundException ex) {
			System.err.println("Class " + tosubclassname + " not found!");
		}
		return result.toArray((Class<T>[])new Class[result.size()]);
	}

	/**
	 * Display all the classes inheriting or implementing a given class in a given
	 * package.
	 *
	 * @see http://www.javaworld.com/javaworld/javatips/jw-javatip113.html
	 * @param pckgname
	 *            the fully qualified name of the package
	 * @param tosubclass
	 *            the name of the class to inherit from
	 * @param includeSuperClass
	 *            whether to include tosubclass in resultset or not
	 * @return array of found classes
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T>[] findDescendants(String pckname, String tosubclassname,
			boolean includeSuperClass) {
		try {
			return findDescendants(pckname, (Class<T>)Class.forName(tosubclassname),
				includeSuperClass);
		} catch (ClassNotFoundException ex) {
			System.err.println("Class " + tosubclassname + " not found!");
		}
		return (Class<T>[])new Class<?>[0];
	}

	/**
	 * Display all the classes inheriting or implementing a given class in a given
	 * package.
	 *
	 * @see http://www.javaworld.com/javaworld/javatips/jw-javatip113.html
	 * @param pckgname
	 *            the fully qualified name of the package
	 * @param tosubclass
	 *            the Class object to inherit from
	 * @param includeSuperClass
	 *            whether to include tosubclass in resultset or not
	 * @return array of found classes
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T>[] findDescendants(String pckgname, Class<T> tosubclass,
			boolean includeSuperClass) {
		// Translate the package name into an absolute path
		String name = new String(pckgname);
		if (!name.startsWith("/")) {
			name = "/" + name;
		}
		name = name.replace('.', '/');

		// Get a File object for the package
		URL url = tosubclass.getResource(name);
		// URL url = ClassLoader.getSystemClassLoader().getResource(name);

		// Happens only if the jar file is not well constructed, i.e.
		// if the directories do not appear alone in the jar file like here:
		//
		// meta-inf/
		// meta-inf/manifest.mf
		// commands/ <== IMPORTANT
		// commands/Command.class
		// commands/DoorClose.class
		// commands/DoorLock.class
		// commands/DoorOpen.class
		// commands/LightOff.class
		// commands/LightOn.class
		// RTSI.class
		//
		if (url == null)
			return (Class<T>[])new Class<?>[0];

		ArrayList<Class<T>> result = new ArrayList<Class<T>>();
		try {
			File directory = new File(URLDecoder.decode(url.getFile(), "ISO-8859-1"));
			if (directory.exists()) {
				// Get the list of the files contained in the package
				String[] files = directory.list();
				for (int i = 0; i < files.length; i++) {
					// we are only interested in .class files
					if (files[i].endsWith(".class")) {
						// removes the .class extension
						String classname = files[i].substring(0, files[i].length() - 6);
						try {
							Class<?> c = Class.forName(pckgname + "." + classname);
							if (tosubclass.isAssignableFrom(c)
									&& (includeSuperClass || c != tosubclass)) {
								result.add((Class<T>)c);
							}
						} catch (ClassNotFoundException cnfex) {
							System.err.println(cnfex);
						}
					}
				}
			} else {
				try {
					// It does not work with the filesystem: we must
					// be in the case of a package contained in a jar file.
					JarURLConnection conn = (JarURLConnection)url.openConnection();
					String starts = conn.getEntryName();
					JarFile jfile = conn.getJarFile();
					Enumeration<JarEntry> e = jfile.entries();
					while (e.hasMoreElements()) {
						ZipEntry entry = e.nextElement();
						String entryname = entry.getName();
						if (entryname.startsWith(starts)
								&& (entryname.lastIndexOf('/') <= starts.length())
								&& entryname.endsWith(".class")) {
							String classname = entryname.substring(0, entryname.length() - 6);
							if (classname.startsWith("/")) {
								classname = classname.substring(1);
							}
							classname = classname.replace('/', '.');
							try {
								Class<?> c = Class.forName(classname);
								if (tosubclass.isAssignableFrom(c)
										&& (includeSuperClass || c != tosubclass)) {
									result.add((Class<T>)c);
								}
							} catch (ClassNotFoundException cnfex) {
								System.err.println(cnfex);
							}
						}
					}
				} catch (IOException ioex) {
					System.err.println(ioex);
				}
			}
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return result.toArray((Class<T>[])new Class<?>[result.size()]);
	}

	/**
	 * Bildet einen HashCode vom Object (0 bei null)
	 */
	public static int hashCode(Object o) {
		return (o == null)? 0 : o.hashCode();
	}

	/**
	 * null-sichere equals-Methode
	 *
	 * @param o1
	 * @param o2
	 * @return
	 */
	public static boolean safeEquals(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null) {
			return false; // o2 can not be null
		}
		return o1.equals(o2);
	}

	/**
	 * null-sichere compare-Methode
	 *
	 * @param o1
	 * @param o2
	 * @param nullsFirst
	 *            <code>true</code> wenn null-Werte am kleiner als alle anderen sein
	 *            sollen
	 * @return
	 */
	public static <T> int safeCompare(Comparable<T> o1, T o2, boolean nullsFirst) {
		if (o1 == null) {
			if (o2 == null) {
				return 0;
			}
			return nullsFirst ? -1 : 1;
		}
		if (o2 == null) {
			return nullsFirst ? 1 : -1;
		}
		return o1.compareTo(o2);
	}

	/**
	 * Diese Methode holt Thread-sicher ein Element aus der übergebenen Map.
	 *
	 * @param <K>
	 *            Key-Typ
	 * @param <V>
	 *            Value-Typ
	 * @param key
	 *            der Schlüssel
	 * @param map
	 *            die Map
	 * @param lock
	 *            das ReadLock, welches während des Zugriffs aquiriert werden soll
	 * @return der gefundene Wert
	 */
	public static final <K, V> V getObjectByKey(K key, Map<K, V> map, Lock lock) {
		try {
			lock.lock();
			return map.get(key);
		} finally {
			lock.unlock();
		}
	}

	public static final <K, V> V getObjectByKeySynchronized(K key, Map<K, V> map, Object lock) {
		synchronized (lock) {
			return map.get(key);
		}
	}

	public static class MutableBoolean {
		boolean value;

		public MutableBoolean( boolean b) {
			value = b;
		}

		public MutableBoolean() {
			value = false;
		}

		public void set() {
			value = true;
		}

		public void unset() {
			value = false;
		}

		public boolean isSet() {
			return value;
		}

		public void multiply( boolean b) {
			value &= b;
		}

		public void add( boolean b) {
			value |= b;
		}

	}

	public static class MutableInteger extends Number implements Comparable<Integer> {
		/**Default serialVersionUID */
		private static final long serialVersionUID = 1L;

		int value;

		public MutableInteger(int value) {
			this.value = value;
		}

		@Override
		public final int intValue() {
			return value;
		}

		@Override
		public final long longValue() {
			return value;
		}

		@Override
		public final float floatValue() {
			return value;
		}

		@Override
		public final double doubleValue() {
			return value;
		}

		public final int compareTo(Integer i) {
			int thisVal = value;
			int anotherVal = i.intValue();
			return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
		}

		public final int increment() {
			return ++value;
		}

		public final int decrement() {
			return --value;
		}

		public final int setValue(int value) {
			int oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		public final int addValue(int value) {
			return (this.value += value);
		}

		public final int subValue(int value) {
			return (this.value -= value);
		}

		public final Integer getInteger() {
			return Integer.valueOf( this.value);
		}
	}

	public static class MutableDouble extends Number implements Comparable<Double> {
		/**Default serialVersionUID */
		private static final long serialVersionUID = 1L;

		double value;

		public MutableDouble(double value) {
			this.value = value;
		}

		@Override
		public final int intValue() {
			return (int)value;
		}

		@Override
		public final long longValue() {
			return (long)value;
		}

		@Override
		public final float floatValue() {
			return (float)value;
		}

		@Override
		public final double doubleValue() {
			return value;
		}

		public final int compareTo(Double d) {
			double thisVal = value;
			double anotherVal = d.intValue();
			return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
		}

		public final double setValue(double value) {
			double oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		public double add( double v) {
			value += v;
			return value;
		}
	}

	public static class MutableMask implements Comparable<Long> {

		long value = 0;
		int offset;

		public MutableMask( int offset) {
			this.offset = offset;
		}

		public final int getOffset(){
			return offset;
		}

		public final long getValue() {
			return value;
		}

		@Override
		public final int compareTo(Long l) {
			return Utils.safeCompare( Long.valueOf( value), l, true);
		}

		public double addNextValue( int v) {
			value *= offset;
			value += v % offset;
			return value;
		}
	}

	public static class MutableEnum<T extends Enum<?>> {
		T value;

		public MutableEnum( T value) {
			this.value = value;
		}

		public MutableEnum() {
			value = null;
		}

		public void set( T value) {
			this.value = value;
		}

		public T get() {
			return value;
		}
	}

	public static final void safeRun(final Runnable runnable) {
		if (runnable != null) {
			runnable.run();
		}
	}

	public static final class Mutable<T> {
		T value;

		public Mutable() {
			this.value = null;
		}

		public Mutable(T value) {
			this.value = value;
		}

		public final T get() {
			return value;
		}

		public final T set(T value) {
			final T oldValue = this.value;
			this.value = value;
			return oldValue;
		}
	}

	/**
	 * kleine Hilfsklasse um zwei Objekte ein zu sammeln
	 *
	 * @author tkister
	 *
	 * @param <T>
	 */
	public static class TwoObjectCollector<T> {

		/** das erste Object vom Type T */
		protected T firstObject = null;

		/** das zweite Object vom Type T */
		protected T secondObject = null;
	}

	/**
	 * Paare von Objekten als Key in einer Set verwenden
	 * für Hibernate
	 * @param <A>
	 * @param <B>
	 */
	public abstract static class Pair<A, B> {
		private A a = null;
		private B b = null;

		/** Constructor für Hibernate */
		public Pair(){ }

		public Pair(A a, B b) {
			this.a = a;
			this.b = b;
		}

		/** GETTER für das erste Element des Paars */
		protected A getA() {
			return a;
		}

		/** SETTER für das erste Element des Paars */
		protected void setA( A a){
			this.a = a;
		}

		/** GETTER für das zweite Element des Paars */
		protected B getB() {
			return b;
		}

		/** SETTER für das zweite Element des Paars */
		protected void setB( B b){
			this.b = b;
		}

		@Override
		public String toString() {
			return "[" +String.valueOf(a) + ", " + String.valueOf(b) + "]";
		}

		@Override
		public boolean equals(Object x) {
			if ( ! (x instanceof Pair) ){
				return false;
			}
			Pair<?,?> that = (Pair<?,?>) x;
			return safeEquals(this.a, that.a) && safeEquals(this.b, that.b);
		}

		@Override
		public int hashCode() {
			return Utils.hashCode(a) ^ Utils.hashCode(b);
		}
	}

	public static class MutableLong extends Number implements Comparable<Long> {
		/**
		 * Default serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		long value;

		public MutableLong( long value) {
			this.value = value;
		}

		@Override
		public final int intValue() {
			return ( int) value;
		}

		@Override
		public final long longValue() {
			return value;
		}

		@Override
		public final float floatValue() {
			return value;
		}

		@Override
		public final double doubleValue() {
			return value;
		}

		public final int compareTo( Long other) {
			return Long.valueOf( value).compareTo( other);
		}

		public final long increment() {
			return ++value;
		}

		public final long decrement() {
			return --value;
		}

		public final long setValue(long value) {
			long oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		public final long addValue(long value) {
			return (this.value += value);
		}

		public final long subValue(long value) {
			return (this.value -= value);
		}
	}

	public static boolean isStringNullOrEmpty( String value) {
		return isStringNullOrEmpty( value, true);
	}

	public static boolean isStringNullOrEmpty( String value, boolean trim) {
		if ( value == null)
			return true;
		if ( trim) {
			return value.trim().isEmpty();
		}
		return value.isEmpty();
	}

	public static void zeroTime(final Calendar calendar) {
		calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
		calendar.set(java.util.Calendar.MINUTE, 0);
		calendar.set(java.util.Calendar.SECOND, 0);
		calendar.set(java.util.Calendar.MILLISECOND, 0);
	}

	/**
	 * Anzahl der Millisekunden pro Tag.
	 */
	public final static long millisPerDay = 24L * 60 * 60 * 1000;

	public static long getUnixDay(final Calendar calendar) {
		final long offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
		final long day = (long)Math.floor((double)(calendar.getTime().getTime() + offset) / ((double)millisPerDay));
		return day;
	}

	public static boolean isNaNor0( double d) {
		return Double.isNaN( d) || d == 0;
	}

	public static boolean isNaNorNull( Double d) {
		return d == null || d.isNaN();
	}

	/**
	 * Runded den übergebenen Wert kaufmännisch auf eine ganze Zahl.
	 *
	 * @param value
	 * @return
	 */
	public static final long roundHalfUp( double value) {
		return ( long) ( ( value < 0.0d) ? Math.ceil( value - 0.5d) : Math.floor( value + 0.5d));
	}

	public static Date dayBefore( Date day) {
		Date	before = new Date( day.getTime() - 1000 * 60 * 60 * 24);
		return before;
	}

	public static Date dayAfter( Date day) {
		Date	after = new Date( day.getTime() + 1000 * 60 * 60 * 24);
		return after;
	}

}// class Utils
