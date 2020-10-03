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
package nl.tudelft.ipv8.messaging.utp.channels.impl.alg;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpTimestampedPacketDTO;
import nl.tudelft.ipv8.messaging.utp.channels.impl.write.UTPWritingRunnableLoggerKt;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.SelectiveAckHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpHeaderExtension;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils;
import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;

import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.C_CONTROL_TARGET_MICROS;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MAX_BURST_SEND;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MAX_PACKET_SIZE;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MICROSECOND_WAIT_BETWEEN_BURSTS;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MINIMUM_MTU;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MINIMUM_TIMEOUT_MILLIS;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.MIN_PACKET_SIZE;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.ONLY_POSITIVE_GAIN;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.PACKET_SIZE_MODE;
import static nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration.SEND_IN_BURST;

public class UtpAlgorithm {
    private int currentWindow = 0;
    private int maxWindow;
    private MinimumDelay minDelay = new MinimumDelay();
    private OutPacketBuffer buffer;
    private MicroSecondsTimeStamp timeStamper;
    private int currentAckPosition = 0;
    private int currentBurstSend = 0;
    private long lastZeroWindow;
    private ByteBuffer bBuffer;

    private long rtt;
    private long rttVar = 0;

    private int advertisedWindowSize;
    private boolean advertisedWindowSizeSet = false;

    private long lastTimeWindowReduced;
    private long timeStampNow;
    private long lastAckReceived;

    private long lastMaxedOutWindow;

    public UtpAlgorithm(MicroSecondsTimeStamp timestamper, SocketAddress addr) {
        maxWindow = MAX_CWND_INCREASE_PACKETS_PER_RTT;
        rtt = MINIMUM_TIMEOUT_MILLIS * 2;
        timeStamper = timestamper;
        buffer = new OutPacketBuffer(timestamper);
        buffer.setRemoteAdress(addr);
        timeStampNow = timeStamper.timeStamp();
    }

    /**
     * handles the acking of the packet.
     *
     * @param pair packet with the meta data.
     */
    public void ackReceived(UtpTimestampedPacketDTO pair) {
        int seqNrToAck = pair.utpPacket().getAckNumber() & 0xFFFF;
        UTPAlgorithmLoggerKt.getLogger().debug("Received ACK " + pair.utpPacket().toString());
        timeStampNow = timeStamper.timeStamp();
        lastAckReceived = timeStampNow;
        int advertisedWindow = pair.utpPacket().getWindowSize();
        updateAdvertisedWindowSize(advertisedWindow);
        int packetSizeJustAcked = buffer.markPacketAcked(seqNrToAck, timeStampNow,
            UtpAlgConfiguration.AUTO_ACK_SMALLER_THAN_ACK_NUMBER);
        if (packetSizeJustAcked > 0) {
            updateRtt(timeStampNow, seqNrToAck);
            updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
        }
        // TODO: With libutp, sometimes null pointer exception -> investigate.
        UTPAlgorithmLoggerKt.getLogger().debug("utpPacket With Ext: " + pair.utpPacket().toString());
        SelectiveAckHeaderExtension selectiveAckExtension = findSelectiveAckExtension(pair.utpPacket());
        if (selectiveAckExtension != null) {

            // if a new packed is acked by selectiveAck, we will
            // only update this one. if more than one is acked newly,
            // ignore it, because it will corrupt our measurements
            boolean windowAlreadyUpdated = false;

            // For each byte in the selective Ack header extension
            byte[] bitMask = selectiveAckExtension.getBitMask();
            for (int i = 0; i < bitMask.length; i++) {
                // each bit in the extension, from 2 to 9, because least significant
                // bit is ACK+2, most significant bit is ack+9 -> loop [2,9]
                for (int j = 2; j < 10; j++) {
                    if (SelectiveAckHeaderExtension.isBitMarked(bitMask[i], j)) {
                        // j-th bit of i-th byte + seqNrToAck equals our selective-Ack-number.
                        // example:
                        // ack:5, sack: 8 -> i = 0, j =3 -> 0*8+3+5 = 8.
                        // bitpattern in this case would be 00000010, bit_index 1 from right side, added 2 to it equals 3
                        // thats why we start with j=2. most significant bit is index 7, j would be 9 then.
                        int sackSeqNr = i * 8 + j + seqNrToAck;
                        // sackSeqNr can overflow too !!
                        if (sackSeqNr > UnsignedTypesUtil.MAX_USHORT) {
                            sackSeqNr -= UnsignedTypesUtil.MAX_USHORT;
                        }
                        // dont ack smaller seq numbers in case of Selective ack !!!!!
                        packetSizeJustAcked = buffer.markPacketAcked(sackSeqNr, timeStampNow, false);
                        if (packetSizeJustAcked > 0 && !windowAlreadyUpdated) {
                            windowAlreadyUpdated = true;
                            updateRtt(timeStampNow, sackSeqNr);
                            updateWindow(pair.utpPacket(), timeStampNow, packetSizeJustAcked, pair.utpTimeStamp());
                        }
                    }
                }
            }
        }
    }

    private void updateRtt(long timestamp, int seqNrToAck) {
        long sendTimeStamp = buffer.getSendTimeStamp(seqNrToAck);
        if (rttUpdateNecessary(sendTimeStamp, seqNrToAck)) {
            long packetRtt = (timestamp - sendTimeStamp) / 1000;
            long delta = rtt - packetRtt;
            rttVar += (Math.abs(delta) - rttVar) / 4;
            rtt += (packetRtt - rtt) / 8;
        }
    }


    private boolean rttUpdateNecessary(long sendTimeStamp, int seqNrToAck) {
        return sendTimeStamp != -1 && buffer.getResendCounter(seqNrToAck) == 0;
    }


    private void updateAdvertisedWindowSize(int advertisedWindo) {
        if (!advertisedWindowSizeSet) {
            advertisedWindowSizeSet = true;
        }
        this.advertisedWindowSize = advertisedWindo;
    }

    private void updateWindow(UtpPacket utpPacket, long timestamp, int packetSizeJustAcked, int utpReceived) {
        currentWindow = buffer.getBytesOnfly();

        if (isWindowFull()) {
            lastMaxedOutWindow = timeStampNow;
        }

        long ourDifference = utpPacket.getTimestampDifference();
        updateOurDelay(ourDifference);

        int theirDifference = timeStamper.utpDifference(utpReceived, utpPacket.getTimestamp());

        updateTheirDelay(theirDifference);
        long ourDelay = ourDifference - minDelay.getCorrectedMinDelay();
        minDelay.addSample(ourDelay);

        long offTarget = C_CONTROL_TARGET_MICROS - ourDelay;
        double delayFactor = ((double) offTarget) / ((double) C_CONTROL_TARGET_MICROS);
        double windowFactor = (Math.min(packetSizeJustAcked, maxWindow)) / (Math.max(maxWindow, packetSizeJustAcked));
        int gain = (int) (MAX_CWND_INCREASE_PACKETS_PER_RTT * delayFactor * windowFactor);

        if (setGainToZero(gain)) {
            gain = 0;
        }

        maxWindow += gain;
        if (maxWindow < 0) {
            maxWindow = 0;
        }

        buffer.setResendtimeOutMicros(getTimeOutMicros());

        if (maxWindow == 0) {
            lastZeroWindow = timeStampNow;
        }
        // get bytes successfully transmitted:
        // this is the position of the bytebuffer (comes from programmer)
        // substracted by the amount of bytes on fly (these are not yet acked)
        int bytesSend = bBuffer.position() - buffer.getBytesOnfly();
    }

    private boolean setGainToZero(int gain) {
        // if i have ever reached lastMaxWindow then check if its longer than 1kk micros
        // if not, true
        boolean lastMaxWindowNeverReached = lastMaxedOutWindow == 0 || (lastMaxedOutWindow - timeStampNow >= UtpAlgConfiguration.MINIMUM_DELTA_TO_MAX_WINDOW_MICROS);
        return (ONLY_POSITIVE_GAIN && gain < 0) || lastMaxWindowNeverReached;
    }


    private void updateTheirDelay(long theirDifference) {
        minDelay.updateTheirDelay(theirDifference, timeStampNow);
    }


    private long getTimeOutMicros() {
        return Math.max(getEstimatedRttMicros(), MINIMUM_TIMEOUT_MILLIS * 1000);
    }

    private long getEstimatedRttMicros() {
        return rtt * 1000 + rttVar * 4 * 1000;
    }


    private void updateOurDelay(long difference) {
        minDelay.updateOurDelay(difference, timeStampNow);
    }

    /**
     * Checks if packets must be resend based on the fast resend mechanism or a transmission timeout.
     *
     * @return All packets that must be resend
     */
    public Queue<DatagramPacket> getPacketsToResend() {
        timeStampNow = timeStamper.timeStamp();
        Queue<DatagramPacket> queue = new LinkedList<>();
        Queue<UtpTimestampedPacketDTO> toResend = buffer.getPacketsToResend(UtpAlgConfiguration.MAX_BURST_SEND);
        for (UtpTimestampedPacketDTO utpTimestampedPacketDTO : toResend) {
            queue.add(utpTimestampedPacketDTO.dataGram());
            utpTimestampedPacketDTO.incrementResendCounter();
            if (utpTimestampedPacketDTO.reduceWindow()) {
                if (reduceWindowNecessary()) {
                    lastTimeWindowReduced = timeStampNow;
                    maxWindow *= 0.5;
                }
                utpTimestampedPacketDTO.setReduceWindow(false);
            }
        }
        return queue;
    }


    private boolean reduceWindowNecessary() {
        if (lastTimeWindowReduced == 0) {
            return true;
        }

        long delta = timeStampNow - lastTimeWindowReduced;
        return delta > getEstimatedRttMicros();

    }


    private SelectiveAckHeaderExtension findSelectiveAckExtension(
        UtpPacket utpPacket) {
        UtpHeaderExtension[] extensions = utpPacket.getExtensions();
        if (extensions == null) {
            return null;
        }
        for (UtpHeaderExtension extension : extensions) {
            if (extension instanceof SelectiveAckHeaderExtension) {
                return (SelectiveAckHeaderExtension) extension;
            }
        }
        return null;
    }


    /**
     * Returns true if a packet can NOW be send
     */
    public boolean canSendNextPacket() {
        if (timeStampNow - lastZeroWindow > getTimeOutMicros() && lastZeroWindow != 0 && maxWindow == 0) {
            maxWindow = MAX_PACKET_SIZE;
        }
        boolean windowNotFull = !isWindowFull();
        boolean burstFull = false;

        if (windowNotFull) {
            burstFull = isBurstFull();
        }

        if (!burstFull && windowNotFull) {
            currentBurstSend++;
        }

        if (burstFull) {
            currentBurstSend = 0;
        }
        UTPWritingRunnableLoggerKt.getLogger().debug("canSendNextPacket: " + !burstFull + ", " + windowNotFull + ", " + ((advertisedWindowSize < maxWindow && advertisedWindowSizeSet) ? advertisedWindowSize : maxWindow) + ", " + currentWindow);
        return SEND_IN_BURST ? (!burstFull && windowNotFull) : windowNotFull;
    }

    private boolean isBurstFull() {
        return currentBurstSend >= MAX_BURST_SEND;
    }

    private boolean isWindowFull() {
        int maximumWindow = (advertisedWindowSize < maxWindow && advertisedWindowSizeSet) ? advertisedWindowSize : maxWindow;
        return currentWindow >= maximumWindow;
    }

    /**
     * Returns the size of the next packet, depending on {@see PacketSizeModus}
     *
     * @return bytes.
     */
    public int sizeOfNextPacket() {
        if (PACKET_SIZE_MODE.equals(PacketSizeModus.DYNAMIC_LINEAR)) {
            return calculateDynamicLinearPacketSize();
        } else if (PACKET_SIZE_MODE.equals(PacketSizeModus.CONSTANT_1472)) {
            return MAX_PACKET_SIZE - UtpPacketUtils.DEF_HEADER_LENGTH - 1;
        }
        return MINIMUM_MTU - UtpPacketUtils.DEF_HEADER_LENGTH;
    }

    private int calculateDynamicLinearPacketSize() {
        int packetSizeDelta = MAX_PACKET_SIZE - MIN_PACKET_SIZE - 1;
        long minDelayOffTarget = C_CONTROL_TARGET_MICROS - minDelay.getRecentAverageDelay();
        minDelayOffTarget = minDelayOffTarget < 0 ? 0 : minDelayOffTarget;
        double packetSizeFactor = ((double) minDelayOffTarget) / ((double) C_CONTROL_TARGET_MICROS);
        double packetSize = MIN_PACKET_SIZE + packetSizeFactor * packetSizeDelta;
        return (int) Math.ceil(packetSize);
    }


    /**
     * Inform the algorithm that this packet just was send
     *
     * @param utpPacket utp packet version
     * @param dgPacket  Datagram of first parameter.
     */
    public void markPacketOnfly(UtpPacket utpPacket, DatagramPacket dgPacket) {
        timeStampNow = timeStamper.timeStamp();
        UtpTimestampedPacketDTO pkt = new UtpTimestampedPacketDTO(dgPacket, utpPacket, timeStampNow, 0);
        buffer.bufferPacket(pkt);
        incrementAckNumber();
        addPacketToCurrentWindow(utpPacket);
    }

    private void incrementAckNumber() {
        if (currentAckPosition == UnsignedTypesUtil.MAX_USHORT) {
            currentAckPosition = 1;
        } else {
            currentAckPosition++;
        }
    }

    private void addPacketToCurrentWindow(UtpPacket pkt) {
        currentWindow += UtpPacketUtils.DEF_HEADER_LENGTH;
        if (pkt.getPayload() != null) {
            currentWindow += pkt.getPayload().length;
        }
    }


    public boolean areAllPacketsAcked() {
        return buffer.isEmpty();
    }

    public void setTimeStamper(MicroSecondsTimeStamp timeStamper) {
        this.timeStamper = timeStamper;

    }

    /**
     * sets the current ack position based on the sequence number
     */
    public void initiateAckPosition(int sequenceNumber) {
        if (sequenceNumber == 0) {
            throw new IllegalArgumentException("sequence number cannot be 0");
        }
        if (sequenceNumber == 1) {
            currentAckPosition = (int) UnsignedTypesUtil.MAX_USHORT;
        } else {
            currentAckPosition = sequenceNumber - 1;
        }
    }

    public void removeAcked() {
        buffer.removeAcked();
        currentWindow = buffer.getBytesOnfly();
    }

    /**
     * Returns the number of micro seconds the writing thread should wait at most based on: timed out packets and window utilisation
     *
     * @return micro seconds.
     */
    public long getWaitingTimeMicroSeconds() {
        long oldestTimeStamp = buffer.getOldestUnackedTimestamp();
        long nextTimeOut = oldestTimeStamp + getTimeOutMicros();
        timeStampNow = timeStamper.timeStamp();
        long timeOutInMicroSeconds = nextTimeOut - timeStampNow;
        if (continueImmediately(timeOutInMicroSeconds, oldestTimeStamp)) {
            return 0L;
        }
        if (!isWindowFull() || maxWindow == 0) {
            return MICROSECOND_WAIT_BETWEEN_BURSTS;
        }
        return timeOutInMicroSeconds;
    }

    private boolean continueImmediately(long timeOutInMicroSeconds, long oldestTimeStamp) {
        return timeOutInMicroSeconds < 0 && (oldestTimeStamp != 0);
    }

    /**
     * terminates.
     */
    public void end(int bytesSend, boolean successfull) {
        if (successfull) {
//            log.debug("Total packets send: " + totalPackets + ", Total Packets Resend: " + resendedPackets);
        }
    }

    /**
     * returns true when a socket timeout happened. (the receiver does not answer anymore)
     */
    public boolean isTimedOut() {
        if (timeStampNow - lastAckReceived > getTimeOutMicros() * 5 && lastAckReceived != 0) {
            throw new RuntimeException("TIMED OUT!");
        }
        return false;
    }

    public void setByteBuffer(ByteBuffer bBuffer) {
        this.bBuffer = bBuffer;
    }
}
