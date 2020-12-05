package io.compgen.ngsutils.vcf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.compgen.common.StringUtils;

public class VCFRecord {

    // passing filter
	public static final String PASS = "PASS";
	// missing value marker
	public static final String MISSING = ".";
	
	protected String chrom;
	protected int pos; // one-based
	protected String dbSNPID = "";
	protected String ref = "";
	protected List<String> alt = null;
	protected double qual = -1;
	protected List<String> filters = null;

	protected VCFAttributes info = null;
	protected List<VCFAttributes> sampleAttributes = null;

	public VCFRecord(String chrom, int pos, String ref) {
		this.chrom = chrom;
		this.pos = pos;
		this.ref = ref;
	}
	
	public VCFRecord(String chrom, int pos, String dbSNPID, String ref, List<String> alt, double qual,
			List<String> filters, VCFAttributes info, List<VCFAttributes> sampleAttributes) {
		this.chrom = chrom;
		this.pos = pos;
		this.dbSNPID = dbSNPID;
		this.ref = ref;
		this.alt = alt;
		this.qual = qual;
		this.filters = filters;
		this.info = info;
		this.sampleAttributes = sampleAttributes;
	}

    public void write(OutputStream out) throws IOException{
		List<String> outcols = new ArrayList<String>();
		
		outcols.add(chrom);
		outcols.add(""+pos);
		if (dbSNPID == null) {
            outcols.add(MISSING);
		} else {
			outcols.add(dbSNPID);
		}
		outcols.add(ref);
		
		if (alt == null || alt.size() == 0) {
			outcols.add(MISSING);
		} else {
			outcols.add(StringUtils.join(",", alt));
		}
		
		if (qual == -1) {
			outcols.add(MISSING);
		} else {
			String qstr = ""+qual;
			if (qstr.endsWith(".0")) {
				qstr = qstr.substring(0,  qstr.length()-2);
			}
			outcols.add(qstr);
		}

		
        if (filters != null && filters.size() == 0) {
            outcols.add(MISSING);
        } else if (!isFiltered()) {
            outcols.add(PASS);
		} else {
          outcols.add(StringUtils.join(";", filters));
		}
		
	    outcols.add(info.toString());
        
		if (sampleAttributes != null && sampleAttributes.size() > 0) {
			List<String> keyOrder = sampleAttributes.get(0).getKeys();
            outcols.add(StringUtils.join(":", keyOrder));
			
			for (VCFAttributes attrs: sampleAttributes) {
                outcols.add(attrs.toString(keyOrder));
			}
		}
		
		StringUtils.writeOutputStream(out, StringUtils.join("\t", outcols)+"\n");
	}

    public static VCFRecord parseLine(String line) throws VCFParseException {
        return parseLine(line, false, null);
    }

    public static VCFRecord parseLine(String line, boolean removeID, VCFHeader header) throws VCFParseException {
		String[] cols = line.split("\t");
		if (cols.length< 5) {
			throw new VCFParseException("Missing columns in VCFRecord! => " + StringUtils.join(",", cols));
		}
		String chrom = cols[0];
		int pos = Integer.parseInt(cols[1]);
		
		String dbSNPID = cols[2];
		if (removeID || dbSNPID.equals(MISSING)) {
			dbSNPID = null;
		}
		
		String ref = cols[3];
		List<String> alts = null;
		for (String a: cols[4].split(",")) {
			if (!a.equals(MISSING)) {
				if (alts == null) {
					alts = new ArrayList<String>();
				}
				alts.add(a);
			}
		}
		
		double qual = -1;
		if (!cols[5].equals(MISSING)) {
			qual = Double.parseDouble(cols[5]);
		}
		
		List<String> filters = null;
		if (!cols[6].equals(PASS)) {
		    // if filters is null => PASS
		    // if filters is not null, but empty => MISSING

		    for (String f: cols[6].split(";")) {
				if (!f.equals(MISSING) && (header == null || header.isFilterAllowed(f))) {
	                if (filters == null) {
	                    filters = new ArrayList<String>();
	                }
				    filters.add(f);
				}
			}
		}

//		System.err.println("FILTER: " + cols[6] + " => " + (filters == null ? "<null>": "?"+StringUtils.join(",", filters)));
		
        VCFAttributes info = VCFAttributes.parseInfo(cols[7], header);
		
//		System.err.println(info.toString());
		
		List<VCFAttributes> samples = null;
		
		if (cols.length>8) {
			List<String> format = new ArrayList<String>();
			for (String k: cols[8].split(":")) {
			        format.add(k);
			}
			
			samples = new ArrayList<VCFAttributes>();
			
			for (int i=9; i<cols.length; i++) {
				samples.add(VCFAttributes.parseFormat(cols[i], format, header));
			}
		}
		
		return new VCFRecord(chrom, pos, dbSNPID, ref, alts, qual, filters, info, samples);
	}

	public String getChrom() {
		return chrom;
	}

	public void setChrom(String chrom) {
		this.chrom = chrom;
	}

	public int getPos() {
		return pos;
	}

	public void setPos(int pos) {
		this.pos = pos;
	}

	public String getDbSNPID() {
		return dbSNPID;
	}

	public void setDbSNPID(String dbSNPID) {
		this.dbSNPID = dbSNPID;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public List<String> getAlt() {
		return alt;
	}

	public void addAlt(String alt) {
		if (this.alt == null) {
			this.alt = new ArrayList<String>();
		}
		this.alt.add(alt);
	}

	public void clearAlt() {
		this.alt = null;
	}
	
	public double getQual() {
		return qual;
	}

	public void setQual(int qual) {
		this.qual = qual;
	}

	public List<String> getFilters() {
		return filters;
	}

	public void addFilter(String filter) {
		if (filters == null) {
			filters = new ArrayList<String>();
		}
		filters.add(filter);
	}

	public void clearFilters() {
		this.filters = null;
	}

	public VCFAttributes getInfo() {
		return info;
	}

	public void setInfo(VCFAttributes info) {
		this.info = info;
	}

	public List<VCFAttributes> getSampleAttributes() {
		return sampleAttributes;
	}

	public void addSampleAttributes(VCFAttributes attrs) {
		if (sampleAttributes == null) {
			sampleAttributes = new ArrayList<VCFAttributes>();
		}
		sampleAttributes.add(attrs);
	}

    public boolean isIndel() {
        if (ref.length() != 1) {
            return true;
        }
        
        for (String a: alt) {
            if (a.length() != 1) {
                return true;
            }
        }
        return false;
    }

    public boolean isFiltered() {
        return (filters != null && filters.size() > 0);
    }

}
