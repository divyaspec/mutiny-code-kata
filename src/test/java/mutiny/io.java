package mutiny;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.file.StandardOpenOption.READ;
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

    @SuppressWarnings("unchecked")
    public static Uni<Multi<Byte>> stream(Path path) {
        if (!path.toFile().exists()) return uni(() -> multi());
        return asynFileChannel(path, READ).map(channel ->
                (Multi.createBy()
                .repeating().uni(() -> new AtomicLong(0L), pos -> readFromFilePos(channel, pos))
                .until(b -> b.position() ==0)
                .flatMap(b -> (Multi)Multi.createFrom().emitter(e -> {
                    byte[] buf = b.array();
                    for (int i=0; i < b.position(); i++)
                        e.emit(buf[i]);
                    e.complete();
                })))
                .onTermination().invoke(() -> close(channel))
        );
    }

    public static <T extends AutoCloseable> void close(T ch) {
            try {
                ch.close();
            } catch (Throwable e) {

            }
    }

    private static Uni<AsynchronousFileChannel> asynFileChannel(Path path, StandardOpenOption openOption) {
        return uni(() -> {
            try {
                return AsynchronousFileChannel.open(path, openOption);
            } catch (IOException io) {
                throw SystemError(io, "Cannot open the file for path [%s], openOption [%s]");
            }
        });
    }

    private static Uni<ByteBuffer> readFromFilePos(AsynchronousFileChannel channel, AtomicLong pos) {
        CompletableFuture<ByteBuffer> done = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(10000);
        channel.read(buffer, pos.get(), null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                done.complete(buffer);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                done.completeExceptionally(exc);
            }
        });
        return uni(() -> buffer);
    }

    public static <T> Multi<T> multi(T... items) {
        return (Multi<T>) Multi.createFrom().item(items);
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