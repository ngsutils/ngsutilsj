package io.compgen.ngsutils.cli.bam;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TabWriter;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Iterator;

@Command(name="bam-tobed", desc="Writes read positions to a BED6 file", category="bam", experimental=true, 
doc="The mapped position of a read is writen to a 6 column BED file.\n" 
  + "If the file is paired end, then only the first read of the pair\n"
  + "will be written to the file.")

public class BamToBed extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private boolean includeUnmapped = false;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Also output unmapped reads", name="unmapped")
    public void setUnmapped(boolean includeUnmapped) {
        this.includeUnmapped = includeUnmapped;
    }

    @Option(desc = "Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc = "Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## include-unmapped: " + includeUnmapped);


        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), new ProgressMessage<SAMRecord>() {
            long i = 0;
            @Override
            public String msg(SAMRecord current) {
                i++;
                return i+" "+current.getReadName();
            }}, new CloseableFinalizer<SAMRecord>());
        long i = 0;
        while (it.hasNext()) {
            SAMRecord read = it.next();
            if (read.getReadUnmappedFlag() && !includeUnmapped) {
                continue;
            }
            
            if (read.isSecondaryOrSupplementary() || (read.getReadPairedFlag() && !read.getFirstOfPairFlag())) {
                continue;
            }
            
            writer.write(read.getReferenceName());
            writer.write(read.getAlignmentStart());
            writer.write(read.getAlignmentEnd());
            writer.write(read.getReadName());
            writer.write(read.getMappingQuality());
            writer.write(read.getReadNegativeStrandFlag() ? "-" : "+");
            writer.eol();
            
        }
        writer.close();
        reader.close();
        System.err.println("Successfully read: "+i+" records.");
    }
}