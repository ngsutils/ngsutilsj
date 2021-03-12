package io.compgen.ngsutils.tabix;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;

import io.compgen.common.io.DataIO;

public class BGZFile {

    public class BGZBlock {
        public final long pos;
        public final int cLength;
        public final byte[] uBuf;
        
        private BGZBlock(long pos, int cLength, byte[] uBuf) {
            this.pos = pos;
            this.cLength = cLength;
            this.uBuf = uBuf;
        }
    }

    public class BGZFileCache {
        protected Map<Long, BGZBlock> blockCache = new HashMap<Long, BGZBlock>();
        protected Deque<Long> lru = new LinkedList<Long>();
        protected long size = 0;
        protected long maxSize = 0;
        
        private BGZFileCache() {
            this(16 * 1024 * 1024);  // 16 MB cache
        }
        
        private BGZFileCache(long maxSize) {
            this.maxSize = maxSize;
        }
        
        private BGZBlock get(long pos) {

            if (!blockCache.containsKey(pos)) {
                return null;
            }
            
            lru.remove(pos);
            lru.add(pos);
            
            return blockCache.get(pos);
        }
        
        private void put(BGZBlock block) {

            while (block.uBuf.length + this.size > this.maxSize) {
                removeItem();
            }
            
            blockCache.put(block.pos, block);
            lru.add(block.pos);
            this.size += block.uBuf.length;
            
        }

        private void removeItem() {
            if (lru.size() > 0) {
                Long key = lru.pop();
                BGZBlock payload = blockCache.remove(key);
                this.size = this.size - payload.uBuf.length;
//                System.err.println("Removed: " + key + " from cache");
            }
        }
    }
    
    
//	protected String filename;
	protected RandomAccessFile file;
	protected BGZFileCache cache;
	
    public BGZFile(String filename) throws IOException {
        if (!isBGZFile(filename)) {
            throw new IOException("File: "+filename+" is not a valid BGZ file!");
        }
//        this.filename = filename;
        this.file = new RandomAccessFile(filename, "r");
        this.cache = new BGZFileCache();
    }

    public BGZFile(RandomAccessFile raf) throws IOException {
        if (!isBGZFile(raf)) {
            throw new IOException("RandomAccessFile is not a valid BGZ file!");
        }
//        this.filename = filename;
        this.file = raf;
        this.cache = new BGZFileCache();
    }

    
    
    public FileChannel getChannel() { 
        return file.getChannel();
    }
    
	public void close() throws IOException {
		file.close();
	}

	
	public byte[] readBlocks(long cOffsetBegin,int uOffsetBegin,long cOffsetEnd, int uOffsetEnd) throws IOException, DataFormatException {
        long curOffset = cOffsetBegin;
//		int blockNum = 1;
		
		byte[] buf = new byte[0];
		int pos = 0;
		
		while (curOffset <= cOffsetEnd) {
		    BGZBlock block = readBlock(curOffset);
			curOffset += block.cLength;
			
			byte[] cur = block.uBuf;

//			System.err.println("block["+(blockNum++)+"] "+curOffset+", "+cur.length);
//			
			int start = 0;
			int end = cur.length;
			
			if (curOffset == cOffsetBegin) {
//				System.err.println("Offsetting chunk (begin); "+uOffsetBegin);
				start = uOffsetBegin;
			}

			if (curOffset >= cOffsetEnd) {
//				System.err.println("Offsetting chunk (end); "+uOffsetEnd);
				end = uOffsetEnd;
			}

			if (start != 0 || end != cur.length) {
			    cur = Arrays.copyOfRange(cur, start, end);
			}

			buf = Arrays.copyOf(buf, buf.length + cur.length);
			for (int i=0; i<cur.length;i++) {
			    buf[pos + i] = cur[i];
			}
			pos = pos + cur.length;
			
//			System.err.println("start="+start+", end="+end+", buf.length="+buf.length);
		}
		
//        System.err.println(" block (complete)");
//        System.err.println(" ====>\n"+new String(buf)+"\n<=====");
		return buf;
	}

	public BGZBlock readBlock(long offset) throws IOException {
	    BGZBlock b = cache.get(offset);
	    if (b == null) {
	        if (file.getFilePointer()!=offset) {
	            if (offset >= file.length()) {
	                return null;
	            }

	            file.seek(offset);
	        }
	        b = readCurrentBlock();
	        cache.put(b);
	    }
        return b;
    }

	public BGZBlock readCurrentBlock() throws IOException {
//		System.err.println("reading chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
		
		long curOffset = file.getFilePointer();
		if (curOffset >= file.length()) {
//			System.err.println(filename+" is all done!");
			return null;
		}
		
		int magic1 = DataIO.readByte(file);
		int magic2 = DataIO.readByte(file);
		
		if (magic1 != 31) {
			throw new IOException("Bad Magic byte1");
		}
		if (magic2 != 139) {
			throw new IOException("Bad Magic byte2");
		}

//		int cm = DataIO.readByte(file);
//		int flg = DataIO.readByte(file);
//		long mtime = DataIO.readUint32(file);
//		int xfl = DataIO.readByte(file);
//		int os = DataIO.readByte(file);

		file.skipBytes(8);
		int xlen = DataIO.readUint16(file);
		
		byte[] extra = DataIO.readRawBytes(file, xlen);
		ByteArrayInputStream bais = new ByteArrayInputStream(extra);
		
		int s1 = 0;
		int s2 = 0;
		int bsize = 0;
		
		while (s1 != 66 && s2 != 67) {
			s1 = DataIO.readByte(bais);
			s2 = DataIO.readByte(bais);
			int slen = DataIO.readUint16(bais);
			byte[] payload = DataIO.readRawBytes(bais, slen);
			if (s1 == 66 && s2 == 67) {
				bsize = DataIO.bytesUint16(payload);
			}
		}
		bais.close();
		
		if (bsize == 0) {
		    throw new IOException("Invalid BGZF chunk (missing BSIZE)!");
		}

		// payload
		//byte[] cdata = DataIO.readRawBytes(file, bsize - xlen - 19);
		file.skipBytes(bsize - xlen - 19);
		// crc
		//long crc = DataIO.readUint32(file);
		file.skipBytes(4);
		
		// Uncompressed size [0, 65536]
		long isize = DataIO.readUint32(file);
//        System.err.println("isize => " + isize);

//		System.out.println("magic1: "+ magic1);
//		System.out.println("magic2: "+ magic2);
//		System.out.println("cm    : "+ cm);
//		System.out.println("flg   : "+ flg);
//		System.out.println("mtime : "+ mtime);
//		System.out.println("xfl   : "+ xfl);
//		System.out.println("os    : "+ os);
//		System.out.println("xlen  : "+ xlen);
//		System.out.println("bsize : "+ bsize);
//		System.out.println("cdata : <"+ cdata.length+" bytes>");
//		System.out.println("crc : "+ crc);
//		System.out.println("isize : "+ isize);
		
        // Now that we know all of the sizes, let's read in the compressed chunk and decompress the 
        // full GZip record (naked inflate on cdata was acting funny...)

		// jump back to the beginning of the record
		// and read it into a byte array
		
		byte[] cBuf = new byte[bsize+1];
		file.seek(curOffset);
		file.readFully(cBuf, 0, cBuf.length);

		GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(cBuf));
		byte[] uBuf = new byte[(int) isize];
		int readPos = 0;
		while (readPos < isize) {
			int c = in.read(uBuf, readPos, uBuf.length - readPos);
			if (c == -1) {
				break;
			}
			readPos += c;
//			System.err.println("================================");
//	        System.err.println("reading chunk -- fname  = " + filename+ ", curOffset = " + curOffset +", clen = "+(bsize+1)+", ulen = "+isize);
//            System.err.println("["+curOffset+"] Read "+c+" bytes from BGZF record // strlen=" + new String(uBuf).length()+", readPos="+readPos);
//            System.err.println(new String(uBuf).substring(0,readPos));
		}

		in.close();
//		System.err.println("read chunk -- fname  = " + filename+ ", curpos = " + file.getFilePointer() +", length = " + file.length());
		return new BGZBlock(curOffset,bsize+1,uBuf);
	}

//	public void dumpIndex() throws IOException {
//		if (index != null) {
//			index.dump();
//		}
//	}
	
	public static boolean isBGZFile(String filename) {
//		System.err.println("Checking file: "+filename);
		try {
			FileInputStream fis = new FileInputStream(filename);
			
			int magic1 = DataIO.readByte(fis);
			int magic2 = DataIO.readByte(fis);
			
			if (magic1 != 31) {
				fis.close();
				return false;
			}
			if (magic2 != 139) {
				fis.close();
				return false;
			}
			
			
			
//			int cm = DataIO.readByte(fis);
//			int flg = DataIO.readByte(fis);
//			long mtime = DataIO.readUint32(fis);
//			int xfl = DataIO.readByte(fis);
//			int os = DataIO.readByte(fis);
			DataIO.readByte(fis);
			DataIO.readByte(fis);
			DataIO.readUint32(fis);
			int xfl = DataIO.readByte(fis);
			DataIO.readByte(fis);

			if (xfl != 4) {
				fis.close();
				return false;
			}
			
			int xlen = DataIO.readUint16(fis);
			byte[] extra = DataIO.readRawBytes(fis, xlen);
			
//			System.out.println("magic1: "+ magic1);
//			System.out.println("magic2: "+ magic2);
//			System.out.println("cm    : "+ cm);
//			System.out.println("flg   : "+ flg);
//			System.out.println("mtime : "+ mtime);
//			System.out.println("xfl   : "+ xfl);
//			System.out.println("os    : "+ os);
//			System.out.println("xlen  : "+ xlen);
//			
			
			ByteArrayInputStream bais = new ByteArrayInputStream(extra);

			int s1 = 0;
			int s2 = 0;
			
			while (s1 != 66 && s2 != 67) {
				s1 = DataIO.readByte(bais);
				s2 = DataIO.readByte(bais);
				if (s1 == 66 && s2 == 67) {
					// good match
					bais.close();
					fis.close();
					return true;
				}
			}
			bais.close();
			fis.close();

		} catch (IOException e) {
		}

		return false;		
	}

	   public static boolean isBGZFile(RandomAccessFile fis) throws IOException {
//	      System.err.println("Checking file: "+filename);

           long offset = fis.getFilePointer();

	        try {
//	            FileInputStream fis = new FileInputStream(filename);
	            int magic1 = DataIO.readByte(fis);
	            int magic2 = DataIO.readByte(fis);
	            
	            if (magic1 != 31) {
                    fis.seek(offset);
	                return false;
	            }
	            if (magic2 != 139) {
                    fis.seek(offset);
	                return false;
	            }
	            
				DataIO.readByte(fis);
				DataIO.readByte(fis);
				DataIO.readUint32(fis);
				int xfl = DataIO.readByte(fis);
				DataIO.readByte(fis);

				if (xfl != 4) {
                    fis.seek(offset);
					return false;
				}
				
	            int xlen = DataIO.readUint16(fis);
	            
	            byte[] extra = DataIO.readRawBytes(fis, xlen);
	            ByteArrayInputStream bais = new ByteArrayInputStream(extra);
	            
	            int s1 = 0;
	            int s2 = 0;
	            
	            while (s1 != 66 && s2 != 67) {
	                s1 = DataIO.readByte(bais);
	                s2 = DataIO.readByte(bais);
	                if (s1 == 66 && s2 == 67) {
//	                  System.err.println("magic matches");
	                    fis.seek(offset);
	                    return true;
	                }
	            }

                fis.seek(offset);

	        } catch (IOException e) {
	        }

	        return false;       
	    }

}
