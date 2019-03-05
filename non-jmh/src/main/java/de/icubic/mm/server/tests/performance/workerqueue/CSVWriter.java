package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.*;

import com.lmax.disruptor.collections.*;

import de.icubic.mm.server.tests.performance.workerqueue.WorkerQueueFactory.*;

class CSVWriter {
	Map<String, String> valueMap = new LinkedHashMap<String, String>();
	String[]	keyOrder = {
			"Conf", "Hardware", "Takt (G)", "OS",
			"Threads", "Queues", "Jobs", "MOps", "Ops/Job",
			"Single",
	};
	List<String> knownKeys = Arrays.asList( keyOrder);
	Map<String, Histogram> histograms = new LinkedHashMap<String, Histogram>();

	public String put( String key, String value) {
		if ( ! isKnownKey( key)) {
			throw new IllegalArgumentException( "Unkown key: " + key);
		}
		return valueMap.put( key, value);
	}

	private boolean isKnownKey( String key) {
		if ( knownKeys.contains( key))
			return true;
		for ( EWorkQueueType type: EWorkQueueType.values()) {
			if ( type.toString().equals( key))
				return true;
		}
		return false;
	}

	void putHistogram( String name, Histogram histo) {
		histograms.put( name, histo);
	}

	Histogram addHistogram( String name, Task[] tasks) {
		long	maxLatency = 0;
		for ( int i = 0;  i < tasks.length;  i++) {
			Task	task = tasks[ i];
			long latency = task.getLatency();
			maxLatency = Math.max( maxLatency, latency);
		}
		List<Long> upperBounds = new ArrayList<Long>();
		long	bound = 1;
		while ( bound < maxLatency) {
			upperBounds.add( bound);
			bound *= 2;
		}
		upperBounds.add( bound);
		long[]	ub = new long[ upperBounds.size()];
		for ( int i = 0; i < ub.length; i++) {
			ub[ i] = upperBounds.get( i);
		}
		Histogram	histo = new Histogram( ub);
		for ( Task task : tasks) {
			histo.addObservation( task.getLatency());
		}
		putHistogram( name, histo);
		return histo;
	}

	public String asString() {
		List<String>	head = new ArrayList<String>();
		List<String>	values = new ArrayList<String>();
		Map<String, String> v = new LinkedHashMap<String, String>( valueMap);
		for ( String key : keyOrder) {
			String value = v.remove( key);
			head.add( key);
			values.add( value);
		}
		for ( String key : v.keySet()) {
			String value = v.get( key);
			head.add( key);
			values.add( value != null ? value : "");
		}
		StringBuilder sb = new StringBuilder();
		String sep = "\t";
		for ( String string : head) {
			final String str = string.replace( '_', ' ');
			sb.append( str);
			sb.append( sep);
		}
		sb.append( "\n");
		for ( String string : values) {
			sb.append( string);
			sb.append( sep);
		}
		return sb.toString();
	}
}