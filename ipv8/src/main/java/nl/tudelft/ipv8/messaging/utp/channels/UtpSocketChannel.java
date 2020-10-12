package nl.tudelft.ipv8.messaging.utp.channels;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpCloseFuture;
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpConnectFuture;
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpReadFuture;
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpWriteFuture;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.conn.UtpConnectFutureImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.receive.UtpPacketRecievable;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils;

import static nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState.CLOSED;
import static nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState.SYN_SENT;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.MAX_USHORT;

public abstract class UtpSocketChannel implements UtpPacketRecievable {

    /* Sequencing begin */
    protected static short DEF_SEQ_START = Short.MIN_VALUE + 1;
    /* lock for the socket state* */
    protected final ReentrantLock stateLock = new ReentrantLock();
    /* timestamping utility */
    protected MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
    /*
     * address of the remote sockets which this socket is connected to
     */
    protected SocketAddress remoteAddress;
    /* current ack Number */
    protected short ackNumber;
    /* reference to the underlying UDP Socket */
    protected DatagramSocket dgSocket;
    /*
     * Connection Future Object - need to hold a reference here i case
     * connection the initial connection attempt fails. So it will be updates
     * once the reattempts success.
     */
    protected UtpConnectFutureImpl connectFuture = null;
    /* Current state of the socket */
    protected volatile UtpSocketState state = null;
    /* ID for outgoing packets */
    private short connectionIdSending;
    /* current sequenceNumber */
    private short sequenceNumber;
    /* ID for incoming packets */
    private short connectionIdReceiving;

    /**
     * Opens a new Socket and binds it to any available port
     *
     * @return {@link UtpSocketChannel}
     */
    public static UtpSocketChannel open(DatagramSocket socket) {
        UtpSocketChannelImpl c = new UtpSocketChannelImpl();
        c.setDgSocket(socket);
        c.setState(CLOSED);
        return c;
    }

    /**
     * Connects this Socket to the specified address
     *
     * @return {@link UtpConnectFuture}
     */
    public UtpConnectFuture connect(SocketAddress address) {
        stateLock.lock();
        try {
            try {
                connectFuture = new UtpConnectFutureImpl();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
            try {
                /* fill packet, set initial variables and send packet */
                setRemoteAddress(address);
                setupConnectionId();
                setSequenceNumber(DEF_SEQ_START);

                UtpPacket synPacket = UtpPacketUtils.createSynPacket();
                synPacket.setConnectionId(getConnectionIdReceiving());
                synPacket.setTimestamp(timeStamper.utpTimeStamp());
                sendPacket(synPacket);
                setState(SYN_SENT);

                incrementSequenceNumber();
                startConnectionTimeOutCounter(synPacket);
            } catch (IOException exp) {
                // DO NOTHING, let's try later with reconnect runnable
            }
        } finally {
            stateLock.unlock();
        }

        return connectFuture;
    }

    /**
     * Writes the content of the Buffer to the Channel.
     *
     * @param src the buffer, it is expected to be in Reading-Mode.
     * @return {@link UtpWriteFuture} which will be updated by the channel
     */
    public abstract UtpWriteFuture write(ByteBuffer src);

    /**
     * Reads the incoming data to the channel.
     *
     * @return {@link UtpWriteFuture} which will be updated by the channel
     */
    public abstract UtpReadFuture read(Consumer<byte[]> onFileReceived);


    /**
     * Closes the channel. Also unbinds the socket if the socket is not shared,
     * for example with the server.
     */
    public abstract UtpCloseFuture close();

    /**
     * @return The Connection ID for Outgoing Packets
     */
    public short getConnectionIdSending() {
        return connectionIdSending;
    }

    /* set connection ID for outgoing packets */
    protected void setConnectionIdSending(short connectionIdSending) {
        this.connectionIdSending = connectionIdSending;
    }

    /**
     * @return true if the channel is open.
     */
    public boolean isOpen() {
        return state != CLOSED;
    }

    public abstract boolean isReading();

    public abstract boolean isWriting();

    public short getConnectionIdReceiving() {
        return connectionIdReceiving;
    }

    protected void setConnectionIdReceiving(short connectionIdReceiving) {
        this.connectionIdReceiving = connectionIdReceiving;
    }

    public UtpSocketState getState() {
        return state;
    }

    protected void setState(UtpSocketState state) {
        this.state = state;
    }

    public SocketAddress getRemoteAdress() {
        return remoteAddress;
    }

    public short getAckNumber() {
        return ackNumber;
    }

    protected abstract void setAckNumber(short ackNumber);

    public short getSequenceNumber() {
        return sequenceNumber;
    }

    protected void setSequenceNumber(short sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public DatagramSocket getDgSocket() {
        return dgSocket;
    }

    /* signal the implementation to start the reConnect Timer */
    protected abstract void startConnectionTimeOutCounter(UtpPacket synPacket);

    /* implementation to cancel all operations and close the socket */
    protected abstract void abortImpl();

    /*
     * increments current sequence number unlike TCP, uTp is sequencing its
     * packets, not bytes but the Seq# is only 16 bits, so overflows are likely
     * to happen Seq# == 0 not possible.
     */
    protected void incrementSequenceNumber() {
        short seqNumber;
        if (getSequenceNumber() + 1 >= Short.MAX_VALUE) {
            seqNumber = Short.MIN_VALUE + 1;
        } else {
            seqNumber = (short) (getSequenceNumber() + 1);
        }
        setSequenceNumber(seqNumber);
    }

    /*
     * general methods to send packets need to be public in the impl, but also
     * need to be accessed from this class.
     */
    protected abstract void sendPacket(UtpPacket packet) throws IOException;

    protected abstract void sendPacket(DatagramPacket pkt) throws IOException;

    /*
     * sets up connection ids. incoming = rnd outgoing = incoming + 1
     */
    private void setupConnectionId() {
        Random rnd = new Random();
        int max = (int) (MAX_USHORT - 1);
        short rndInt = (short) (rnd.nextInt(max) + Short.MIN_VALUE);
        setConnectionIdReceiving(rndInt);
        setConnectionIdSending((short) (rndInt + 1));
    }

    /**
     * Creates an Ack-Packet.
     *
     * @param pkt              the packet to be Acked
     * @param timedifference   Between t1 and t2. t1 beeing the time the remote socket has
     *                         sent this packet, t2 when this socket received the packet.
     *                         Differences is <b>unsigned</b> and in �s resolution.
     * @param advertisedWindow How much space this socket has in its temporary receive buffer
     * @return ack packet
     */
    protected abstract UtpPacket createAckPacket(UtpPacket pkt, int timedifference, long advertisedWindow);

    /*
     * setting the ack current ack number to the sequence number of the packet.
     * needs to be accessed from this class and publicly from the
     * implementation, hence protected and abstract.
     */
    protected abstract void setAckNrFromPacketSqNr(UtpPacket utpPacket);

    /*
     * creates a datapacket, increments seq. number the packet is ready to be
     * filled with data.
     */
    protected abstract UtpPacket createDataPacket();

    protected abstract void setRemoteAddress(SocketAddress remoteAdress);
}
