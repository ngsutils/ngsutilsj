package org.ngsutils.support.progress;

import java.util.Iterator;

import org.ngsutils.support.TTY;

import net.sf.samtools.util.CloseableIterator;

public class ProgressUtils {
    public static Progress getProgress(String name) {
        Progress p = null;

        String socket = System.getProperty(ProgressUtils.class.getPackage().getName()+".socket");
        if (p == null && socket != null) {
            try {
                int port = Integer.parseInt(socket);
                p = new SocketProgress(port);
            } catch (NumberFormatException e) {
                p = new SocketProgress(socket);
            }
        }

        socket = System.getenv((ProgressUtils.class.getPackage().getName()+".socket").replaceAll("\\.", "_").toUpperCase());
        if (p == null && socket != null) {
            try {
                int port = Integer.parseInt(socket);
                p = new SocketProgress(port);
            } catch (NumberFormatException e) {
                p = new SocketProgress(socket);
            }
        }

        // Rely on the shell stub to determine if we are in a tty.
        if (!TTY.isattyStdErr()) {
            return null;
        }
        
        // There is no good way to know if we are in a tty or not (thanks Java), so we need to look for either 
        // a system property or env variable to know if we need to suppress progress.
        String silent = System.getProperty(ProgressUtils.class.getPackage().getName()+".silent");
        if (silent != null && silent.equals("1")) {
            return null;
        }
        
        silent = System.getenv((ProgressUtils.class.getPackage().getName()+".silent").replaceAll("\\.", "_").toUpperCase());
        if (silent != null && silent.equals("1")) {
            return null;
        }

        if (p == null) {
            p = new StdErrProgress();
        }
        
        p.setName(name);
        return p;
    }

    public static <T> Iterator<T> getIterator(String name, final Iterator<T> it, final ProgressStats stats) {
        return getIterator(name, it, stats, null);
    }
    
    public static <T> Iterator<T> getIterator(String name, final Iterator<T> it, ProgressStats stats, final ProgressMessage<T> msg) {
        final Progress progress = ProgressUtils.getProgress(name);
        
        final ProgressStats statsMon;
        if (stats == null) {
            statsMon = new ProgressStats() {
                @Override
                public long size() {
                    return -1;
                }

                @Override
                public long position() {
                    return -1;
                }};
        } else {
            statsMon = stats;
        }
        
        if (progress!=null) {
            progress.start(statsMon.size());
        }
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                boolean hasNext = it.hasNext();
                if (!hasNext) {
                    if (progress != null) {
                        progress.done();
                    }
                    if (it instanceof CloseableIterator) {
                        ((CloseableIterator<T>) it).close();
                    }
                }
                return hasNext;
            }

            @Override
            public T next() {
                T current = it.next();
                if (progress != null) {
                    if (msg != null) {
                        progress.update(statsMon.position(), msg.msg(current));
                    } else {
                        progress.update(statsMon.position());
                    }
                }
                return current;
            }

            @Override
            public void remove() {
                it.remove();
            }};
    }
}
