package nl.tudelft.ipv8.messaging.utp.channels.impl.read;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpReadFuture;

public class UtpReadFutureImpl extends UtpReadFuture {

    private final Consumer<byte[]> onFileReceived;

    public UtpReadFutureImpl(Consumer<byte[]> onFileReceived) throws InterruptedException {
        super();
        this.onFileReceived = onFileReceived;
    }

    /**
     * Releasing semaphore and running the listener if set.
     */
    public void finished(IOException exp, ByteArrayOutputStream bos) {
        this.bos = bos;
        this.exception = exp;
        isDone = true;
        semaphore.release();
        listenerLock.lock();
        onFileReceived.accept(bos.toByteArray());
        listenerLock.unlock();
    }
}
