package de.icubic.mm.bench.benches;

import java.text.NumberFormat;
import java.util.*;

import de.icubic.mm.bench.base.*;

public class ListGCBench {

	// Node in the linked list
	public final static class Node {
		Node prev;
		Node next;
		double value;
	}

	// Random number generator
	private final Random random = new Random( 12);

	// Dummy node for the head
	private final Node head = new Node();

	// Split the list at the given node, and sort the right-hand side
	private static Node splitAndSort( Node node, boolean ascending) {
		// Split the list at the given node
		if ( node.prev != null)
			node.prev.next = null;
		node.prev = null;

		// Ensure we have at LEAST two elements
		if ( node.next == null)
			return node;

		// Find the midpoint to split the list
		Node mid = node.next;
		Node end = node.next;
		do {
			end = end.next;
			if ( end != null) {
				end = end.next;
				mid = mid.next;
			}
		} while ( end != null);

		// Sort the two sides
		Node list2 = splitAndSort( mid, ascending);
		Node list1 = splitAndSort( node, ascending);

		// Merge the two lists (setting prev only)
		node = null;
		while ( true) {

			if ( list1 == null) {
				list2.prev = node;
				node = list2;
				break;
			} else if ( list2 == null) {
				list1.prev = node;
				node = list1;
				break;
			} else if ( ascending == ( list1.value < list2.value)) {
				list2.prev = node;
				node = list2;
				list2 = list2.next;
			} else {
				list1.prev = node;
				node = list1;
				list1 = list1.next;
			}
		}

		// Fix all the nexts (based on the prevs)
		while ( node.prev != null) {
			node.prev.next = node;
			node = node.prev;
		}

		return node;
	}

	// Sort the nodes in ascending order
	public void sortNodes() {
		if ( head.next != null) {
			head.next = splitAndSort( head.next, true);
			head.next.prev = head;
		}
	}

	// Prepend a number of nodes with random values
	public void prependNodes( int count) {
		for ( int i = 0; i < count; i++) {
			Node node = new Node();
			if ( head.next != null) {
				node.next = head.next;
				head.next.prev = node;
			}
			node.value = random.nextDouble();
			node.prev = head;
			head.next = node;
		}
	}

	public static void main( String[] args) {
		NumberFormat nf = BenchLogger.LNF;
		nf.setGroupingUsed( true);
		ListGCBench list = new ListGCBench();
		int count = 0;
		long start = System.currentTimeMillis();
		while ( true) {
			// Append a million random entries
			final int increment = 1000000;
			list.prependNodes(increment);
			count += increment;

			// Sort the entire list
			list.sortNodes();

			// Print the time taken for this pass
			long end = System.currentTimeMillis();
			BenchLogger.sysout( "Took " + nf.format( end - start) + " ms to prepend "
					+ nf.format( increment)
					+ " and then sort all " + nf.format( count));
			start = end;
		}
	}
}
