package org.ngsutils.bam.filter;

import htsjdk.samtools.SAMRecord;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.ngsutils.bam.Orientation;

public class BedInclude extends BedExclude {
    public BedInclude(BamFilter parent, boolean verbose, String filename, Orientation orient) throws FileNotFoundException, IOException {
        super(parent, verbose, filename, orient);
    }
    @Override
    public boolean keepRead(SAMRecord read) {
        return !super.keepRead(read);
    }
}
