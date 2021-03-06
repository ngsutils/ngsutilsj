package io.compgen.ngsutils.cli.fastq;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name = "fastq-tofasta", desc = "Convert FASTQ sequences to FASTA format", category="fastq")
public class FastqToFasta extends AbstractCommand {
	private String[] filenames;

	public FastqToFasta() {
	}

	@UnnamedArg(name = "FILE")
	public void setFilename(String[] filenames) throws IOException {
	    this.filenames = filenames;
	}
	
	@Exec
    public void exec() throws IOException {
	    long count=0;

	    for (String filename: filenames) {
            File file1 = new File(filename);
            FileInputStream fis1 = new FileInputStream(file1);
            FileChannel channel1 = fis1.getChannel();
        
            FastqReader reader = Fastq.open(fis1, null, channel1, filename);
            for (FastqRead read : reader) {
                count++;
                if (read.getComment()!=null) {
                    System.out.println(">"+read.getName()+" " +read.getComment());
                } else {
                    System.out.println(">"+read.getName());
                }
                System.out.println(read.getSeq());
            }
    
    	    System.err.println(count+" reads found.");
	    }
	}
}
