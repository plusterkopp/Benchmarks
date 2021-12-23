package de.icubic.mm.server.utils;

import java.io.*;
import java.text.*;
import java.util.*;

public class Tabulator {

    static final NumberFormat nf = NumberFormat.getNumberInstance();

    public static void main( String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader( System.in));
        List<String>    lines = new ArrayList();
        while (true) {
            try {
                if (!br.ready()) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String line = null;
            try {
                line = br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            lines.add( line);
        }
        List<String> tabulated = tabulate(lines);
        tabulated.forEach( System.out::println);
    }

    private static List<String> tabulate(List<String> reports) {
        List<String>    result = new ArrayList<>( reports);
        List<String[]> splits = new ArrayList<>();
        for ( String s: reports) {
            String[] split = s.split("\\s");
            splits.add( split);
        }
        List<Integer> maxL = new ArrayList<>();
        for ( String[] split: splits) {
            for ( int i = 0;  i < split.length;  i++) {
                int l = split[ i].length();
                // ensure element at i
                while ( maxL.size() < i+1) {
                    maxL.add( 0);
                }
                if ( maxL.get( i) < l) {
                    maxL.set( i, l);
                }
            }
        }
        for ( int i = 0;  i < splits.size();  i++) {
            String[] split = splits.get( i);
            StringBuilder newSB = new StringBuilder();
            for ( int j = 0;  j < split.length;  j++) {
                int max = maxL.get( j);
                String s = split[j];
                int fillCount = max - s.length();
                if ( fillCount <= 0) {
                    newSB.append( s).append( ' ');
                    continue;
                }
                StringBuilder sb = new StringBuilder( s);
                for ( int f = 0;  f < fillCount;  f++) {
                    if ( isNumeric( s)) {
                        sb.insert( 0, ' ');
                    } else {
                        sb.append( ' ');
                    }
                }
                newSB.append( sb).append( ' ');
            }
            String newString = newSB.toString().replace( '_', ' ');
            result.set( i, newString);
        }
        return result;
    }

    private static boolean isNumeric( String s) {
        try {
            nf.parse( s);
            return true;
        } catch ( ParseException e) {
            return false;
        }
    }


}
