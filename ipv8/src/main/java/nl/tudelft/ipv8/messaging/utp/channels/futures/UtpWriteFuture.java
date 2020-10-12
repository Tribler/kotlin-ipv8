package nl.tudelft.ipv8.messaging.utp.channels.futures;

public abstract class UtpWriteFuture extends UtpBlockableFuture {

    protected volatile int bytesWritten;

    public UtpWriteFuture() throws InterruptedException {
        super();
    }
}
