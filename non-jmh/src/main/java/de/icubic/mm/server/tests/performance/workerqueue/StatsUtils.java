package de.icubic.mm.server.tests.performance.workerqueue;

import de.icubic.mm.bench.base.*;

import java.text.*;
import java.util.*;

class StatsUtils {

		static class LongIntPair {
		public LongIntPair( long m, int j) {
			l = m;
			i = j;
		}
		long	l;
		int	i;

		@Override
		public String toString() {
			return "<" + l + ", " + i + ">";
		}

	}

		List<Double>	xtileBounds;
		Map<Double, Long>		xtiles;
		private long min;
		private long max;
		private int length;
		private double avg;
		private double avg90;
		private double res;

		public void computeStatsFor( final Task[] tasks) {
			StringBuilder	sb = new StringBuilder();
			long[]	latencies = new long[ tasks.length];
			int	latencyIndex = 0;
			int	firstError = -1;
			int	lastError = -1;
			for ( int taskIndex = 0;  taskIndex < tasks.length;  taskIndex++) {
				Task	task = tasks[ taskIndex];
				if ( task.finishedBy != null) {
					latencies[ latencyIndex] = task.getLatency();
					if ( latencies[ latencyIndex] < 0) {
						if ( firstError == -1) {
							firstError = taskIndex;
						}
						lastError = taskIndex;
					} else {	// print error chain from first to last
						if ( firstError > -1) {
							printLatencyError( tasks, firstError, lastError, sb);
							firstError = -1;
							lastError = -1;
						}
					}
					latencyIndex++;
				}
			}
			if ( firstError > -1) {
				printLatencyError( tasks, firstError, lastError, sb);
			}
			long[]	validLatencies = new long[ latencyIndex];
			System.arraycopy( latencies, 0, validLatencies, 0, latencyIndex);
			computeStatsFor( validLatencies);
			if ( sb.length() > 0) {
				BenchLogger.syserr( sb.toString());
			}
		}

		private void printLatencyError( Task[] tasks, int firstError, int lastError, StringBuilder sb) {
			if ( firstError != lastError) {
			Task	first = tasks[ firstError];
			Task	last = tasks[ lastError];
			sb.append( "Task " + MainClass.lnf.format( firstError) + "-" + MainClass.lnf.format( lastError)
					+ ": enc " + MainClass.lnf.format( first.enqueuedAtNano) + "-" + MainClass.lnf.format( last.enqueuedAtNano)
					+ " but fin " + MainClass.lnf.format( first.finishedAtNano) + "-" + MainClass.lnf.format( last.finishedAtNano)
					+ " (lat " + MainClass.lnf.format( first.finishedAtNano - first.enqueuedAtNano) + "-" + MainClass.lnf.format( last.finishedAtNano - last.enqueuedAtNano) + "), ");
			} else {
				Task	first = tasks[ firstError];
				sb.append( "Task " + MainClass.lnf.format( firstError) // + "-" + nf.format( lastError)
						+ ": enc " + MainClass.lnf.format( first.enqueuedAtNano) // + "-" + nf.format( last.enqueuedAtNano)
						+ " but fin " + MainClass.lnf.format( first.finishedAtNano) // + "-" + nf.format( last.finishedAtNano)
						+ " (lat " + MainClass.lnf.format( first.finishedAtNano - first.enqueuedAtNano) // + "-" + nf.format( last.enqueuedAtNano - last.finishedAtNano)
						+ "), ");
			}
		}

		public void computeStatsFor( final long[] obs) {
			Arrays.sort( obs);
			min = obs[ 0];
			max = obs[ obs.length - 1];
			length = obs.length;
			res = computeResolution( obs);
			xtileBounds = new ArrayList<Double>();
			xtileBounds.add( 0.5);
			double	oneMinusXtileBound = 0.1;
			while ( 1.0 / obs.length < oneMinusXtileBound) {
				xtileBounds.add( 1.0 - oneMinusXtileBound);
				oneMinusXtileBound *= 0.1;
			}
			xtiles = new LinkedHashMap<Double, Long>();
			for ( double xtileBound : xtileBounds) {
				int	index = ( int) ( obs.length * xtileBound);
				xtiles.put( xtileBound, obs[ index]);
			}
		}

		private double computeResolution( long[] sortedObs) {
			// try to find the smallest difference (latency) greater than jitter
			int	jitter = 2;
			ArrayList<StatsUtils.LongIntPair> rleList = new ArrayList<StatsUtils.LongIntPair>();
			int	iFirstNonNeg = 0;
			while ( sortedObs[ iFirstNonNeg] < 0) {
				iFirstNonNeg++;
			}
			StatsUtils.LongIntPair	p = new StatsUtils.LongIntPair( sortedObs[ iFirstNonNeg], 1) {};
			for ( int i = iFirstNonNeg + 1; i < sortedObs.length; i++) {
				long l = sortedObs[ i];
				if ( p.l == l) {
					p.i++;
				} else {
					rleList.add( p);
					p = new StatsUtils.LongIntPair( l,  1);
				}
			}
			rleList.add( p);

			// Average
			double sum = 0;
			int	num = 0;
			for ( StatsUtils.LongIntPair longIntPair : rleList) {
				sum += ( double ) longIntPair.l * longIntPair.i;
				num += longIntPair.i;
			}
			avg = sum / num;
			double sum90 = 0;
			final int max90 = ( int) ( sortedObs.length * 0.90);
			for ( int i = iFirstNonNeg;  i < max90;  i++) {
				sum90 += sortedObs[ i];
			}
			avg90 = sum90 / ( max90 - iFirstNonNeg);
			// print latencies (by number of occurencies, first x %)
//			SortedSet<LongIntPair> sortedRleList = new TreeSet<MainClass.LongIntPair>( new Comparator<LongIntPair>() {
//				@Override
//				public int compare( LongIntPair o1, LongIntPair o2) {
//					if ( o1.i > o2.i) {
//						return -1;
//					}
//					if ( o1.i < o2.i) {
//						return 1;
//					}
//					if ( o1.l > o2.l) {
//						return 1;
//					}
//					if ( o1.l < o2.l) {
//						return -1;
//					}
//					return 0;
//				}
//			});
//			sortedRleList.addAll( rleList);
//			StringBuilder sb = new StringBuilder( "" + rleList.size() + " Latencies: ");
//			int	count = 0;
//			sum = 0;
//			for ( Iterator<LongIntPair> iterator = sortedRleList.iterator(); iterator.hasNext() && count < num * 0.99 && sb.length() < 10000;) {
//				LongIntPair longIntPair = iterator.next();
//				count += longIntPair.i;
//				sum += ( double) longIntPair.l * longIntPair.i;
//				sb.append( longIntPair + " (" + nf.format( sum / count) + ")"
//						+ ", ");
//			}
//			BenchLogger.sysout( sb.toString());
			for ( int i = 0; i < rleList.size(); i++) {
				p = rleList.get( i);
				if ( p.l > jitter) {
					StatsUtils.LongIntPair	q = p;
					int	j = i + 1;
					StatsUtils.LongIntPair	r = rleList.get( j);
					while ( r.l - q.l <= jitter) {
						q = r;
						r = rleList.get( ++j);
					}
					double res = 0;
					num = 0;
					for ( int k = i;  k < j;  k++) {
						StatsUtils.LongIntPair	s = rleList.get( k);
						res += s.l * s.i;
						num += s.i;
					}
					res /= num;
					return res;
				}
			}
			return jitter;
		}

		public String asString() {
			NumberFormat	nf = DecimalFormat.getNumberInstance();
			nf.setMaximumFractionDigits( Math.max( 0, xtiles.size() - 3));
			nf.setGroupingUsed( true);
			StringBuilder	sb = new StringBuilder();
			for ( double bound : xtiles.keySet()) {
				long	xtile = xtiles.get( bound);
				if ( bound == 0.5) {
					sb.append( "Mean: ");
				} else {
					sb.append( nf.format( 100*bound) + "%: ");
				}
				sb.append( nf.format( xtile));
				sb.append( ", ");
			}
			if ( sb.length() >= 2) {
				sb.setLength( sb.length() - 2);
			}
			sb.append( " Avg: " + nf.format( ( long) avg) + ", Avg90: " + nf.format( ( long) avg90)
					+ ", Min: " + nf.format( min) + ", Max: " + nf.format( max)
					+ ", " + nf.format( length) + " jobs, nano res = " + nf.format( res));
			return sb.toString();
		}
	}