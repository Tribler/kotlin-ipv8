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
package nl.tudelft.ipv8.messaging.utp.channels.impl.write;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import nl.tudelft.ipv8.messaging.utp.channels.impl.MyQueue;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpTimestampedPacketDTO;
import nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgorithm;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;

import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.extractUtpPacket;

/**
 * Handles the writing job of a channel...
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class UtpWritingRunnable extends Thread implements Runnable {

    private ByteBuffer buffer;
    private volatile boolean graceFullInterrupt;
    private UtpSocketChannelImpl channel;
    private boolean isRunning = false;
    private UtpAlgorithm algorithm;
    private IOException possibleException = null;
    private MicroSecondsTimeStamp timeStamper;
    private UtpWriteFutureImpl future;

    public UtpWritingRunnable(UtpSocketChannelImpl channel, ByteBuffer buffer, MicroSecondsTimeStamp timeStamper, UtpWriteFutureImpl future) {
        this.buffer = buffer;
        this.channel = channel;
        this.timeStamper = timeStamper;
        this.future = future;
        algorithm = new UtpAlgorithm(timeStamper, channel.getRemoteAdress());
    }

    @Override
    public void run() {
        UTPWritingRunnableLoggerKt.getLogger().debug("Starting sending");
        algorithm.initiateAckPosition(channel.getSequenceNumber());
        algorithm.setTimeStamper(timeStamper);
        algorithm.setByteBuffer(buffer);
        isRunning = true;
        IOException possibleExp = null;
        boolean exceptionOccurred = false;
//        buffer.flip();
        while (continueSending()) {
            UTPWritingRunnableLoggerKt.getLogger().debug("New iteration: " + buffer.position());
            if (!checkForAcks()) {
                UTPWritingRunnableLoggerKt.getLogger().debug("No acks");
                graceFullInterrupt = true;
                break;
            }
            UTPWritingRunnableLoggerKt.getLogger().debug("Acks found");

            Queue<DatagramPacket> packetsToResend = algorithm.getPacketsToResend();
            for (DatagramPacket datagramPacket : packetsToResend) {
                datagramPacket.setSocketAddress(channel.getRemoteAdress());
                channel.sendPacket(datagramPacket);
                UTPWritingRunnableLoggerKt.getLogger().debug("Resent packet: " + extractUtpPacket(datagramPacket).getSequenceNumber());
            }

            if (algorithm.isTimedOut()) {
                UTPWritingRunnableLoggerKt.getLogger().debug("Timed out");
                graceFullInterrupt = true;
                possibleExp = new IOException("timed out");
                exceptionOccurred = true;
            }
//			if(!checkForAcks()) {
//				graceFullInterrupt = true;
//				break;
//			}
//            UTPWritingRunnableLoggerKt.getLogger().debug("Almost sending: " + algorithm.canSendNextPacket() + ", " + !exceptionOccurred + ", " + !graceFullInterrupt + ", " + buffer.hasRemaining());
            while (algorithm.canSendNextPacket() && !exceptionOccurred && !graceFullInterrupt && buffer.hasRemaining()) {
                try {
                    DatagramPacket packet = getNextPacket();
                    channel.sendPacket(packet);
                } catch (IOException exp) {
                    UTPWritingRunnableLoggerKt.getLogger().debug("Exception 2");
                    exp.printStackTrace();
                    graceFullInterrupt = true;
                    possibleExp = exp;
                    exceptionOccurred = true;
                    break;
                }
            }
            updateFuture();
        }

        if (possibleExp != null) {
            exceptionOccurred(possibleExp);
        }
        isRunning = false;
        algorithm.end(buffer.position(), !exceptionOccurred);
        future.finished(possibleExp, buffer.position());
        UTPWritingRunnableLoggerKt.getLogger().debug("WRITER OUT");
        channel.removeWriter();
    }

    private void updateFuture() {
        future.setBytesSend(buffer.position());
    }


    private boolean checkForAcks() {
        /*BlockingQueue<UtpTimestampedPacketDTO>*/
        MyQueue queue = channel.getWritingQueue();
        try {
            waitAndProcessAcks(queue);
        } catch (InterruptedException ie) {
            return false;
        }
        return true;
    }

    private void waitAndProcessAcks(/*BlockingQueue<UtpTimestampedPacketDTO>*/MyQueue queue) throws InterruptedException {
        long waitingTimeMicros = algorithm.getWaitingTimeMicroSeconds();
        UtpTimestampedPacketDTO temp = queue.poll(waitingTimeMicros, TimeUnit.MICROSECONDS);
        if (temp != null) {
            algorithm.ackReceived(temp);
            algorithm.removeAcked();
            if (queue.peek() != null) {
                processAcks(queue);
            }
        }
    }

    private void processAcks(/*BlockingQueue<UtpTimestampedPacketDTO>*/MyQueue queue) {
        UtpTimestampedPacketDTO pair;
        while ((pair = queue.poll()) != null) {
            algorithm.ackReceived(pair);
            algorithm.removeAcked();
        }
    }

    private DatagramPacket getNextPacket() throws IOException {
        int packetSize = algorithm.sizeOfNextPacket();
        int remainingBytes = buffer.remaining();

        if (remainingBytes < packetSize) {
            packetSize = remainingBytes;
        }

        byte[] payload = new byte[packetSize];
        buffer.get(payload);
        UtpPacket utpPacket = channel.getNextDataPacket();
        utpPacket.setPayload(payload);

        int leftInBuffer = buffer.remaining();
        utpPacket.setWindowSize(leftInBuffer);
        byte[] utpPacketBytes = utpPacket.toByteArray();
        UTPWritingRunnableLoggerKt.getLogger().debug("Sending next packet: " + utpPacket.getSequenceNumber());
        DatagramPacket udpPacket = new DatagramPacket(utpPacketBytes, utpPacketBytes.length, channel.getRemoteAdress());
        algorithm.markPacketOnfly(utpPacket, udpPacket);
        return udpPacket;
    }


    private void exceptionOccurred(IOException exp) {
        possibleException = exp;
    }

    public IOException getException() {
        return possibleException;
    }

    private boolean continueSending() {
        return !graceFullInterrupt && !allPacketsAckedSendAndAcked();
    }

    private boolean allPacketsAckedSendAndAcked() {
//		return finSend && algorithm.areAllPacketsAcked() && !buffer.hasRemaining();
        return algorithm.areAllPacketsAcked() && !buffer.hasRemaining();
    }


    public void graceFullInterrupt() {
        UTPWritingRunnableLoggerKt.getLogger().debug("GraceFullInterrupt()");
        graceFullInterrupt = true;
        throw new RuntimeException("GraceFullInterrupt error");
    }

    public boolean isRunning() {
        return isRunning;
    }
}
