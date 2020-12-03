package mutiny;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class FileSink implements MultiSubscriber<byte[]> {

    final File file;
    private AsynchronousFileChannel in;
    private final CompletableFuture<File> done = new CompletableFuture<>();
    private final AtomicReference<CompletableFuture<Void>> doneReading = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicLong index = new AtomicLong(0);
    private Subscription subscription;

    public final Uni<File> complete = Uni.createFrom().completionStage(done);

    FileSink(File file) {
        this.file = file;
    }

    @Override
    public void onItem(byte[] item) {
        doneReading.set(new CompletableFuture<>());
        in.read(ByteBuffer.wrap(item), index.get(), subscription, new CompletionHandler<Integer, Subscription>() {
            @Override
            public void completed(Integer result, Subscription attachment) {
                index.addAndGet(item.length);
                doneReading.get().complete(null);
                subscription.request(1);
            }

            @Override
            public void failed(Throwable exc, Subscription attachment) {
                subscription.cancel();
                doneReading.get().completeExceptionally(exc);
                done.completeExceptionally(exc);
            }
        });
    }

    @Override
    public void onFailure(Throwable failure) {
        close();
        done.completeExceptionally(failure);
    }

    private void close() {
        try {
            in.close();
        } catch (Throwable ignore) {

        }
    }

    @Override
    public void onCompletion() {
        doneReading.get().thenAccept(x -> {
           close();
           done.complete(file);
        });
    }

    @Override
    public void onSubscribe(Subscription s) {
        try {
            in = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ);
            this.subscription = subscription;
            this.subscription.request(1);
        } catch (IOException io) {
            throw predef.SystemError(io, "Cannot read the file [%s]", file.getAbsolutePath());
        }

    }
}
