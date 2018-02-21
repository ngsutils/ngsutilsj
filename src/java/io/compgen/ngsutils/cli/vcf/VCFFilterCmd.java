package io.compgen.ngsutils.cli.vcf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.common.TallyValues;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.vcf.filter.Equals;
import io.compgen.ngsutils.vcf.filter.GreaterThan;
import io.compgen.ngsutils.vcf.filter.GreaterThanEqual;
import io.compgen.ngsutils.vcf.filter.LessThan;
import io.compgen.ngsutils.vcf.filter.LessThanEqual;
import io.compgen.ngsutils.vcf.filter.NotEquals;
import io.compgen.ngsutils.vcf.filter.VCFFilter;
import io.compgen.ngsutils.vcf.vcf.VCFHeader;
import io.compgen.ngsutils.vcf.vcf.VCFReader;
import io.compgen.ngsutils.vcf.vcf.VCFRecord;
import io.compgen.ngsutils.vcf.vcf.VCFWriter;


@Command(name="vcf-filter", desc="Filter a VCF file", category="vcf")
public class VCFFilterCmd extends AbstractOutputCommand {
	private String filename = "-";
	
	List<VCFFilter> filterChain = new ArrayList<VCFFilter>();
	
	private boolean onlyOutputPass = false;
	private String statsFilename=null;
	
    @Option(desc="Write filter stats to a file", name="stats")
    public void setStatsFilename(String statsFilename) {
    	this.statsFilename = statsFilename;
    }
    
    @Option(desc="Only output passing variants", name="passing")
    public void setOnlyOutputPass(boolean onlyOutputPass) {
    	this.onlyOutputPass = onlyOutputPass;
    }
    
    @Option(desc="Values for {KEY} is not equal to {VAL} (multiple allowed, String or number)", name="neq", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setNotEQ(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		if (spl.length==2) {
        	filterChain.add(new NotEquals(spl[0], spl[1], null, null));
		} else if (spl.length==3) {
        	filterChain.add(new NotEquals(spl[0], spl[1], spl[2], null));
		} else if (spl.length==4) {
        	filterChain.add(new NotEquals(spl[0], spl[1], spl[2], spl[3]));
		} else {
    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @Option(desc="Values for {KEY} is equal to {VAL} (multiple allowed, String or number)", name="eq", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setEQ(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		if (spl.length==2) {
        	filterChain.add(new Equals(spl[0], spl[1], null, null));
		} else if (spl.length==3) {
        	filterChain.add(new Equals(spl[0], spl[1], spl[2], null));
		} else if (spl.length==4) {
        	filterChain.add(new Equals(spl[0], spl[1], spl[2], spl[3]));
		} else {
    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @Option(desc="Values for {KEY} is less than {VAL} (multiple allowed)", name="lt", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setLT(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		try {
			if (spl.length==2) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThan(spl[0], thres, null, null));
			} else if (spl.length==3) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThan(spl[0], thres, spl[2], null));
			} else if (spl.length==4) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThan(spl[0], thres, spl[2], spl[3]));
			} else {
	    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
			}
		} catch (NumberFormatException e) {
    		throw new CommandArgumentException("2. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @Option(desc="Values for {KEY} is less than or equal to {VAL} (multiple allowed)", name="lte", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setLTE(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		try {
			if (spl.length==2) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThanEqual(spl[0], thres, null, null));
			} else if (spl.length==3) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThanEqual(spl[0], thres, spl[2], null));
			} else if (spl.length==4) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new LessThanEqual(spl[0], thres, spl[2], spl[3]));
			} else {
	    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
			}
		} catch (NumberFormatException e) {
    		throw new CommandArgumentException("2. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @Option(desc="Values for {KEY} is greater than {VAL} (multiple allowed)", name="gt", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setGT(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		try {
			if (spl.length==2) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThan(spl[0], thres, null, null));
			} else if (spl.length==3) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThan(spl[0], thres, spl[2], null));
			} else if (spl.length==4) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThan(spl[0], thres, spl[2], spl[3]));
			} else {
	    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
			}
		} catch (NumberFormatException e) {
    		throw new CommandArgumentException("2. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @Option(desc="Values for {KEY} is greater than or equal {VAL} (multiple allowed)", name="gte", helpValue="KEY:VAL:SAMPLEID:ALLELE", allowMultiple=true)
    public void setGTE(String val) throws CommandArgumentException {
    	String[] spl = val.split(":");
		try {
			if (spl.length==2) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThanEqual(spl[0], thres, null, null));
			} else if (spl.length==3) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThanEqual(spl[0], thres, spl[2], null));
			} else if (spl.length==4) {
	        	double thres = Double.parseDouble(spl[1]);
	        	filterChain.add(new GreaterThanEqual(spl[0], thres, spl[2], spl[3]));
			} else {
	    		throw new CommandArgumentException("1. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
			}
		} catch (NumberFormatException e) {
    		throw new CommandArgumentException("2. Malformed argument. Should be in form => KEY:VAL or KEY:VAL:SAMPLEID or KEY:VAL:SAMPLEID:ALLELE");
		}
    }
    
    @UnnamedArg(name = "input.vcf", required=true)
    public void setFilename(String filename) throws CommandArgumentException {
    	this.filename = filename;
    }

	@Exec
	public void exec() throws Exception {		
		VCFReader reader;
		if (filename.equals("-")) {
			reader = new VCFReader(System.in);
		} else {
			reader = new VCFReader(filename);
		}
		
		VCFHeader header = reader.getHeader();
		for (VCFFilter filter: filterChain) {
			filter.setHeader(header);
		}
		
		header.addLine("##ngsutilsj_vcf_filterCommand="+NGSUtils.getArgs());
		if (!header.contains("##ngsutilsj_vcf_filterVersion="+NGSUtils.getVersion())) {
			header.addLine("##ngsutilsj__filterVersion="+NGSUtils.getVersion());
		}
		
		VCFWriter writer;
		if (out.equals("-")) {
			writer = new VCFWriter(System.out, header);
		} else {
			writer = new VCFWriter(out, header);
		}
		
		TallyValues<String> filterCounts = new TallyValues<String>();
		TallyValues<String> filterCounts2 = new TallyValues<String>();
		
		Iterator<VCFRecord> it = reader.iterator();
		long count = 0;
		long filtered = 0;
		
		for (VCFRecord rec: IterUtils.wrap(it)) {
//			System.err.println(rec+" ;; " + !onlyOutputPass+" ;; "+!rec.isFiltered());
			for (VCFFilter filter: filterChain) {
				filter.filter(rec);
//				System.err.println("filter: " + filter+ " rec = "+rec);
				if (rec == null) {
					break;
				}
			}
//			System.err.println(rec+" ;; " + !onlyOutputPass+" ;; "+!rec.isFiltered());
			if (!onlyOutputPass || !rec.isFiltered()) {
				count++;
				writer.write(rec);
			}
			if (rec.isFiltered()) {
				filtered++;
				for (String filter: rec.getFilters()) {
					filterCounts.incr(filter);
				}
				List<String> filters = new ArrayList<String>();
				filters.addAll(rec.getFilters());
				Collections.sort(filters);
				String key = StringUtils.join(",", filters);
				filterCounts2.incr(key);
			}
		}
		
		System.err.println(    "Wrote   : " + count + " variants");
		if (onlyOutputPass) {
			System.err.println("Filtered: " + filtered + " variants");
		}
		System.err.println();
		for (String filter: filterCounts.keySet()) {
			System.err.println(filter+": " +filterCounts.getCount(filter));
		}
		if (statsFilename != null) {
			TabWriter stats = new TabWriter(statsFilename);
			for (String filter: filterCounts2.keySet()) {
				stats.write(filter);
				stats.write(filterCounts2.getCount(filter));
				stats.eol();
			}
		}
		
		reader.close();
		writer.close();
	}

}