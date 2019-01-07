package shipilev;

import java.util.*;

public class CrappyGC {
    static List<Object> l;

    public static void main(String... args) {
        l = new ArrayList<>();
        for (int c = 0; c < 100_000_000; c++) {
            l.add(new Object());
        }
    }
}
