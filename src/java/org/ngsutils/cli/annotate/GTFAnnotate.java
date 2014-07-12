package org.ngsutils.cli.annotate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ngsutils.NGSUtils;
import org.ngsutils.NGSUtilsException;
import org.ngsutils.annotation.GTFAnnotationSource;
import org.ngsutils.annotation.GTFAnnotationSource.GTFGene;
import org.ngsutils.annotation.GenomeRegion;
import org.ngsutils.bam.Strand;
import org.ngsutils.cli.AbstractOutputCommand;
import org.ngsutils.cli.Command;
import org.ngsutils.support.StringLineReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.TabWriter;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="ngsutilsj annotate-gtf")
@Command(name="annotate-gtf", desc="Finds gene annotations from a GTF model", doc="Note: Column indexes start at 1.", cat="annotation")
public class GTFAnnotate extends AbstractOutputCommand {
    
    private String filename=null;
    private String gtfFilename=null;
    
    private int refCol = -1;
    private int startCol = -1;
    private int endCol = -1;
    private int strandCol = -1;
    private int regionCol = -1;
    private int junctionCol = -1;
    
    private int within = 0;
    
    private boolean hasHeader = true;
    private boolean headerComment = false;
    
    private boolean zeroBased = true;
    
    private List<String> outputs = new ArrayList<String>();
    
    @Unparsed(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "GTF  filename", longName="gtf", defaultToNull=true)
    public void setGTFFilename(String gtfFilename) {
        this.gtfFilename = gtfFilename;
    }


    @Option(description = "Column of chromosome (Default: 1)", longName="col-chrom", defaultValue="-1")
    public void setChromCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.refCol = val - 1;
        } else { 
            this.refCol = -1;
        }
    }

    @Option(description = "Column of start-position (1-based position) (Default: 2)", longName="col-start", defaultValue="-1")
    public void setStartCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.startCol = val - 1;
        } else { 
            this.startCol = -1;
        }
    }

    @Option(description = "Column of end-position (Default: -1, no end col)", longName="col-end", defaultValue="-1")
    public void setEndCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.endCol = val - 1;
        } else { 
            this.endCol = -1;
        }
    }

    @Option(description = "Column of strand (Default: -1, not strand-specific)", longName="col-strand", defaultValue="-1")
    public void setStrandCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.strandCol = val - 1;
        } else { 
            this.strandCol = -1;
        }
    }

    @Option(description = "Column of a region (Default: -1, not used)", longName="col-region", defaultValue="-1")
    public void setRegionCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.regionCol = val - 1;
        } else { 
            this.regionCol = -1;
        }
    }

    @Option(description = "Column of a junction (Default: -1, not used)", longName="col-junction", defaultValue="-1")
    public void setJunctionCol(int val) {
        if (val > 0) {
            // stored as 0-based, given as 1-based
            this.junctionCol = val - 1;
        } else { 
            this.junctionCol = -1;
        }
    }

    @Option(description = "Use BED3 format presets", longName="bed3")
    public void setUseBED3(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = -1;
        }
    }

    @Option(description = "Use BED6 format presets", longName="bed6")
    public void setUseBED6(boolean val) {
        if (val) {
            this.refCol = 0;
            this.startCol = 1;
            this.endCol = 2;
            this.strandCol = 5;
        }
    }

    @Option(description = "Add gene_id annotation", longName="gene-id")
    public void setGeneId(boolean val) {
        outputs.add("gene_id");
    }

    @Option(description = "Add gene_name annotation", longName="gene-name")
    public void setGeneName(boolean val) {
        outputs.add("gene_name");
    }

    @Option(description = "Add biotype annotation", longName="biotype")
    public void setBioType(boolean val) {
        outputs.add("biotype");
    }


    @Option(description = "Input file uses one-based coordinates (default is 0-based)", longName="one")
    public void setOneBased(boolean val) {
        zeroBased = !val;
    }

    @Option(description = "Input file doesn't have a header row", longName="noheader")
    public void setHasHeader(boolean val) {
        hasHeader = !val;
    }

    @Option(description = "The header is the last commented line", longName="header-comment")
    public void setHeaderComment(boolean val) {
        headerComment = val;
    }


    @Option(description = "Repeat can be within [value] bp of the genomic range (requires start and end columns)", longName="within", defaultValue="0")
    public void setWithin(int val) {
        this.within = val;
    }

    @Override
    public void exec() throws NGSUtilsException, IOException {
        if (gtfFilename == null) {
            throw new NGSUtilsException("You must specify a GTF file!");
        }
        
        if (filename == null) {
            throw new NGSUtilsException("You must specify an input file! (- for stdin)");
        }
        
        if (refCol == -1 && startCol == -1 && regionCol == -1 && junctionCol == -1) {
            // set the defaults if nothing is specified
            refCol = 0;
            startCol = 1;
        }
        

        TabWriter writer = new TabWriter();
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## gtf-annotations: " + gtfFilename);
        
        if (verbose) {
            System.err.print("Reading GTF annotation file: "+gtfFilename);
        }

        GTFAnnotationSource ann = new GTFAnnotationSource(gtfFilename);
        if (verbose) {
            System.err.println(" [done]");
        }

        boolean first = true;
        String lastline = null;
        int colNum = -1;
        for (String line: new StringLineReader(filename)) {
            try {
                if (line.charAt(0) == '#') {
                    if (lastline != null) {
                        writer.write_line(lastline);
                    }
                    lastline = line;
                    continue;
                }
                
                if (lastline!=null) {
                    if (headerComment && hasHeader) {
                        String[] cols = lastline.split("\\t", -1);
                        colNum = cols.length;
                        writer.write(cols);
                        if (outputs.size()>0) {
                            for (String output: outputs) {
                                if (output.equals("biotype") && !ann.provides("biotype")) {
                                    continue;
                                }
                                writer.write(output);
                            }
                        } else {
                            writer.write("gene_id");
                            writer.write("gene_name");
                            if (ann.provides("biotype")) {
                                writer.write("biotype");                    
                            }
                        }
                        writer.eol();
                        first = false;
                    } else {
                        writer.write_line(lastline);
                    }
                    
                    lastline = null;
                }
                
                String[] cols = line.split("\\t", -1);
                writer.write(cols);
                if (hasHeader && first) {
                    first = false;
                    colNum = cols.length;
                    
                    if (outputs.size()>0) {
                        for (String output: outputs) {
                            if (output.equals("biotype") && !ann.provides("biotype")) {
                                continue;
                            }
                            writer.write(output);
                        }
                    } else {
                        writer.write("gene_id");
                        writer.write("gene_name");
                        if (ann.provides("biotype")) {
                            writer.write("biotype");                    
                        }
                    }
                    writer.eol();
                    continue;
                }
                
                for (int i=cols.length; i<colNum; i++) {
                    writer.write("");
                }
                
                List<GTFGene> annVals;
                if (regionCol > -1) {
                    String region = cols[regionCol];
                    annVals = ann.findAnnotation(GenomeRegion.parse(region));
                } else if (junctionCol > -1) {
                    String junction = cols[junctionCol];
                    annVals = ann.findJunction(junction);
                } else {
                    String ref = cols[refCol];
                    int start = Integer.parseInt(cols[startCol])-within;
                    int end = start+within;
                    Strand strand = Strand.NONE;
                    
                    if (!zeroBased && start > 0) {
                        start = start - 1;
                    }
                    
                    if (endCol>-1) { 
                        end = Integer.parseInt(cols[endCol])+within;
                    }
                    
                    if (strandCol>-1) {
                        strand = Strand.parse(cols[strandCol]);
                    }
                    annVals = ann.findAnnotation(new GenomeRegion(ref, start, end, strand));
                }

                String[] geneIds = new String[annVals.size()];
                String[] geneNames = new String[annVals.size()];
                String[] bioTypes = new String[annVals.size()];
               
                for (int i=0; i < annVals.size(); i++) {
                    GTFGene gene = annVals.get(i);
                    geneIds[i] = gene.getGeneId();
                    geneNames[i] = gene.getGeneName();
                    bioTypes[i] = gene.getBioType();
                }
                if (outputs.size()>0) {
                    for (String output: outputs) {
                        if (output.equals("biotype") && !ann.provides("biotype")) {
                            continue;
                        }
                        switch(output) {
                        case "gene_id":
                            writer.write(StringUtils.join(",", geneIds));
                            break;
                        case "gene_name":
                            writer.write(StringUtils.join(",", geneNames));
                            break;
                        case "biotype":
                            writer.write(StringUtils.join(",", bioTypes));
                            break;
                        }
                    }
                } else {
                    writer.write(StringUtils.join(",", geneIds));
                    writer.write(StringUtils.join(",", geneNames));
                    if (ann.provides("biotype")) {
                        writer.write(StringUtils.join(",", bioTypes));
                    }
                }
    
                writer.eol();
            } catch (Exception ex) {
                System.err.println("ERROR processing line: "+line);
                System.err.println(ex);
                ex.printStackTrace(System.err);
                throw(ex);
            }
        }

        writer.close();
    }
}
