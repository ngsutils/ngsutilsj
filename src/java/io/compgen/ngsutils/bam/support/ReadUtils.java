package io.compgen.ngsutils.bam.support;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecord.SAMTagAndValue;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.Strand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class ReadUtils {
    public static final int READ_PAIRED_FLAG = 0x1;
    public static final int PROPER_PAIR_FLAG = 0x2;
    public static final int READ_UNMAPPED_FLAG = 0x4;
    public static final int MATE_UNMAPPED_FLAG = 0x8;
    public static final int READ_STRAND_FLAG = 0x10;
    public static final int MATE_STRAND_FLAG = 0x20;
    public static final int FIRST_OF_PAIR_FLAG = 0x40;
    public static final int SECOND_OF_PAIR_FLAG = 0x80;
    public static final int NOT_PRIMARY_ALIGNMENT_FLAG = 0x100;
    public static final int READ_FAILS_VENDOR_QUALITY_CHECK_FLAG = 0x200;
    public static final int DUPLICATE_READ_FLAG = 0x400;
    public static final int SUPPLEMENTARY_ALIGNMENT_FLAG = 0x800;

    
    public static class MappedReadCounter {
        private Set<String> readsR1=new HashSet<String>();
        private Set<String> readsR2=null;
        
        private boolean splitReads = false;
        private String tagName = null;
        
        private int tagAccR1=0;
        private int tagAccR2=0;
        private int tagCountR1=0;
        private int tagCountR2=0;
        
        public MappedReadCounter() {
        }

        public MappedReadCounter(boolean splitReads) {
            if (splitReads) {
                readsR2=new HashSet<String>();
                this.splitReads = splitReads;
            }
        }
        public MappedReadCounter(String tagName) {
            this.tagName = tagName.toUpperCase();
        }

        public MappedReadCounter(String tagName, boolean splitReads) {
            if (splitReads) {
                readsR2=new HashSet<String>();
                this.splitReads = splitReads;
            }
            this.tagName = tagName.toUpperCase();
        }

        public void addRead(SAMRecord read) {
            if (!splitReads || read.getFirstOfPairFlag() || !read.getReadPairedFlag()) {
                readsR1.add(read.getReadName());
                if (tagName != null) {
                    tagAccR1 += getTagValue(read);
                    tagCountR1 += 1;
                }
            } else if (splitReads && !read.getFirstOfPairFlag() && read.getReadPairedFlag()) {
                readsR2.add(read.getReadName());
                if (tagName != null) {
                    tagAccR2 += getTagValue(read);
                    tagCountR2 += 1;
                }
            }
        }
        
        public int getTagValue(SAMRecord read) {
        	// Can't use the samtools version of this because it is case-sensitive
        	Integer val = null;
        	for (SAMTagAndValue tagval: read.getAttributes()) {
        		if (tagval.tag.toUpperCase().equals(tagName)) {
    			    val = coerceTagValueInt(tagval.tag, tagval.value);
    			    if (val != null) {
    			    	return val;
    			    } else {
    			    	return -1;
    			    }
        		}
        	}
        	return -1;
        }

        public int getCountR1() {
            return readsR1.size();
        }
        
        public int getCountR2() {
            return readsR2.size();
        }
        
        public double getTagMeanR1() {
            if (tagCountR1 > 0) {
                return (double) tagAccR1 / tagCountR1;
            }
            return 0;
        }
        
        public double getTagMeanR2() {
            if (tagCountR2 > 0) {
                return (double) tagAccR2 / tagCountR2;
            }
            return 0;
        }        
    }
    
    
    /**
     * Calculates the effective orientation for a given fragment. This is useful for strand-specific operations
     * where you want to filter out reads that aren't in the correct orientation.
     * 
     * @param read
     * @param orient - enum: RF, FR, or unstranded
     * @return enum Strand: PLUS, MINUS (null for unmapped)
     */
    public static Strand getFragmentEffectiveStrand(SAMRecord read, Orientation orient) {
        if (read.getReadUnmappedFlag()) {
            return null;
        }
                
        if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
            // unpaired or first read in a pair
            if (orient == null || orient == Orientation.FR || orient == Orientation.UNSTRANDED) {
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            }
        } else {
            // paired end and second read...
            if (orient == null || orient == Orientation.FR || orient == Orientation.UNSTRANDED) {
                // this assumes read1 and read2 are sequenced in opposite
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.PLUS;
                } else {
                    return Strand.MINUS;
                }
            } else { // RF
                if (read.getReadNegativeStrandFlag()) {
                    return Strand.MINUS;
                } else {
                    return Strand.PLUS;
                }
            }
        }
    }
    
    /**
     * For a given read, calculate the splice-junctions it crosses. A read may cross more than one junction.
     * This is determined by looking for an 'N' operation in the alignment string. If a minimum overlap is 
     * specified, then the read must have more than minOverlap bases on each side of the junction. If there is more
     * than one junction, then only the first and last flanking sequences need to be longer than minOverlap.
     * 
     * If there is no junction, then this function will return the entire read region.
     * 
     * @param read 
     * @param orient - the orientation for the sequencing library (FR, RF, etc).
     * @param minOverlap - the minimum amount of flanking sequence there needs to be (default: 4bp).
     * @return
     */
    public static List<GenomeSpan> getFlankingRegions(SAMRecord read, Orientation orient, int minOverlap) {
        List<GenomeSpan> out = new ArrayList<GenomeSpan>();
        Strand strand = getFragmentEffectiveStrand(read, orient);
        
        int refpos = read.getAlignmentStart() - 1; // alignment-start is 1-based
//        int readpos = 0;
        
        int flankStart = refpos;
        
        for (CigarElement el: read.getCigar().getCigarElements()) {
            switch (el.getOperator()) {
            case M:
            case EQ:
            case X:
                refpos += el.getLength();
//                readpos += el.getLength();
                break;
            case I:
                refpos += el.getLength();
                break;
//            case D:
//            case S:
//                readpos += el.getLength();
//                break;
            case N:
          	    out.add(new GenomeSpan(read.getReferenceName(), flankStart, refpos, strand));
                refpos += el.getLength();
                flankStart = refpos;
                break;
            case H:
            default:
                break;
                
            }
        }
        
        out.add(new GenomeSpan(read.getReferenceName(), flankStart, refpos, strand));

        // check the first and last flanks... if they are too short, then don't return anything.
        if (out.size() > 1) {
            GenomeSpan first = out.get(0);
            GenomeSpan last = out.get(out.size()-1);
        	
            if (first.length() < minOverlap) {
                out.clear();
            } else if (last.length() < minOverlap) {
                out.clear();
            }
        }
        
        return out;
    }
    public static List<GenomeSpan> getJunctionFlankingRegions(SAMRecord read, Orientation orient) {
        return getFlankingRegions(read, orient, 4);
    }

    
    public static int getReadLength(SamReader reader) {
    	return getReadLength(reader, 10000);
    }
    public static int getReadLength(SamReader reader, int recordsToScan) {
    	int i = 0;
    	int size = 0;
        SAMRecordIterator it = reader.iterator();
        while (it.hasNext() && i < recordsToScan) {
        	SAMRecord read = it.next();
        	size = Math.max(size,  read.getReadLength());
        	i++;
        }
        it.close();
        return size;
    }

    public static List<SAMRecord> findOverlappingReads(SamReader reader, GenomeSpan pos, Orientation orient, int readLength, int minOverlap) {
        return findOverlappingReads(reader, pos, orient, readLength, minOverlap, false);
    }

    public static List<SAMRecord> findOverlappingReads(SamReader reader, GenomeSpan pos, Orientation orient, int readLength, int minOverlap, boolean allowGaps) {
    	List<SAMRecord> out = new ArrayList<SAMRecord>();

    	SAMRecordIterator it = reader.query(pos.ref, pos.start - readLength + minOverlap, pos.start + readLength - minOverlap, true);
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
            
            if (!allowGaps) {
                // skip all reads with gaps
                for (CigarElement el: read.getCigar().getCigarElements()) {
                    if (el.getOperator() == CigarOperator.N) {
                        continue;
                    }
                }
            }
            
            if (ReadUtils.getFragmentEffectiveStrand(read, orient) != pos.strand) {
            	continue;
            }
            
            for (GenomeSpan region: ReadUtils.getFlankingRegions(read, orient, minOverlap)) {
            	if (region.start <= (pos.start - minOverlap) && region.end >= (pos.start + minOverlap)) {
            		out.add(read);
            		break;
            	}
            }
        }
        it.close();
    	return out;
    }

    public static SortedMap<GenomeSpan, MappedReadCounter> findJunctions(SamReader reader, String ref, int start, int end, Orientation orient) {
        return findJunctions(reader, ref, start, end, orient, 4, null, false);
    }
    public static SortedMap<GenomeSpan, MappedReadCounter> findJunctions(SamReader reader, String ref, int start, int end, Orientation orient, int minOverlap, String tallyTagName, boolean splitReads) {
        SAMRecordIterator it = reader.query(ref, start, end, true);
        SortedMap<GenomeSpan, MappedReadCounter> counters = new TreeMap<GenomeSpan, MappedReadCounter>();
        
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag() || read.getSupplementaryAlignmentFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
           
            if (read.getAlignmentBlocks().size() > 1) {
                int last_end = -1;
                for (GenomeSpan flank: ReadUtils.getFlankingRegions(read, orient, minOverlap)) {
                    if (last_end != -1) {
                        GenomeSpan junction = new GenomeSpan(read.getReferenceName(), last_end, flank.start, flank.strand);
                        
                        if (!counters.containsKey(junction)) {
                            counters.put(junction, new MappedReadCounter(tallyTagName, splitReads));
                        }
                        
                        counters.get(junction).addRead(read);
                    }
                    last_end = flank.end;
                }
            }
        }
        it.close();
        return counters;
    }
    
    private static Integer coerceTagValueInt(String tag, Object val) {
    	// from HTSJDK: https://github.com/samtools/htsjdk/blob/master/src/java/htsjdk/samtools/SAMRecord.java
    	
        if (val == null) return null;
        if (val instanceof Integer) {
            return (Integer)val;
        }
        if (!(val instanceof Number)) {
            throw new RuntimeException("Value for tag " + tag + " is not Number: " + val.getClass());
        }
        final long longVal = ((Number)val).longValue();
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
            throw new RuntimeException("Value for tag " + tag + " is not in Integer range: " + longVal);
        }
        return (int)longVal;

    }
    
    public static String revcomp(String seq) {
        String out = "";
        for (int i=seq.length(); i > 0; i--) {
            char c = seq.charAt(i-1);
            switch (c) {
            case 'A':
                out += 'T';
                break;
            case 'T':
                out += 'A';
                break;
            case 'C':
                out += 'G';
                break;
            case 'G':
                out += 'C';
                break;
            case 'N':
                out += 'N';
                break;
            case 'a':
                out += 't';
                break;
            case 't':
                out += 'a';
                break;
            case 'c':
                out += 'g';
                break;
            case 'g':
                out += 'c';
                break;
            case 'n':
                out += 'n';
                break;
            }   
        }
        return out;
    }
}