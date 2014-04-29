package org.ngsutils.cli.bam.count;

import java.util.Iterator;

import net.sf.samtools.SAMSequenceDictionary;

import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;

public class BinSpans implements Iterable<Span> {
    final private SAMSequenceDictionary seqdict;
    final private int binsize;
    final private Orientation orient;
    
    public BinSpans(SAMSequenceDictionary seqdict, int binsize, Orientation orient) {
        this.seqdict = seqdict;
        this.binsize = binsize;
        this.orient = orient;
    }

    @Override
    public Iterator<Span> iterator() {
        
        return new Iterator<Span>() {
            int currentSeq = 0;
            int currentPos = 0;
            Span next = null;
            @Override
            public boolean hasNext() {
                if (currentSeq < seqdict.size()) {
                    return true;
                }                
                return false;
            }

            @Override
            public Span next() {
                if (next != null) {
                    Span tmp = next;
                    next = null;
                    return tmp;
                }
                int start = currentPos;
                int end = currentPos + binsize;
                String ref = seqdict.getSequence(currentSeq).getSequenceName();
                
                if (end > seqdict.getSequence(currentSeq).getSequenceLength()) {
                    end = seqdict.getSequence(currentSeq).getSequenceLength();
                    currentSeq++;
                    currentPos = 0;
                } else {
                    currentPos = end;
                }
                
                if (orient == Orientation.UNSTRANDED) {
                    return new Span(ref, start, end, Strand.NONE, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.NONE.toString()});
                } else {
                    next = new Span(ref, start, end, Strand.MINUS, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.MINUS.toString()});
                    return new Span(ref, start, end, Strand.PLUS, new String[] { ref, Integer.toString(start), Integer.toString(end), Strand.PLUS.toString()});
                }
            }

            @Override
            public void remove() {
            }
            
        };
    }
}