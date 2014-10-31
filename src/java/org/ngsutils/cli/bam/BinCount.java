package org.ngsutils.cli.bam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.bam.Orientation;
import org.ngsutils.bam.Strand;
import org.ngsutils.cli.bam.count.BinCounter;
import org.ngsutils.cli.bam.count.BinCounter.BinCounterExporter;
import org.ngsutils.support.TabWriter;
import org.ngsutils.support.cli.AbstractOutputCommand;
import org.ngsutils.support.cli.Command;
import org.ngsutils.support.progress.FileChannelStats;
import org.ngsutils.support.progress.ProgressMessage;
import org.ngsutils.support.progress.ProgressUtils;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj bam-bins")
@Command(name="bam-bins", desc="Quickly count the number of reads that fall into bins", cat="bam")
public class BinCount extends AbstractOutputCommand {
    
    private String samFilename=null;
    
    private int binSize = 0;
    
    private boolean lenient = false;
    private boolean silent = false;
    
    private boolean stranded = false;
    private boolean showAll = false;
    
    private Orientation orient = Orientation.UNSTRANDED;
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        samFilename = filename;
    }

    @Option(description = "Count bins of size [value] (default: 50bp)", longName="bins", defaultValue="50")
    public void setBinSize(int binSize) {
        this.binSize = binSize;
    }

    @Option(description = "Show counts for all bins, including zero count bins", longName="all")
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }
    
    @Option(description = "Count bins in a strand-specific manner", longName="stranded")
    public void setStranded(boolean stranded) {
        this.stranded = stranded;
    }
    
    @Option(description = "Use lenient validation strategy", longName="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(description = "Use silent validation strategy", longName="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Option(description = "Library is in FR orientation", longName="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(description = "Library is in RF orientation", longName="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(description = "Library is in unstranded orientation (default)", longName="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        File file = new File(samFilename);
        FileInputStream fis = new FileInputStream(file);
        SAMFileReader reader = new SAMFileReader(fis);
        if (lenient) {
            reader.setValidationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            reader.setValidationStringency(ValidationStringency.SILENT);
        }

        final TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## input: " + samFilename);
        writer.write_line("## library-orientation: " + orient.toString());

        
        writer.write_line("## binsize: " + binSize);
        writer.write_line("## stranded: " + (stranded? "true": "false"));
        writer.write_line("## counts: number of reads per bin (bins assigned based on 5' end of the read)");

        writer.write("chrom", "start", "end");
        if (stranded) {
            writer.write("strand");
        }
        writer.write("count");
        
        writer.eol();

        BinCounter counter = new BinCounter(orient, binSize, stranded, showAll, new BinCounterExporter() {
            @Override
            public void writeBin(String ref, int start, int end, Strand strand, int count) {
                writer.write(ref, ""+start, ""+end, strand.toString(), ""+count);
                try {
                    writer.eol();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }});
        
        Iterator<SAMRecord> it = ProgressUtils.getIterator(file.getName(), reader.iterator(), new FileChannelStats(fis.getChannel()), new ProgressMessage<SAMRecord>() {

            @Override
            public String msg(SAMRecord current) {
                return current.getReadName();
            }});
        
        while (it.hasNext()) {
            SAMRecord read = it.next();

            if (read.isSecondaryOrSupplementary() || read.getDuplicateReadFlag() || read.getNotPrimaryAlignmentFlag() || read.getReadUnmappedFlag()) {
                // skip all secondary / duplicate / unmapped reads
                continue;
            }
            
            counter.addRead(read);
        }
        
        counter.flush();
        writer.close();
        reader.close();
    }
}
