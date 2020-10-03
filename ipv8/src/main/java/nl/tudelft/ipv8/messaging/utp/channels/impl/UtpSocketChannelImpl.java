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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
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
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.UtpReadFutureImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.read.UtpReadingRunnable;
import nl.tudelft.ipv8.messaging.utp.channels.impl.write.UtpWriteFutureImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.write.UtpWritingRunnable;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.SelectiveAckHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils;

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

    private volatile BlockingQueue<UtpTimestampedPacketDTO> queue = new LinkedBlockingQueue<>();

    private UtpWritingRunnable writer;
    private UtpReadingRunnable reader;
    private final Object sendLock = new Object();

    private ScheduledExecutorService retryConnectionTimeScheduler;
    private int connectionAttempts = 0;

    public UtpSocketChannelImpl() {
    }

    /*
     * Handles packet.
     */
    @Override
    public void receivePacket(DatagramPacket udpPacket) {
        UTPSocketChannelLoggerKt.getLogger().debug("receivePacket");
        if (isSynAckPacket(udpPacket)) {
            handleSynAckPacket(udpPacket);
        } else if (isResetPacket(udpPacket)) {
            handleResetPacket(udpPacket);
        } else if (isSynPkt(udpPacket)) {
            handleIncomingConnectionRequest(udpPacket);
        } else if (isDataPacket(udpPacket)) {
            handlePacket(udpPacket);
        } else if (isStatePacket(udpPacket)) {
            handlePacket(udpPacket);
        } else if (isFinPacket(udpPacket)) {
            handleFinPacket(udpPacket);
        } else {
//            sendResetPacket(udpPacket.getSocketAddress());
        }
    }

    private void handleFinPacket(DatagramPacket udpPacket) {
        UTPSocketChannelLoggerKt.getLogger().debug("handleFinPacket");
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
        UTPSocketChannelLoggerKt.getLogger().debug("handleResetPacket");
        this.close();
    }

    private boolean isSynAckPacket(DatagramPacket udpPacket) {
        return isStatePacket(udpPacket) && getState() == UtpSocketState.SYN_SENT;
    }

    private void handleSynAckPacket(DatagramPacket udpPacket) {
        UTPSocketChannelLoggerKt.getLogger().debug("handleSyncAckPacket");
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

    private void handlePacket(DatagramPacket udpPacket) {
        UtpPacket utpPacket = extractUtpPacket(udpPacket);
        queue.offer(new UtpTimestampedPacketDTO(udpPacket, utpPacket, timeStamper.timeStamp(), timeStamper.utpTimeStamp()));
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
        UTPSocketChannelLoggerKt.getLogger().debug("handleIncomingConnectionRequest");
        UtpPacket utpPacket = extractUtpPacket(udpPacket);

        if (acceptSyn(udpPacket)) {
            int timeStamp = timeStamper.utpTimeStamp();
            setRemoteAddress(udpPacket.getSocketAddress());
            setConnectionIdsFromPacket(utpPacket);
            setupRandomSeqNumber();
            setAckNrFromPacketSqNr(utpPacket);
            printState("[Syn received] ");
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
        UTPSocketChannelLoggerKt.getLogger().debug("acceptSyn");
        UtpPacket pkt = extractUtpPacket(udpPacket);
        return getState() == CLOSED
            || (getState() == CONNECTED && isSameAddressAndId(
            pkt.getConnectionId(), udpPacket.getSocketAddress()));
    }

    @Override
    protected void setAckNrFromPacketSqNr(UtpPacket utpPacket) {
        short ackNumberS = utpPacket.getSequenceNumber();
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

    public BlockingQueue<UtpTimestampedPacketDTO> getDataGramQueue() {
        return queue;
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
        UtpReadFutureImpl readFuture = null;
        try {
            readFuture = new UtpReadFutureImpl(onFileReceived);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        reader = new UtpReadingRunnable(this, timeStamper, readFuture);
        reader.start();
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
        sendPacket(sack);
    }

    private UtpPacket createSelectiveAckPacket(SelectiveAckHeaderExtension headerExtension,
                                               int timestampDifference, long advertisedWindow) {
        UtpPacket ackPacket = new UtpPacket();
        ackPacket.setAckNumber(longToUshort(getAckNumber()));
        ackPacket.setTimestampDifference(timestampDifference);
        ackPacket.setTimestamp(timeStamper.utpTimeStamp());
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
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
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
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
        ackPacket.setConnectionId(longToUshort(getConnectionIdsending()));
        ackPacket.setTypeVersion(STATE);
        ackPacket.setWindowSize(longToUint(advertisedWindow));
        return ackPacket;
    }

    protected UtpPacket createDataPacket() {
        UtpPacket pkt = new UtpPacket();
        pkt.setSequenceNumber(longToUshort(getSequenceNumber()));
        incrementSequenceNumber();
        pkt.setAckNumber(longToUshort(getAckNumber()));
        pkt.setConnectionId(longToUshort(getConnectionIdsending()));
        pkt.setTimestamp(timeStamper.utpTimeStamp());
        pkt.setTypeVersion(UtpPacketUtils.DATA);
        return pkt;
    }

}
