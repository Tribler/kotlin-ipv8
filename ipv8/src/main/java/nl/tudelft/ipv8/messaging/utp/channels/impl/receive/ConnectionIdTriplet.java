package nl.tudelft.ipv8.messaging.utp.channels.impl.receive;

import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;

public class ConnectionIdTriplet {

    private UtpSocketChannel channel;
    private short incoming;
    //outgoing is incoming connection ID + 1 as specified
    private short outGoing;

    public ConnectionIdTriplet(UtpSocketChannel channel, short incoming, short outgoing) {
        this.channel = channel;
        this.incoming = incoming;
        this.outGoing = outgoing;
    }

    public UtpSocketChannel getChannel() {
        return channel;
    }

    public void setChannel(UtpSocketChannelImpl channel) {
        this.channel = channel;
    }

    @Override
    public boolean equals(Object other) {

        if (other == null) {
            return false;
        } else if (this == other) {
            return true;
        } else if (!(other instanceof ConnectionIdTriplet)) {
            return false;
        } else {
            ConnectionIdTriplet otherTriplet = (ConnectionIdTriplet) other;
            return this.incoming == otherTriplet.getIncoming() &&
                this.outGoing == otherTriplet.getOutGoing();
        }
    }

    @Override
    public int hashCode() {
        int hash = 23;
        hash = hash * 31 + (int) outGoing;
        hash = hash * 31 + (int) incoming;
        return hash;
    }

    public short getIncoming() {
        return incoming;
    }


    public void setIncoming(short incoming) {
        this.incoming = incoming;
    }


    public short getOutGoing() {
        return outGoing;
    }


    public void setOutgoing(short outGoing) {
        this.outGoing = outGoing;
    }
}
