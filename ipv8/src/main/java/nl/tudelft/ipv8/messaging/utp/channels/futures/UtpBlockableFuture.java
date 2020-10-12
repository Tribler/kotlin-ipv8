package nl.tudelft.ipv8.messaging.utp.channels.futures;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class UtpBlockableFuture {
    protected volatile boolean isDone;
    protected volatile IOException exception;
    protected volatile Semaphore semaphore = new Semaphore(1);

    public UtpBlockableFuture() throws InterruptedException {
        semaphore.acquire();
    }

    /**
     * Blocks the current thread until the future task is done.
     */
    public void block() throws InterruptedException {
        semaphore.acquire();
        semaphore.release();
    }

    public boolean isDone() {
        return isDone;
    }

}
