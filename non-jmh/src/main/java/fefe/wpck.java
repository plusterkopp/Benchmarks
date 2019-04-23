package fefe;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;


public class wpck {
    public static void main(final String[] args) throws Exception {
        final long start = System.currentTimeMillis();

        final Map<String, Integer> map = new ConcurrentHashMap<>(1048576);
        try (final FileInputStream fis = new FileInputStream(FileDescriptor.in)) {
            final FileChannel channel = fis.getChannel();
            CharsetDecoder decoder = Charset.forName("ISO-8859-1").newDecoder();
            Reader reader = Channels.newReader(channel, decoder, 1048576);
            final BufferedReader bufferedReader = new BufferedReader(reader, 262144);
            final Stream<String> s = bufferedReader.lines().parallel();

            s
                    .map(line -> new StringTokenizer(line, " "))
                    .forEach(st -> {
                        while (st.hasMoreTokens()) {
                            map.merge(st.nextToken(), 1, (oldVal, val) -> oldVal + 1);
                        }
                    });
        }

        final long finishMapping = System.currentTimeMillis();
        System.err.println("mapping time: " + ( finishMapping - start));

        final StringBuilder sb = new StringBuilder();
        map.entrySet().stream().parallel().sorted((a, b) -> {
            int valueCompare = b.getValue() - a.getValue();
            if ( valueCompare != 0) {
                return valueCompare;
            }
            return b.getKey().compareTo( a.getKey());
        }).sequential()
                .forEach(e -> sb.append(e.getValue()).append(' ').append(e.getKey()).append('\n'));

        final long finishSorting = System.currentTimeMillis();
        System.err.println("sorting time: " + ( finishSorting - finishMapping));

        System.out.println(sb.toString());

        final long finishPrinting = System.currentTimeMillis();
        System.err.println("printing time: " + ( finishPrinting - finishSorting));
        System.err.println("total time: " + ( finishPrinting - start));
    }
}
