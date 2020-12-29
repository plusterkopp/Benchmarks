package misc;

import java.util.*;
import java.util.concurrent.*;

/**
 * java version "1.7.0_03"
 * OpenJDK Runtime Environment (IcedTea7 2.1.1pre) (7~u3-2.1.1~pre1-1ubuntu2)
 * OpenJDK 64-Bit Server VM (build 22.0-b10, mixed mode)
 *
 * javac InternBench.java
 * java -Xmx1g -Xms1g -XX:+PrintGCDetails -XX:NewRatio=1 -XX:+PrintCompilation -verbose:gc -classpath . InternBench
 *
 * Results:
 *
 * intern() many different strings:
 * ================================
 * count       initial intern   lookup same string  lookup equal string
 * 1'000'000            40206                34698                35000
 *   400'000             5198                 4481                 4477
 *   200'000              955                  828                  803
 *   100'000              234                  215                  220
 *    80'000              110                   94                   99
 *    40'000               52                   30                   32
 *    20'000               20                   10                   13
 *    10'000                7                    5                    7
 *
 * Manual "interning" with ConcurrentHashMap:
 * ==========================================
 * 1'000'000              411                  246                  309
 *   800'000              352                  194                  229
 *   400'000              162                   95                  114
 *   200'000               78                   50                   55
 *   100'000               41                   28                   28
 *    80'000               31                   23                   22
 *    40'000               20                   14                   16
 *    20'000               12                    6                    7
 *    10'000                9                    5                    3
 *
 */
public class StringInternBench {

	private static long timerStart = 0;
	private static long timerEnd = 0;

	public static void main(String[] args){

		startTimer();
		Set<String> candidates = new HashSet<String>();
		Random rand = new Random();
		for(int i = 0; i < 1_000_000; i ++){
			String candidate = Long.toString(rand.nextLong(),36);
			candidates.add(candidate);
		}
		endTimer("Generated candidate strings in %d ms");

		putAll(candidates);
	}

	private static void internAll(Set<String> candidates){
		startTimer();
		for(String s : candidates){
			s.intern();
		}
		endTimer("Initial interning done in %d ms");

		startTimer();
		for(String s : candidates){
			s.intern();
		}
		endTimer("Identity lookup done in %d ms");

		System.out.println("meuh");
		Set<String> copies = new HashSet<String>();
		for(String s : candidates){
			copies.add(new String(s));
		}
		System.out.println("foo");

		startTimer();
		for(String s : copies){
			s.intern();
		}
		endTimer("Equals lookup done in %d ms");

	}

	private static void putAll(Set<String> candidates){

		ConcurrentHashMap<String,String> pool = new ConcurrentHashMap<String,String>();

		// warmup the methods of ConcurrentHashMap and the other java.util.concurrent stuff
		startTimer();
		for(int i = 0; i < 10000; i ++){
			String s = Integer.toString(i);
			pool.putIfAbsent(s,s);
			pool.putIfAbsent(s,s);
		}
		for(int i = 0; i < 10000; i ++){
			String s = Integer.toString(i);
			pool.putIfAbsent(s,s);
		}
		endTimer("JIT warmup done in %d ms");

		pool = new ConcurrentHashMap<String,String>();

		startTimer();
		for(String s : candidates){
			pool.putIfAbsent(s,s);
		}
		endTimer("Initial put done in %d ms");

		startTimer();
		for(String s : candidates){
			pool.putIfAbsent(s,s);
		}
		endTimer("Identity lookup done in %d ms");

		System.out.println("meuh");
		Set<String> copies = new HashSet<String>();
		for(String s : candidates){
			copies.add(new String(s));
		}
		System.out.println("foo");

		startTimer();
		for(String s : copies){
			pool.putIfAbsent(s,s);
		}
		endTimer("Equals lookup done in %d ms");

	}

	private static void startTimer(){
		timerStart = System.nanoTime();
	}

	private static void endTimer(String messagePattern){
		timerEnd = System.nanoTime();
		long msElapsed = (timerEnd - timerStart)/1000000;
		System.out.println(String.format(messagePattern, msElapsed));
	}
}