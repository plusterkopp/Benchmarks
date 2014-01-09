/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.base;


/**
 * <p>@author ralf
 *
 */
public abstract class AbstractBenchRunnable implements IBenchRunnable {

	private String name;

	public AbstractBenchRunnable( String string) {
		name = string;
		setup();
	}

	@Override
	public void setup() {
		reset();
	}

	@Override
	public void reset() {
	}

	@Override
	public void run( long nruns) {
		for ( long i = nruns;  i > 0;  i--) {
			run();
		}
	}

	/* (non-Javadoc)
	 * @see de.pkmd.utils.bench.IBenchRunnable#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see de.icubic.mm.bench.base.IBenchRunnable#setName(java.lang.String)
	 */
	@Override
	public void setName( String string) {
		name = string;
	}

	@Override
	public long getTotalRunSize( long nruns) {
		return getRunSize() * nruns;
	}

	public String getCSVHeader() {
		return null;
	}

	public String getCSVLine() {
		return null;
	}

	protected String toCSV( Object... values) {
		if ( values != null && values.length > 0) {
			StringBuilder	sb = new StringBuilder();
			for ( Object object : values) {
				sb.append( object);
				sb.append( "\t");
			}
			return sb.toString();
		}
		return null;
	}
}

