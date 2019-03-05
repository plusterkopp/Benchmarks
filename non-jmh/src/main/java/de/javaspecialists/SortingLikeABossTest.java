package de.javaspecialists;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static org.junit.Assert.assertEquals;

public class SortingLikeABossTest {
    @Test
    public void testBossSorting() {
        List<Double> jumble =
                ThreadLocalRandom.current()
                        .doubles(1_000_000)
                        .parallel()
                        .boxed()
                        .collect(Collectors.toList());

        List<Double> sorted = new ArrayList<>(jumble);
        Collections.sort(sorted);

        ArrayList<Double> al = SortingLikeABoss.parallelSort(
                jumble, ArrayList::new
        );
        assertEquals(sorted, al);

        LinkedList<Double> ll = SortingLikeABoss.parallelSort(
                jumble, LinkedList::new
        );
        assertEquals(sorted, ll);

        CopyOnWriteArrayList<Double> cowal =
                SortingLikeABoss.parallelSort(
                        jumble, CopyOnWriteArrayList::new
                );
        assertEquals(sorted, cowal);
    }
}