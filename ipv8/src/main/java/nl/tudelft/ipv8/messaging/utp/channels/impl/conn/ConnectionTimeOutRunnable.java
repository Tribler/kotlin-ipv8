
package nl.tudelft.ipv8.messaging.utp.channels.impl.conn;

import java.util.concurrent.locks.ReentrantLock;

import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;

public class ConnectionTimeOutRunnable implements Runnable {

    private UtpPacket synPacket;
    private UtpSocketChannelImpl channel;

    public ConnectionTimeOutRunnable(UtpPacket packet,
                                     UtpSocketChannelImpl channel) {
        this.synPacket = packet;
        this.channel = channel;
    }

    @Override
    public void run() {
        channel.resendSynPacket(synPacket);
    }


}
