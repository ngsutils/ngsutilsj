package io.compgen.ngsutils.cli.bam.count;

public interface SpanSource extends Iterable<SpanGroup> {
    public String[] getHeader();
    public long size();
    public long position();
}
