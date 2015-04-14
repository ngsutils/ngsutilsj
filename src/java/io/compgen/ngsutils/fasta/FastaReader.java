package io.compgen.ngsutils.fasta;

import java.io.IOException;

public interface FastaReader {

    /*
     * start is zero-based
     */
    public abstract String fetch(String ref, int start, int end) throws IOException;

    public abstract void close() throws IOException;

}