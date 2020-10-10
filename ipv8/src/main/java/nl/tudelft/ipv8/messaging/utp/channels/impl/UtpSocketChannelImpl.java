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
package nl.tudelft.ipv8.messaging.utp.channels.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel;
import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState;
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpCloseFuture;
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpWriteFuture;
import nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration;
import nl.tudelft.ipv8.messaging.utp.channels.impl.conn.ConnectionTimeOutRunnable;
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.SkippedPacketBuffer;
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.UTPReadingRunnableLoggerKt;
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.UtpReadFutureImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.UtpReadingRunnable;
import nl.tudelft.ipv8.messaging.utp.channels.impl.write.UtpWriteFutureImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.write.UtpWritingRunnable;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.SelectiveAckHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils;
import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;

import static nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState.CLOSED;
import static nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState.CONNECTED;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.FIN;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.SELECTIVE_ACK;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.STATE;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.extractUtpPacket;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.isDataPacket;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.isFinPacket;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.isResetPacket;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.isStatePacket;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.isSynPkt;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.MAX_USHORT;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUint;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUshort;

/**
 * Implements and hides implementation details from the superclass.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class UtpSocketChannelImpl extends UtpSocketChannel {

    private final Object sendLock = new Object();
//    private volatile BlockingQueue<UtpTimestampedPacketDTO> writingQueue = new LinkedBlockingQueue<>();
//    private volatile BlockingQueue<UtpTimestampedPacketDTO> readingQueue = new LinkedBlockingQueue<>();
    private volatile MyQueue writingQueue = new MyQueue();
    private volatile MyQueue readingQueue = new MyQueue();
    private UtpWritingRunnable writer;
    private UtpReadingRunnable reader;
    private ScheduledExecutorService retryConnectionTimeScheduler;
    private int connectionAttempts = 0;












    private static final int PACKET_DIFF_WARP = 50000;
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    private SkippedPacketBuffer skippedBuffer = new SkippedPacketBuffer();
    private boolean exceptionOccured = false;
    private boolean graceFullInterrupt;
    private boolean isRunning;
    private long totalPayloadLength = 0;
    private long lastPacketTimestamp;
    private int lastPayloadLength;
    private UtpReadFutureImpl readFuture;
    private long nowtimeStamp;
    private long lastPackedReceived;
    private long startReadingTimeStamp;
    private boolean gotLastPacket = false;
    // in case we ack every x-th packet, this is the counter.
    private int currentPackedAck = 0;



























    public UtpSocketChannelImpl() {
    }

    /*
     * Handles packet.
     */
    @Override
    public void receivePacket(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("receivePacket");
        if (isSynAckPacket(udpPacket)) {
            handleSynAckPacket(udpPacket);
        } else if (isResetPacket(udpPacket)) {
            handleResetPacket(udpPacket);
        } else if (isSynPkt(udpPacket)) {
            handleIncomingConnectionRequest(udpPacket);
        } else if (isDataPacket(udpPacket)) {
            handleDataPacket(udpPacket);
        } else if (isStatePacket(udpPacket)) {
            handleStatePacket(udpPacket);
        } else if (isFinPacket(udpPacket)) {
            handleFinPacket(udpPacket);
        } else {
//            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private void handleFinPacket(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleFinPacket");
        stateLock.lock();
        try {
            setState(UtpSocketState.GOT_FIN);
            UtpPacket finPacket = extractUtpPacket(udpPacket);
            long freeBuffer;
            if (reader != null && reader.isRunning()) {
                freeBuffer = reader.getLeftSpaceInBuffer();
            } else {
                freeBuffer = UtpAlgConfiguration.MAX_PACKET_SIZE;
            }
            ackPacket(finPacket, timeStamper.utpDifference(finPacket.getTimestamp()), freeBuffer);
        } catch (IOException exp) {
            exp.printStackTrace();
            // TODO: what to do if exception?
        } finally {
            stateLock.unlock();
        }
    }

    private void handleResetPacket(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleResetPacket");
        this.close();
    }

    private boolean isSynAckPacket(DatagramPacket udpPacket) {
        return isStatePacket(udpPacket) && getState() == UtpSocketState.SYN_SENT;
    }

    private void handleSynAckPacket(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleSyncAckPacket");
        UtpPacket pkt = extractUtpPacket(udpPacket);
        if ((pkt.getConnectionId() & 0xFFFF) == getConnectionIdReceiving()) {
            stateLock.lock();
            setAckNrFromPacketSqNr(pkt);
            setState(CONNECTED);
            printState("[SynAck received] ");
            disableConnectionTimeOutCounter();
            connectFuture.finished(null);
            stateLock.unlock();
        } else {
//            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private boolean isSameAddressAndId(long id, SocketAddress addr) {
        return (id) == getConnectionIdReceiving() && addr.equals(getRemoteAdress());
    }

    private void disableConnectionTimeOutCounter() {
        if (retryConnectionTimeScheduler != null) {
            retryConnectionTimeScheduler.shutdown();
            retryConnectionTimeScheduler = null;
        }
        connectionAttempts = 0;
    }

    public int getConnectionAttempts() {
        return connectionAttempts;
    }

    public void incrementConnectionAttempts() {
        connectionAttempts++;
    }

    private void handleDataPacket(DatagramPacket udpPacket) {
        UtpPacket utpPacket = extractUtpPacket(udpPacket);
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleDataPacket, seq=" + utpPacket.getSequenceNumber() + ", ack=" + utpPacket.getAckNumber() + ", exp=" + getExpectedSeqNr());
        UtpTimestampedPacketDTO timestampedPair = new UtpTimestampedPacketDTO(udpPacket, utpPacket, timeStamper.timeStamp(), timeStamper.utpTimeStamp());
        currentPackedAck++;
//                    UTPReadingRunnableLoggerKt.getLogger().debug("Seq: " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
        lastPackedReceived = timestampedPair.stamp();
        try {
            if (isLastPacket(timestampedPair)) {
                gotLastPacket = true;
                UTPReadingRunnableLoggerKt.getLogger().debug("GOT LAST PACKET");
                lastPacketTimestamp = timeStamper.timeStamp();
            }
            if (isPacketExpected(timestampedPair.utpPacket())) {
                UTPReadingRunnableLoggerKt.getLogger().debug("Handle expected packet: " + timestampedPair.utpPacket().getSequenceNumber());
                handleExpectedPacket(timestampedPair);
            } else {
                UTPReadingRunnableLoggerKt.getLogger().debug("Handle UUNNNEXPECTED PACKET");
                handleUnexpectedPacket(timestampedPair);
            }
            if (ackThisPacket()) {
                currentPackedAck = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isTimedOut() {
        //TODO: extract constants...
        /* time out after 4sec, when eof not reached */
        boolean timedOut = nowtimeStamp - lastPackedReceived >= 4000000;
        /* but if remote socket has not received synack yet, he will try to reconnect
         * await that as well */
        boolean connectionReattemptAwaited = nowtimeStamp - startReadingTimeStamp >= 4000000;
        return timedOut && connectionReattemptAwaited;
    }

    private boolean isLastPacket(UtpTimestampedPacketDTO timestampedPair) {
        return (timestampedPair.utpPacket().getWindowSize()) == 0;
    }

    private void handleExpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
        if (hasSkippedPackets()) {
            bos.write(timestampedPair.utpPacket().getPayload());
            int payloadLength = timestampedPair.utpPacket().getPayload().length;
            lastPayloadLength = payloadLength;
            totalPayloadLength += payloadLength;
            Queue<UtpTimestampedPacketDTO> packets = skippedBuffer.getAllUntillNextMissing();
            int lastSeqNumber = 0;
            if (packets.isEmpty()) {
                lastSeqNumber = timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF;
            }
            UtpPacket lastPacket = null;
            for (UtpTimestampedPacketDTO p : packets) {
                bos.write(p.utpPacket().getPayload());
                payloadLength += p.utpPacket().getPayload().length;
                lastSeqNumber = p.utpPacket().getSequenceNumber() & 0xFFFF;
                lastPacket = p.utpPacket();
            }
            skippedBuffer.reindex(lastSeqNumber);
            UTPSocketChannelImplLoggerKt.getLogger().debug("set Ack number 1 to " + lastSeqNumber);
            setAckNumber(lastSeqNumber);
            //if still has skipped packets, need to selectively ack
            if (hasSkippedPackets()) {
                if (ackThisPacket()) {
                    SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
                    selectiveAckPacket(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
                }

            } else {
                if (ackThisPacket()) {
                    ackPacket(lastPacket, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
                }
            }
        } else {
            if (ackThisPacket()) {
                ackPacket(timestampedPair.utpPacket(), getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
            } else {
                UTPSocketChannelImplLoggerKt.getLogger().debug("set Ack number 2 to " + (timestampedPair.utpPacket().getSequenceNumber()) + ", " + (timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF));
                setAckNumber(timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF);
            }
            bos.write(timestampedPair.utpPacket().getPayload());
            totalPayloadLength += timestampedPair.utpPacket().getPayload().length;
        }
    }

    private boolean ackThisPacket() {
        return currentPackedAck >= UtpAlgConfiguration.SKIP_PACKETS_UNTIL_ACK;
    }

    /**
     * Returns the average space available in the buffer in Bytes.
     *
     * @return bytes
     */
    public long getLeftSpaceInBuffer() throws IOException {
        return (skippedBuffer.getFreeSize()) * lastPayloadLength;
    }

    private int getTimestampDifference(UtpTimestampedPacketDTO timestampedPair) {
        return timeStamper.utpDifference(timestampedPair.utpTimeStamp(), timestampedPair.utpPacket().getTimestamp());
    }

    private void handleUnexpectedPacket(UtpTimestampedPacketDTO timestampedPair) throws IOException {
        int expected = getExpectedSeqNr();
        int seqNr = timestampedPair.utpPacket().getSequenceNumber() & 0xFFFF;
        if (skippedBuffer.isEmpty()) {
            skippedBuffer.setExpectedSequenceNumber(expected);
        }
        //TODO: wrapping seq nr: expected can be 5 e.g.
        // but buffer can receive 65xxx, which already has been acked, since seq numbers wrapped.
        // current implementation puts this wrongly into the buffer. it should go in the else block
        // possible fix: alreadyAcked = expected > seqNr || seqNr - expected > CONSTANT;
        boolean alreadyAcked = expected > seqNr || seqNr - expected > PACKET_DIFF_WARP;

        boolean saneSeqNr = expected == skippedBuffer.getExpectedSequenceNumber();
        if (saneSeqNr && !alreadyAcked) {
            skippedBuffer.bufferPacket(timestampedPair);
            // need to create header extension after the packet is put into the incoming buffer.
            SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
            if (ackThisPacket()) {
                selectiveAckPacket(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
            }
        } else if (ackThisPacket()) {
            SelectiveAckHeaderExtension headerExtension = skippedBuffer.createHeaderExtension();
            ackAlreadyAcked(headerExtension, getTimestampDifference(timestampedPair), getLeftSpaceInBuffer());
        }
    }

    /**
     * True if this packet is expected.
     *
     * @param utpPacket packet
     */
    public boolean isPacketExpected(UtpPacket utpPacket) {
        int seqNumberFromPacket = utpPacket.getSequenceNumber() & 0xFFFF;
        return getExpectedSeqNr() == seqNumberFromPacket;
    }

    private int getExpectedSeqNr() {
        int ackNumber = getAckNumber();
        if (ackNumber == UnsignedTypesUtil.MAX_USHORT) {
            UTPSocketChannelImplLoggerKt.getLogger().debug("getExpectedSeqNr returning instead of " + ackNumber + 1 + ": 1");
            return 1;
        }
        return ackNumber + 1;
    }

    private boolean hasSkippedPackets() {
        return !skippedBuffer.isEmpty();
    }

    public void graceFullInterrupt() {
        this.graceFullInterrupt = true;
    }

    private boolean continueReading() {
        return !graceFullInterrupt && !exceptionOccured
            && (!gotLastPacket || hasSkippedPackets() || !timeAwaitedAfterLastPacket());
    }

    private boolean timeAwaitedAfterLastPacket() {
        return (timeStamper.timeStamp() - lastPacketTimestamp) > UtpAlgConfiguration.TIME_WAIT_AFTER_LAST_PACKET
            && gotLastPacket;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public MicroSecondsTimeStamp getTimestamp() {
        return timeStamper;
    }

    public void setTimestamp(MicroSecondsTimeStamp timestamp) {
        this.timeStamper = timestamp;
    }


































































    private void handleStatePacket(DatagramPacket udpPacket) {
        UtpPacket utpPacket = extractUtpPacket(udpPacket);
        writingQueue.offer(new UtpTimestampedPacketDTO(udpPacket, utpPacket, timeStamper.timeStamp(), timeStamper.utpTimeStamp()));
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleStatePacket, seq=" + utpPacket.getSequenceNumber() + ", ack=" + utpPacket.getAckNumber());
    }

    /**
     * Sends an ack.
     *
     * @param utpPacket           the packet that should be acked
     * @param timestampDifference timestamp difference for tha ack packet
     * @param windowSize          the remaining buffer size.
     * @throws IOException
     */
    public void ackPacket(UtpPacket utpPacket, int timestampDifference,
                          long windowSize) throws IOException {
        UtpPacket ackPacket = createAckPacket(utpPacket, timestampDifference,
            windowSize);
        UTPSocketChannelImplLoggerKt.getLogger().debug("ackPacket: " + ackPacket.getAckNumber());
        sendPacket(ackPacket);
    }

    /**
     * setting up a random sequence number.
     */
    public void setupRandomSeqNumber() {
        Random rnd = new Random();
        int max = (int) (MAX_USHORT - 1);
        int rndInt = rnd.nextInt(max);
        setSequenceNumber(rndInt);

    }

    private void handleIncomingConnectionRequest(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("handleIncomingConnectionRequest");
        UtpPacket utpPacket = extractUtpPacket(udpPacket);

        lastPayloadLength = UtpAlgConfiguration.MAX_PACKET_SIZE;
        this.startReadingTimeStamp = timeStamper.timeStamp();

        if (acceptSyn(udpPacket)) {
            int timeStamp = timeStamper.utpTimeStamp();
            setRemoteAddress(udpPacket.getSocketAddress());
            setConnectionIdsFromPacket(utpPacket);
            setupRandomSeqNumber();
            setAckNrFromPacketSqNr(utpPacket);
            int timestampDifference = timeStamper.utpDifference(timeStamp,
                utpPacket.getTimestamp());
            UtpPacket ackPacket = createAckPacket(utpPacket,
                timestampDifference,
                UtpAlgConfiguration.MAX_PACKET_SIZE * 1000);
            sendPacket(ackPacket);
            setState(CONNECTED);
        } else {
//            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private boolean acceptSyn(DatagramPacket udpPacket) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("acceptSyn");
        UtpPacket pkt = extractUtpPacket(udpPacket);
        return getState() == CLOSED
            || (getState() == CONNECTED && isSameAddressAndId(
            pkt.getConnectionId(), udpPacket.getSocketAddress()));
    }

    @Override
    protected void setAckNrFromPacketSqNr(UtpPacket utpPacket) {
        short ackNumberS = utpPacket.getSequenceNumber();
        UTPSocketChannelImplLoggerKt.getLogger().debug("set Ack number 3 to " + (ackNumberS) + ", " + (ackNumberS & 0xFFFF));
        setAckNumber(ackNumberS & 0xFFFF);
    }

    private void setConnectionIdsFromPacket(UtpPacket utpPacket) {
        short connId = (short) utpPacket.getConnectionId();
        int connIdSender = (connId & 0xFFFF);
        int connIdRec = (connId & 0xFFFF) + 1;
        setConnectionIdSending(connIdSender);
        setConnectionIdReceiving(connIdRec);

    }

    @Override
    protected void abortImpl() {
    }

    @Override
    public UtpWriteFuture write(ByteBuffer src) {
        UtpWriteFutureImpl future = null;
        try {
            future = new UtpWriteFutureImpl();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        writer = new UtpWritingRunnable(this, src, timeStamper, future);
        writer.start();
        return future;
    }

    public /*BlockingQueue<UtpTimestampedPacketDTO>*/MyQueue getReadingQueue() {
        return readingQueue;
    }

//    public void resetReadQueue() {
//        readingQueue = new LinkedBlockingQueue<>();
//    }

    public /*BlockingQueue<UtpTimestampedPacketDTO>*/MyQueue getWritingQueue() {
        return writingQueue;
    }

    /**
     * Returns a Data packet with specified header fields already set.
     *
     * @return
     */
    public UtpPacket getNextDataPacket() {
        return createDataPacket();
    }

    /**
     * Returns predefined fin packet
     *
     * @return fin packet.
     */
    public UtpPacket getFinPacket() {
        UtpPacket fin = createDataPacket();
        fin.setTypeVersion(FIN);
        return fin;
    }

    @Override
    public UtpReadFutureImpl read(Consumer<byte[]> onFileReceived) {
        UTPSocketChannelImplLoggerKt.getLogger().debug("UtpReadingRunnable creation 1");
        try {
            readFuture = new UtpReadFutureImpl(onFileReceived);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (reader == null) {
            UTPSocketChannelImplLoggerKt.getLogger().debug("UtpReadingRunnable already running: false");
        } else {
            UTPSocketChannelImplLoggerKt.getLogger().debug("UtpReadingRunnable already running: " + reader.isRunning());
        }
//        reader = new UtpReadingRunnable(this, timeStamper, readFuture);
//        reader.start();
        UTPSocketChannelImplLoggerKt.getLogger().debug("UtpReadingRunnable creation 2");
        return readFuture;
    }

    /**
     * Creates an Selective Ack packet
     *
     * @param headerExtension     the header extension where the SACK data is stored
     * @param timestampDifference timestamp difference for the ack pcket.
     * @param advertisedWindow    remaining buffer size.
     * @throws IOException
     */
    public void selectiveAckPacket(SelectiveAckHeaderExtension headerExtension,
                                   int timestampDifference, long advertisedWindow) throws IOException {
        UtpPacket sack = createSelectiveAckPacket(headerExtension, timestampDifference, advertisedWindow);
        UTPSocketChannelImplLoggerKt.getLogger().debug("selectiveAckPacket: " + sack.getAckNumber());
        sendPacket(sack);
    }

    private UtpPacket createSelectiveAckPacket(SelectiveAckHeaderExtension headerExtension,
                                               int timestampDifference, long advertisedWindow) {
        UtpPacket ackPacket = new UtpPacket();
        ackPacket.setAckNumber(longToUshort(getAckNumber()));
        ackPacket.setTimestampDifference(timestampDifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdSending()));
        ackPacket.setWindowSize(longToUint(advertisedWindow));

        ackPacket.setFirstExtension(SELECTIVE_ACK);
        UtpHeaderExtension[] extensions = {headerExtension};
        ackPacket.setExtensions(extensions);
        ackPacket.setTypeVersion(STATE);

        return ackPacket;
    }

    @Override
    public UtpSocketState getState() {
        return state;
    }

    @Override
    public void setState(UtpSocketState state) {
        this.state = state;
    }

    @Override
    public UtpCloseFuture close() {
        abortImpl();
        if (isReading()) {
            reader.graceFullInterrupt();
        }
        if (isWriting()) {
            writer.graceFullInterrupt();
        }
        return null;
    }

    @Override
    public boolean isReading() {
        return (reader != null && reader.isRunning());
    }

    @Override
    public boolean isWriting() {
        return (writer != null && writer.isRunning());
    }

    public void ackAlreadyAcked(SelectiveAckHeaderExtension extension, int timestampDifference,
                                long windowSize) throws IOException {
        UtpPacket ackPacket = new UtpPacket();
        ackPacket.setAckNumber(longToUshort(getAckNumber()));
        SelectiveAckHeaderExtension[] extensions = {extension};
        ackPacket.setExtensions(extensions);
        ackPacket.setTimestampDifference(timestampDifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdSending()));
        ackPacket.setTypeVersion(STATE);
        ackPacket.setWindowSize(longToUint(windowSize));
        sendPacket(ackPacket);

    }

    @Override
    public void sendPacket(DatagramPacket packet) {
        synchronized (sendLock) {
            UTPSocketBinder.send(getDgSocket(), packet);
        }
    }

    /* general method to send a packet, will be wrapped by a UDP Packet */
    @Override
    public void sendPacket(UtpPacket packet) {
        if (packet != null) {
            byte[] utpPacketBytes = packet.toByteArray();
            int length = packet.getPacketLength();
            DatagramPacket pkt = new DatagramPacket(utpPacketBytes, length,
                getRemoteAdress());
            sendPacket(pkt);
        }
    }

    @Override
    public void setDgSocket(DatagramSocket dgSocket) {
        if (this.dgSocket != null) {
            this.dgSocket.close();
        }
        this.dgSocket = dgSocket;
    }

    @Override
    public void setAckNumber(int ackNumber) {
        this.ackNumber = ackNumber;
    }

    @Override
    public void setRemoteAddress(SocketAddress remoteAdress) {
        this.remoteAddress = remoteAdress;
    }

    public void returnFromReading() {
        reader = null;
        //TODO: dispatch:
        if (!isWriting()) {
            this.state = UtpSocketState.CONNECTED;
        } else {

        }
    }

    public void removeWriter() {
        writer = null;
    }

    /*
     * Start a connection time out counter which will frequently resend the syn packet.
     */
    @Override
    protected void startConnectionTimeOutCounter(UtpPacket synPacket) {
        retryConnectionTimeScheduler = Executors
            .newSingleThreadScheduledExecutor();
        ConnectionTimeOutRunnable runnable = new ConnectionTimeOutRunnable(
            synPacket, this, stateLock);
        // retryConnectionTimeScheduler.schedule(runnable, 2, TimeUnit.SECONDS);
        retryConnectionTimeScheduler.scheduleWithFixedDelay(runnable,
            UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
            UtpAlgConfiguration.CONNECTION_ATTEMPT_INTERVALL_MILLIS,
            TimeUnit.MILLISECONDS);
    }

    /**
     * Called by the time out counter {@see ConnectionTimeOutRunnable}
     *
     * @param exp, is optional.
     */
    public void connectionFailed(IOException exp) {
        setSequenceNumber(DEF_SEQ_START);
        setRemoteAddress(null);
        abortImpl();
        setState(CLOSED);
        retryConnectionTimeScheduler.shutdown();
        retryConnectionTimeScheduler = null;
        connectFuture.finished(exp);

    }

    /**
     * Resends syn packet. called by {@see ConnectionTimeOutRunnable}
     *
     * @param synPacket
     */
    public void resendSynPacket(UtpPacket synPacket) {
        stateLock.lock();
        try {
            int attempts = getConnectionAttempts();
            if (getState() == UtpSocketState.SYN_SENT) {
                if (attempts < UtpAlgConfiguration.MAX_CONNECTION_ATTEMPTS) {
                    incrementConnectionAttempts();
                    sendPacket(synPacket);
                } else {
                    connectionFailed(new SocketTimeoutException());
                }
            }
        } finally {
            stateLock.unlock();
        }

    }

    public void setTimetamper(MicroSecondsTimeStamp stamp) {
        this.timeStamper = stamp;

    }

    /*
     * Creates an ACK packet.
     */
    protected UtpPacket createAckPacket(UtpPacket pkt, int timedifference,
                                        long advertisedWindow) {
        UtpPacket ackPacket = new UtpPacket();
        if (pkt.getTypeVersion() != FIN) {
            setAckNrFromPacketSqNr(pkt);
        }
        ackPacket.setAckNumber(longToUshort(getAckNumber()));

        ackPacket.setTimestampDifference(timedifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdSending()));
        ackPacket.setTypeVersion(STATE);
        ackPacket.setWindowSize(longToUint(advertisedWindow));
        return ackPacket;
    }

    protected UtpPacket createDataPacket() {
        UtpPacket pkt = new UtpPacket();
        pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
        incrementSequenceNumber();
        pkt.setAckNumber(longToUshort(getAckNumber()));
        pkt.setConnectionId(longToUshort(getConnectionIdSending()));
        pkt.setTimestamp(timeStamper.utpTimeStamp());
        pkt.setTypeVersion(UtpPacketUtils.DATA);
        return pkt;
    }

}
