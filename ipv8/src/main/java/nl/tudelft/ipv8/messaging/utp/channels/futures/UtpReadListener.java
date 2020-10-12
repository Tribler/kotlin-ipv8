package nl.tudelft.ipv8.messaging.utp.channels.futures;

import java.io.IOException;

public abstract class UtpReadListener implements Runnable {
    protected IOException exception;
    protected Thread currentThread = null;

    public IOException getIOException() {
        return exception;
    }

    public void setIOException(IOException exp) {
        this.exception = exp;
    }

    @Override
    public void run() {
        this.currentThread = Thread.currentThread();
        actionAfterReading();
    }

    /**
     * Implement the action after reading.
     */
    public abstract void actionAfterReading();
}
