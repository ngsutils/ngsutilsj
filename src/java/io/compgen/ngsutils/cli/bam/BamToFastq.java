package io.compgen.ngsutils.cli.bam;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.ngsutils.bam.BamFastqReader;
import io.compgen.ngsutils.fastq.FastqRead;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * TODO: Add option for exporting arbitrary tag values as comments (e.g. RG:Z, BC:Z, etc...)
 * TODO: Add option for exporting by Read Group
 * @author mbreese
 *
 */

@Command(name="bam-tofastq", desc="Export the read sequences from a BAM file in FASTQ format", category="bam")
public class BamToFastq extends AbstractCommand {
    
    private String filename=null;
    private String outTemplate=null;

    private boolean split = false;
    private boolean compress = false;
    private boolean force = false;
    private boolean comments = false;
    
    private boolean onlyFirst = false;
    private boolean onlySecond = false;
    private boolean onlyMapped = false;
    private boolean onlyUnmapped = false;
    
    private boolean lenient = false;
    private boolean silent = false;

    @UnnamedArg(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Output filename template (default: stdout)", name="out")
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(desc="Force overwriting output", name="force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(desc="Compress output", name="gz")
    public void setCompress(boolean val) {
        this.compress = val;
    }

    @Option(desc="Split output into paired files (default: interleaved)", name="split")
    public void setSplit(boolean val) {
        this.split = val;
    }

    @Option(desc="Ouput only first reads (default: output both)", name="first")
    public void setFirst(boolean val) {
        this.onlyFirst = val;
    }

    @Option(desc="Ouput only second reads (default: output both)", name="second")
    public void setSecond(boolean val) {
        this.onlySecond = val;
    }

    @Option(desc="Export only mapped reads", name="mapped")
    public void setOnlyMapped(boolean val) {
        this.onlyMapped = val;
    }

    @Option(desc="Export only unmapped reads", name="unmapped")
    public void setOnlyUnmapped(boolean val) {
        this.onlyUnmapped = val;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }
   
    @Option(desc="Include comments tag from BAM file", name="comments")
    public void setComments(boolean val) {
        this.comments = val;
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM file!");
        }
        if (split && (outTemplate == null || outTemplate.equals("-"))) {
            throw new CommandArgumentException("You cannot have split output to stdout!");
        }

        if (onlyFirst && onlySecond) {
            throw new CommandArgumentException("You can not use --first and --second at the same time!");
        }

        if (onlyMapped && onlyUnmapped) {
            throw new CommandArgumentException("You can not use --mapped and --unmapped at the same time!");
        }

        if (split && (onlyFirst || onlySecond)) {
            throw new CommandArgumentException("You can not use --split and --first or --second at the same time!");
        }
        
        OutputStream[] outs;
        if (outTemplate==null || outTemplate.equals("-")) {
            outs = new OutputStream[] { new BufferedOutputStream(System.out) };
            if (verbose) {
                System.err.println("Output: stdout");
            }
        } else if (outTemplate != null && (onlyFirst || onlySecond)) {
            String outFilename;
            if (compress) {
                if (onlyFirst) { 
                    outFilename = outTemplate+"_R1.fastq.gz";
                } else {
                    outFilename = outTemplate+"_R2.fastq.gz";
                }
            } else {
                if (onlyFirst) { 
                    outFilename = outTemplate+"_R1.fastq";
                } else {
                    outFilename = outTemplate+"_R2.fastq";
                }
            }
            if (new File(outFilename).exists() && !force) {
                throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
            }
            if (verbose) {
                System.err.println("Output: " + outFilename);
            }
            if (compress) {
                outs = new OutputStream[] { new GZIPOutputStream(new FileOutputStream(outFilename)) };
            } else {
                outs = new OutputStream[] { new BufferedOutputStream(new FileOutputStream(outFilename)) };
            }
        } else if (outTemplate != null && split) {
            String[] outFilenames;
            if (compress) {
                outFilenames = new String[] { outTemplate+"_R1.fastq.gz", outTemplate+"_R2.fastq.gz" };
            } else {
                outFilenames = new String[] {outTemplate+"_R1.fastq", outTemplate+"_R2.fastq"};
            }
            for (String outFilename: outFilenames) {
                if (new File(outFilename).exists() && !force) {
                    throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
                }
                if (verbose) {
                    System.err.println("Output: " + outFilename);
                }
            }

            outs = new OutputStream[2];
            if (compress) {
                outs[0] = new GZIPOutputStream(new FileOutputStream(outFilenames[0]));
                outs[1] = new GZIPOutputStream(new FileOutputStream(outFilenames[1]));                
            } else {
                outs[0] = new BufferedOutputStream(new FileOutputStream(outFilenames[0]));
                outs[1] = new BufferedOutputStream(new FileOutputStream(outFilenames[1]));                
            }
        } else {
            String outFilename;
            if (compress) {
                outFilename = outTemplate+".fastq.gz";
            } else {
                outFilename = outTemplate+".fastq";
            }
            if (new File(outFilename).exists() && !force) {
                throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
            }
            if (verbose) {
                System.err.println("Output: " + outFilename);
            }
            if (compress) {
                outs = new OutputStream[] { new GZIPOutputStream(new FileOutputStream(outFilename)) };
            } else {
                outs = new OutputStream[] { new BufferedOutputStream(new FileOutputStream(outFilename)) };
            }
        }

        BamFastqReader bfq = new BamFastqReader(filename);
        bfq.setLenient(lenient);
        bfq.setSilent(silent);
        bfq.setFirst(!onlySecond);
        bfq.setSecond(!onlyFirst);
        bfq.setComments(comments);
        bfq.setIncludeUnmapped(!onlyMapped);
        bfq.setIncludeMapped(!onlyUnmapped);
        
        String lastName = null;
        long i=0;
        for (FastqRead read: bfq) {
            if (verbose) {
                i++;
                if (i % 100000 == 0) {
                    System.err.println("Read: " + i);
                }
            }
            
            if (split && read.getName().equals(lastName)) {
                read.write(outs[1]);
                lastName = null;
            } else {
                read.write(outs[0]);
                lastName = read.getName();
            }
        }
        bfq.close();
        
        for (OutputStream out: outs) {
            out.close();
        }
    }    
}
