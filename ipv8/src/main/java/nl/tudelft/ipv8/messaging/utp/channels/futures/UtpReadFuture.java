package nl.tudelft.ipv8.messaging.utp.channels.futures;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.locks.ReentrantLock;

public class UtpReadFuture extends UtpBlockableFuture {
    protected final ReentrantLock listenerLock = new ReentrantLock();
    protected volatile ByteArrayOutputStream bos;
    protected volatile UtpReadListener listener;

    public UtpReadFuture() throws InterruptedException {
        super();
    }

    /**
     * Sets a listener that will be informed once the future task is completed.
     */
    public void setListener(UtpReadListener listener) {
        listenerLock.lock();
        this.listener = listener;
        listenerLock.unlock();
    }
}
