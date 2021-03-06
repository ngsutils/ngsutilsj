package io.compgen.ngsutils.fastq.filter;

import io.compgen.ngsutils.fastq.FastqRead;

public class PrefixTrimFilter extends AbstractSingleReadFilter {
	private int removeSize;
	public PrefixTrimFilter(Iterable<FastqRead> parent, boolean verbose, int removeSize) throws FilteringException {
		super(parent, verbose);
		if (removeSize < 0) {
			throw new FilteringException("Number of bases to remove must be greated than zero!");
		}
		this.removeSize = removeSize;
        if (verbose) {
            System.err.println("["+this.getClass().getSimpleName()+"] Removing: " + removeSize + "bases from 5' end of reads");
        }
		
	}

	@Override
	protected FastqRead filterRead(FastqRead read) throws FilteringException {
		String name = read.getName();
		String seq = read.getSeq();
		String qual = read.getQual();
		String comment = read.getComment();
		
		if (seq.length() != qual.length()) {
			throw new FilteringException("You cannot use the PrefixTrimFilter with color-space files!");
		}
		
		seq = seq.substring(removeSize);
		qual = qual.substring(removeSize);
		if (qual.length() > 0) {
			if (comment == null) {
				comment = "#prefix";
			} else {
				comment = comment + " #prefix";
			}
			
			return new FastqRead(name, seq, qual, comment);
		}
		
		return null;
	}

}
