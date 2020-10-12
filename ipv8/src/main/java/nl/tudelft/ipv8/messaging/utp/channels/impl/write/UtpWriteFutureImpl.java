package nl.tudelft.ipv8.messaging.utp.channels.impl.write;

import java.io.IOException;

import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpWriteFuture;

public class UtpWriteFutureImpl extends UtpWriteFuture {


    public UtpWriteFutureImpl() throws InterruptedException {
        super();
    }

    public void finished(IOException exp, int bytesWritten) {
        this.setBytesSend(bytesWritten);
        this.exception = exp;
        isDone = true;
        semaphore.release();
    }

    public void setBytesSend(int position) {
        bytesWritten = position;
    }

}
