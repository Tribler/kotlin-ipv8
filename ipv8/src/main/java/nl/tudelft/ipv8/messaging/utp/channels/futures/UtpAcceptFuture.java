package nl.tudelft.ipv8.messaging.utp.channels.futures;

import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;

public class UtpAcceptFuture extends UtpBlockableFuture {

    protected volatile UtpSocketChannelImpl channel = null;

    public UtpAcceptFuture() throws InterruptedException {
        super();
    }

    public UtpSocketChannel getChannel() {
        return channel;
    }

}
