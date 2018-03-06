package io.compgen.ngsutils.vcf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.compgen.common.StringUtils;
import io.compgen.ngsutils.support.GlobUtils;

public class VCFAttributes {
	public Map<String, VCFAttributeValue> attributes = new LinkedHashMap<String, VCFAttributeValue>();
	
	public void put(String key, VCFAttributeValue value) {
		attributes.put(key, value);
	}
	
    public VCFAttributeValue get(String key) {
        return attributes.get(key);
    }

    public List<String> findKeys(String keyGlob) {
        List<String> keys = new ArrayList<String>();

        for (String k: attributes.keySet()) {
            if (GlobUtils.matches(k,  keyGlob)) {
                keys.add(k);
            }
        }
        
        return keys;
    }

    
    public boolean contains(String key) {
		return attributes.containsKey(key);
	}
	
	public List<VCFAttributeValue> get(List<String> keys) {
		List<VCFAttributeValue> vals = new ArrayList<VCFAttributeValue>();
		for (String key: keys) {
			if (!contains(key)) {
				vals.add(VCFAttributeValue.MISSING);
			} else {
				vals.add(get(key));
			}
		}
		return vals;
	}
	
	public VCFAttributeValue remove(String key) {
		return attributes.remove(key);
	}
	
	public List<String> getKeys() {
		return new ArrayList<String>(attributes.keySet());
	}

	public static VCFAttributes parseString(String s) throws VCFParseException {
		VCFAttributes attrs = new VCFAttributes();
		
		for (String el: s.split(";")) {
			if (el.indexOf("=") == -1) {
				attrs.put(el, VCFAttributeValue.EMPTY);
			} else {
			    try {
			        String[] kv = el.split("=");				
			        attrs.put(kv[0], VCFAttributeValue.parse(kv[1]));
			    } catch (ArrayIndexOutOfBoundsException e) {
			        e.printStackTrace(System.err);
                    System.err.println("ERROR: processing attributes string "+ el);
                    System.err.println("ERROR: "+ s);
			        System.exit(1);
			    }
			}
		}
		return attrs;
	}
	
	public static VCFAttributes parseString(String s, List<String> format) throws VCFParseException {
		VCFAttributes attrs = new VCFAttributes();
		
		String[] spl = s.split(":");
		
		if (format.size() != spl.length) {
			throw new VCFParseException("Unable to parse genotype field: "+s);
		}
		
		for (int i=0; i< spl.length; i++) {
			attrs.put(format.get(i), VCFAttributeValue.parse(spl[i]));
		}

		return attrs;
	}

	// output in INFO format
	public String toString() {
		List<String> outcols = new ArrayList<String>();
		for (Entry<String, VCFAttributeValue> kv: attributes.entrySet()) {
			if (kv.getValue()==VCFAttributeValue.EMPTY) {
				outcols.add(kv.getKey());
			} else {
				outcols.add(kv.getKey()+"="+kv.getValue().toString());
			}
		}
		return StringUtils.join(";", outcols);
	}

	// output in GENOTYPE format (with given FORMAT keys)
	public String toString(List<String> format) {
		List<String> outcols = new ArrayList<String>();
		for (VCFAttributeValue val: get(format)) {
			outcols.add(val.toString());
		}
		return StringUtils.join(":", outcols);
	}
}