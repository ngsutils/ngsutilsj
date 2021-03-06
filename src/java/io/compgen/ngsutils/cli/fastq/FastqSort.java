package io.compgen.ngsutils.cli.fastq;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.ComparablePair;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;

@Command(name="fastq-sort", desc="Sorts a FASTQ file", category="fastq")
public class FastqSort extends AbstractOutputCommand {
	private String filename =  null;

	private int bufferSize = 200000;
	private boolean bySequence = false;
	private boolean noCompressTemp = false;
	private boolean verbose = false;

	private File tmpdir = null;

	private ArrayList<String> tempFiles = new ArrayList<String>();

	public FastqSort(){
	}

	@Option(desc="Number of reads to include in temporary files (default: 200000)", name="buf", defaultValue="200000")
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	@Option(desc="Sort by sequence (default: sort by name)", name="seq")
	public void setBySequence(boolean bySequence) {
		this.bySequence = bySequence;
	}

	@UnnamedArg(name="FILE")
	public void setFilename(String filename) throws IOException {
	    this.filename = filename;
	}

	@Option(desc="Compress temporary files (default: true)", name="nogz")
	public void setNoCompressTemp(boolean noCompressTemp) {
		this.noCompressTemp = noCompressTemp;
	}

	@Option(desc="Write temporary files to this directory", name="tmp")
	public void setTmpdir(String tmpdir) {
		this.tmpdir = new File(tmpdir);
	}

    @Exec
	public void exec() throws IOException {
		long readCount = 0;
		ArrayList<FastqRead> buffer = new ArrayList<FastqRead>();
		if (verbose) {
			System.err.println("Splitting into subfiles...");
		}
		
	    FastqReader reader = Fastq.open(filename);
		
		for (FastqRead read : reader) {
			readCount++;
			buffer.add(read);
			if (bufferSize > -1) {
				if (buffer.size() >= bufferSize) {
					if (verbose) {
						System.err.println("Reads: "+readCount);
					}
					writeTemp(buffer);
					buffer.clear();
				}
			} else if (readCount % 1000 == 0) {
				// only write temp files when the used memory is over 80%.
			    // only check every 1000 reads...

			    double usedPercent=(double)(Runtime.getRuntime().totalMemory()
					    -Runtime.getRuntime().freeMemory())/Runtime.getRuntime().maxMemory();
				if (usedPercent > 0.80) {
					writeTemp(buffer);
					buffer.clear();
					Runtime.getRuntime().gc();
				}
			}
		}
		reader.close();
		writeTemp(buffer);
		buffer.clear();
		if (verbose) {
			System.err.println("Total reads: "+readCount);
			System.err.println("Total number of subfiles: "+tempFiles.size());
			System.err.println("Merging subfiles...");
		}
        ArrayList<Iterator<FastqRead>> iterators = new ArrayList<Iterator<FastqRead>>();
        ArrayList<FastqReader> readers = new ArrayList<FastqReader>();
		boolean addedRead = true;
		for (String tmpname : tempFiles) {
		    FastqReader reader1 = Fastq.open(tmpname);
		    readers.add(reader1);
			iterators.add(reader1.iterator());
			buffer.add(null);
		}

		readCount = 0;
		int j = 0;

		while (addedRead) {
			ArrayList<ComparablePair<String, Integer>> sortList = new ArrayList<ComparablePair<String, Integer>>();
			addedRead = false;

			for (int i = 0; i < buffer.size(); i++) {
				if (buffer.get(i) == null) {
					if (iterators.get(i).hasNext()) {
						FastqRead read = iterators.get(i).next();
						buffer.set(i, read);
					}
				}
				if (buffer.get(i) != null) {
					addedRead = true;
					if (bySequence) {
						sortList.add(new ComparablePair<String, Integer>(buffer.get(i)
								.getSeq(), i));
					} else {
						sortList.add(new ComparablePair<String, Integer>(buffer.get(i)
								.getName(), i));
					}
				}
			}

			Collections.sort(sortList, new Comparator<ComparablePair<String, Integer>>() {
				@Override
				public int compare(ComparablePair<String, Integer> o1,
						ComparablePair<String, Integer> o2) {
					return o1.one.compareTo(o2.one);
				}
			});

			if (addedRead) {
				int bestIdx = sortList.get(0).two;
				buffer.get(bestIdx).write(out);
				buffer.set(bestIdx, null);
			} else {
				// Nothing left to read from files... just write them all.
				for (ComparablePair<String, Integer> pair : sortList) {
					buffer.get(pair.two).write(out);
				}
			}
			if (j >= bufferSize && verbose) {
				j = 0;
				System.err.println("Merged: "+j);
			}
			j++;
		}
		close();
		for (FastqReader reader1: readers) {
		    reader1.close();		    
		}
	}

	private void writeTemp(ArrayList<FastqRead> buffer) throws IOException {
		Collections.sort(buffer, new Comparator<FastqRead>() {
			@Override
			public int compare(FastqRead o1, FastqRead o2) {
				if (bySequence) {
					return o1.getSeq().compareTo(o2.getSeq());
				} else {
					return o1.getName().compareTo(o2.getName());
				}
			}
		});

		String suffix = ".tmp";
		if (!noCompressTemp) {
		    suffix = ".gz"; 
		}
		
		File temp;
		if (tmpdir == null) {
			temp = Files.createTempFile(".fastq-sort-", suffix).toFile();
		} else {
			temp = Files.createTempFile(tmpdir.toPath(), ".fastq-sort-", suffix).toFile();
		}
		temp.setReadable(true, true);
		temp.setWritable(true, true);
		temp.setExecutable(false, false);
		tempFiles.add(temp.getAbsolutePath());
		temp.deleteOnExit();

		OutputStream tmpOut;
		if (noCompressTemp) {
			tmpOut = new BufferedOutputStream(new FileOutputStream(temp));
		} else {
			tmpOut = new GZIPOutputStream(new FileOutputStream(temp));
		}
		for (FastqRead read1 : buffer) {
			read1.write(tmpOut);
		}
		tmpOut.close();
	}
}
