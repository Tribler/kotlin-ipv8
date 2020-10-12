package nl.tudelft.ipv8.messaging.utp.channels.impl.conn;

import java.io.IOException;

import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpConnectFuture;

public class UtpConnectFutureImpl extends UtpConnectFuture {

    public UtpConnectFutureImpl() throws InterruptedException {
        super();
    }

    /**
     * Releases semaphore and sets return values.
     *
     * @param exp if exception occurred, null is possible.
     */
    public void finished(IOException exp) {
        this.exception = exp;
        this.isDone = true;
        semaphore.release();
    }

}
