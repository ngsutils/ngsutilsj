package io.compgen.ngsutils.cli.bed;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.SetBuilder;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Strand;
import io.compgen.ngsutils.bed.BedReader;
import io.compgen.ngsutils.bed.BedRecord;

import java.io.IOException;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

@Command(name="bed-reduce", desc="Merge overlaping BED regions", category="bed")
public class BedReduce extends AbstractOutputCommand {
    
    public class MutableBedRecord {
        public final GenomeSpan effectiveCoord;
        public final BedRecord bedRecord;
        
        public MutableBedRecord(GenomeSpan effectiveCoord, BedRecord record) {
            this.effectiveCoord = effectiveCoord;
            this.bedRecord = record;
        }
    }
    
    private String filename = null;
    private int extend = 0;
    private boolean scoreIsCount = false;
    private boolean noStrand = false;
    
    @Option(name="extend", desc="Extend a regions N bases in both directions to find an overlap.")
    public void setExtend(int extend) {
        this.extend = extend;
    }
    
    @Option(charName="c", desc="Score should be the count of merged regions (default: the sum of scores)")
    public void setScoreIsCount(boolean scoreIsCount) {
        this.scoreIsCount = scoreIsCount;
    }

    @Option(name="ns", desc="Ignore strand when merging regions")
    public void setNoStrand(boolean noStrand) {
        this.noStrand = noStrand;
    }

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) throws CommandArgumentException {
        this.filename = filename;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("Missing/invalid arguments!");
        }

        TreeMap<GenomeSpan, BedRecord> records = new TreeMap<GenomeSpan, BedRecord>();
        
        Iterator<BedRecord> it = BedReader.readFile(filename);
        MutableBedRecord record = null;
        
        while (it.hasNext() || record != null) {
            if (record == null && it.hasNext()) {
                BedRecord rec = it.next();
                if (scoreIsCount) {
                    rec = new BedRecord(rec.getCoord(), rec.getName(), 1, rec.getExtras());
                }
                GenomeSpan coord = rec.getCoord();
                if (extend > 0) {
                    coord = coord.extend5(extend);
                    coord = coord.extend3(extend);
                }
                
                if (noStrand) {
                    coord = coord.clone(Strand.NONE);
                }
                record = new MutableBedRecord(coord, rec);
            }

            
            GenomeSpan floor = records.floorKey(record.effectiveCoord);
            SortedMap<GenomeSpan, BedRecord> tail;
            if (floor != null) {
                tail = records.tailMap(floor);
            } else {
                tail = records;
            }
            
            GenomeSpan mergeWith = null;
            for (GenomeSpan test: tail.keySet()) {
                if (record.effectiveCoord.overlaps(test)) {
                    mergeWith = test;
                    break;
                }
            }
            
            if (mergeWith == null) {
                records.put(record.effectiveCoord,  record.bedRecord);
                record = null;
            } else {
                GenomeSpan mergedCoord = record.effectiveCoord.combine(mergeWith);
                BedRecord rec = mergeRecords(record.bedRecord, records.get(mergeWith));
                records.remove(mergeWith);
                record = new MutableBedRecord(mergedCoord, rec);
            }
        }
        
        for (GenomeSpan coord: records.keySet()) {
            records.get(coord).write(out);
        }
    }
    
    protected BedRecord mergeRecords(BedRecord one, BedRecord two) {
        GenomeSpan coord = one.getCoord().combine(two.getCoord());
        String name = StringUtils.join("|", new SetBuilder<String>().add(one.getName()).add(two.getName()).build());
        double score = one.getScore() + two.getScore();

        
        String[] extras = null;
        
        if (one.getExtras()!=null || two.getExtras()!=null) {
            int max = 0;
            if (one.getExtras() != null) {
                max = one.getExtras().length;
            }
            if (two.getExtras()!=null) {
                max = Math.max(max,  two.getExtras().length);
            }
            
            extras = new String[max];
            for (int i=0; i<max; i++) {
                String s = "";
                if (one.getExtras() != null && one.getExtras().length<i) {
                    s = one.getExtras()[i];
                }
                if (two.getExtras() != null && two.getExtras().length<i) {
                    if (!s.equals("")) {
                        s += "|";
                    }
                    s += two.getExtras()[i];
                }
                extras[i] = s;
            }
        }
        
        return new BedRecord(coord, name, score, extras);
    }
}
