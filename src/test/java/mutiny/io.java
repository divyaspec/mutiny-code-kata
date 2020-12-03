package mutiny;

import io.smallrye.mutiny.Uni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static mutiny.predef.SystemError;
import static mutiny.predef.uni;

public final class io {
    static ExecutorService blockingIOPool = cachedThreadPool("io");

    public static ExecutorService cachedThreadPool(final String name) {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            final AtomicInteger count = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, name + "-" + count.getAndIncrement());
            }
        });
    }

    public static Uni<byte[]> read(File f) {
        return read(f.toPath());
    }

    private static Uni<byte[]> read(Path path) {
        return uni(() -> {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw SystemError(e, "File cannot be read [%s]", path);
            }
        }).runSubscriptionOn(blockingIOPool);
    }


    static class Util {

        public static String readLine(InputStream in) throws IOException {
            return readUntil(in, '\n');
        }

        public static String readUntil(InputStream in, char delimiter) throws IOException {
            StringBuffer bf = new StringBuffer();
            int r = in.read();
            while (r != delimiter) {
                if (r != -1) {
                    bf.append((char) r);
                }
                r = in.read();
            }
            return bf.toString();
        }

        static Integer readInteger(InputStream in, char delimiter) throws IOException {
            return Integer.valueOf(readUntil(in, delimiter));
        }

        static String readString(Integer length, InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            do {
                c = in.read();
                if (c != -1) {
                    sb.append((char) c);
                }
            } while (sb.length() < length);
            return sb.toString();
        }

    }
}