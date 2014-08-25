package org.ngsutils.annotation;

import org.ngsutils.annotation.SimpleAnnotationSource.SimpleValueAnnotation;
import org.ngsutils.bam.Strand;

public class SimpleAnnotationSource extends AbstractAnnotationSource<SimpleValueAnnotation> {

    public class SimpleValueAnnotation implements Annotation, Comparable<SimpleValueAnnotation> {
        final private String value;
        public SimpleValueAnnotation(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
        
        public String toString() {
            return value;
        }
        
        @Override
        public String[] toStringArray() {
            return new String[]{value};
        }

        @Override
        public int compareTo(SimpleValueAnnotation o) {
            return value.compareTo(o.getValue());
        }

    }

    public void addAnnotation(GenomeRegion coord, String val) {
        this.addAnnotation(coord, new SimpleValueAnnotation(val));
    }
    
    public void addAnnotation(String ref, int pos, String val) {
        this.addAnnotation(new GenomeRegion(ref, pos, Strand.NONE), new SimpleValueAnnotation(val));
    }
    
    public void addAnnotation(String ref, int pos, Strand strand, String val) {
        this.addAnnotation(new GenomeRegion(ref, pos, strand), new SimpleValueAnnotation(val));
    }
    
    public void addAnnotation(String ref, int start, int end, String val) {
        this.addAnnotation(new GenomeRegion(ref, start, end, Strand.NONE), new SimpleValueAnnotation(val));
    }

    public void addAnnotation(String ref, int start, int end, Strand strand, String val) {
        this.addAnnotation(new GenomeRegion(ref, start, end, strand), new SimpleValueAnnotation(val));
    }
    
    
    @Override
    public String[] getAnnotationNames() {
        return new String[]{"value"};
    }
}