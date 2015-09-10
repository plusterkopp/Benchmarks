package de.icubic.mm.bench;

import java.text.*;
import java.util.*;

public class Benchmarks {

	public static void main( String[] args) {
		String	folderName = getDateTimeFolderName();

	}

	private static String getDateTimeFolderName() {
		Date now = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return df.format( now);
	}


}
