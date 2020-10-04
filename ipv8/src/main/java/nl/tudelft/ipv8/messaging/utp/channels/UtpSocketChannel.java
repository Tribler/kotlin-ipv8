/* Copyright 2013 Ivan Iljkic
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
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
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUshort;

/**
 * Interface for a Socket
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public abstract class UtpSocketChannel implements UtpPacketRecievable {

    /* Sequencing begin */
    protected static int DEF_SEQ_START = 1;
    /* lock for the socket state* */
    protected final ReentrantLock stateLock = new ReentrantLock();
    /* timestamping utility */
    protected MicroSecondsTimeStamp timeStamper = new MicroSecondsTimeStamp();
    /*
     * address of the remote sockets which this socket is connected to
     */
    protected SocketAddress remoteAddress;
    /* current ack Number */
    protected int ackNumber;
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
    private long connectionIdSending;
    /* current sequenceNumber */
    private int sequenceNumber;
    /* ID for incoming packets */
    private int connectionIdReceiving;

    /**
     * Opens a new Socket and binds it to any available port
     *
     * @return {@link UtpSocketChannel}
     * @throws IOException see {@link DatagramSocket#DatagramSocket()}
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
     * @param address
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
            /* fill packet, set initial variables and send packet */
            setRemoteAddress(address);
            setupConnectionId();
            setSequenceNumber(DEF_SEQ_START);

            UtpPacket synPacket = UtpPacketUtils.createSynPacket();
            synPacket
                .setConnectionId(longToUshort(getConnectionIdReceiving()));
            synPacket.setTimestamp(timeStamper.utpTimeStamp());
            sendPacket(synPacket);
            setState(SYN_SENT);
            printState("[Syn send] ");

            incrementSequenceNumber();
            startConnectionTimeOutCounter(synPacket);
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
    public long getConnectionIdSending() {
        return connectionIdSending;
    }

    /**
     * @return true if the channel is open.
     */
    public boolean isOpen() {
        return state != CLOSED;
    }

    public abstract boolean isReading();

    public abstract boolean isWriting();

    /**
     * @return true, if this socket is connected to another socket.
     */
    public boolean isConnected() {
        return getState() == UtpSocketState.CONNECTED;
    }

    public int getConnectionIdReceiving() {
        return connectionIdReceiving;
    }

    protected void setConnectionIdReceiving(int connectionIdReceiving) {
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

    public int getAckNumber() {
        return ackNumber;
    }

    protected abstract void setAckNumber(int ackNumber);

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    protected void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public DatagramSocket getDgSocket() {
        return dgSocket;
    }

    protected abstract void setDgSocket(DatagramSocket dgSocket);

    /* signal the implementation to start the reConnect Timer */
    protected abstract void startConnectionTimeOutCounter(UtpPacket synPacket);

    /* debug method to print id's, seq# and ack# */
    protected void printState(String msg) {
        String state = "[ConnID Sending: " + connectionIdSending + "] "
            + "[ConnID Recv: " + connectionIdReceiving + "] [SeqNr. "
            + sequenceNumber + "] [AckNr: " + ackNumber + "]";
    }

    /* implementation to cancel all operations and close the socket */
    protected abstract void abortImpl();

    /*
     * increments current sequence number unlike TCP, uTp is sequencing its
     * packets, not bytes but the Seq# is only 16 bits, so overflows are likely
     * to happen Seq# == 0 not possible.
     */
    protected void incrementSequenceNumber() {
        int seqNumber = getSequenceNumber() + 1;
        if (seqNumber > MAX_USHORT) {
            seqNumber = 1;
        }
        setSequenceNumber(seqNumber);
    }

    /*
     * general methods to send packets need to be public in the impl, but also
     * need to be accessed from this class.
     */
    protected abstract void sendPacket(UtpPacket packet);

    protected abstract void sendPacket(DatagramPacket pkt);

    /*
     * sets up connection ids. incoming = rnd outgoing = incoming + 1
     */
    private void setupConnectionId() {
        Random rnd = new Random();
        int max = (int) (MAX_USHORT - 1);
        int rndInt = rnd.nextInt(max);
        setConnectionIdReceiving(rndInt);
        setConnectionIdSending(rndInt + 1);
    }

    /* set connection ID for outgoing packets */
    protected void setConnectionIdSending(long connectionIdSending) {
        this.connectionIdSending = connectionIdSending;
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
