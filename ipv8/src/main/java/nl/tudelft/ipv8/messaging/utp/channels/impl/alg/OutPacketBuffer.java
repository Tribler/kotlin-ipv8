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
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpTimestampedPacketDTO;
import nl.tudelft.ipv8.messaging.utp.data.MicroSecondsTimeStamp;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils;
import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;

/*Logging*/

/**
 * Out buffer that handles outgoing packets.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class OutPacketBuffer {

    private static int size = 3000;
    private ArrayList<UtpTimestampedPacketDTO> buffer = new ArrayList<>(size);
    private int bytesOnFly = 0;
    private long resendTimeOutMicros;
    private MicroSecondsTimeStamp timeStamper;
    private SocketAddress addr;
    private long currentTime;
    public OutPacketBuffer(MicroSecondsTimeStamp stamper) {
        timeStamper = stamper;
    }

    public void setResendtimeOutMicros(long timeOutMicroSec) {
        this.resendTimeOutMicros = timeOutMicroSec;
    }

    /**
     * Puts a packet in the buffer.
     */
    public void bufferPacket(UtpTimestampedPacketDTO pkt) {
        buffer.add(pkt);
        if (pkt.utpPacket().getPayload() != null) {
            bytesOnFly += pkt.utpPacket().getPayload().length;
        }
        bytesOnFly += UtpPacketUtils.DEF_HEADER_LENGTH;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /**
     * Used to tell the buffer that packet was acked
     *
     * @param seqNrToAck            the sequence number that has been acked
     * @param timestamp             now time stamp
     * @param ackSmallerThanThisSeq if true, ack all packets lower than this sequence number, if false, only ack this sequence number.
     * @return bytes acked. negative there was no packed with that sequence number.
     */
    public int markPacketAcked(int seqNrToAck, long timestamp, boolean ackSmallerThanThisSeq) {
        int bytesJustAcked = -1;
        UtpTimestampedPacketDTO pkt = findPacket(seqNrToAck);
        if (pkt != null) {
            if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
                if (!pkt.isPacketAcked()) {
                    int payloadLength = pkt.utpPacket().getPayload() == null ? 0
                        : pkt.utpPacket().getPayload().length;
                    bytesJustAcked = payloadLength
                        + UtpPacketUtils.DEF_HEADER_LENGTH;
                }
                pkt.setPacketAcked(true);
                if (ackSmallerThanThisSeq) {
                    for (UtpTimestampedPacketDTO toAck : buffer) {
                        if ((toAck.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
                            break;
                        } else {
                            toAck.setPacketAcked(true);
                        }
                    }
                }
            } else {
                throw new RuntimeException("ERROR FOUND WRONG SEQ NR");
            }
        }
        return bytesJustAcked;
    }

    private UtpTimestampedPacketDTO findPacket(int seqNrToAck) {

        if (!buffer.isEmpty()) {
            int firstSeqNr = buffer.get(0).utpPacket().getSequenceNumber() & 0xFFFF;
            int index = seqNrToAck - firstSeqNr;
            if (index < 0) {
                // overflow in seq nr
                index += UnsignedTypesUtil.MAX_USHORT;
            }

            if (index < buffer.size()
                && (buffer.get(index).utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
                return buffer.get(index);
            } else {
                // bug -> search sequentially until fixed
                for (int i = 0; i < buffer.size(); i++) {
                    UtpTimestampedPacketDTO pkt = buffer.get(i);
                    if ((pkt.utpPacket().getSequenceNumber() & 0xFFFF) == seqNrToAck) {
                        return pkt;
                    }
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Removes all acked packets up to the first unacked packet.
     */
    public void removeAcked() {
        ArrayList<UtpTimestampedPacketDTO> toRemove = new ArrayList<>(size);
        for (UtpTimestampedPacketDTO pkt : buffer) {
            if (pkt.isPacketAcked()) {
                // we got the header, remove it from the bytes that are on the
                // wire
                bytesOnFly -= UtpPacketUtils.DEF_HEADER_LENGTH;
                if (pkt.utpPacket().getPayload() != null) {
                    // in case of a data packet, subtract the payload
                    bytesOnFly -= pkt.utpPacket().getPayload().length;
                }
                toRemove.add(pkt);
            } else {
                break;
            }
        }
        buffer.removeAll(toRemove);
    }

    /**
     * Returns all packets that timed out or that should be resend by fast resend.
     *
     * @param maxResend maximum number of packets to resend.
     * @return Queue with all packets that must be resend.
     * @throws SocketException
     */
    public Queue<UtpTimestampedPacketDTO> getPacketsToResend(int maxResend) {
        currentTime = timeStamper.timeStamp();
        Queue<UtpTimestampedPacketDTO> unacked = new LinkedList<>();
        for (UtpTimestampedPacketDTO pkt : buffer) {
            if (!pkt.isPacketAcked()) {
                unacked.add(pkt);
            } else {
                for (UtpTimestampedPacketDTO unackedPkt : unacked) {
                    unackedPkt.incrementAckedAfterMe();
                }
            }

        }
        Queue<UtpTimestampedPacketDTO> toReturn = new LinkedList<>();

        for (UtpTimestampedPacketDTO unackedPkt : unacked) {
            if (resendRequired(unackedPkt) && toReturn.size() <= maxResend) {
                toReturn.add(unackedPkt);
                updateResendTimeStamps(unackedPkt);
            }
            unackedPkt.setAckedAfterMeCounter(0);
        }

        return toReturn;

    }

    private void updateResendTimeStamps(UtpTimestampedPacketDTO unackedPkt) {
        unackedPkt.utpPacket().setTimestamp(timeStamper.utpTimeStamp());
        byte[] newBytes = unackedPkt.utpPacket().toByteArray();
        // TB: why create new datagram packet, can't it be reused?
        // TODO: ukackedPacket.datagram.getData()[x] = newtimestamp[0]
        // 		 ukackedPacket.datagram.getData()[x + 1] = newtimestamp[1]
        // 		 ukackedPacket.datagram.getData()[x + 2] = newtimestamp[2]
        // 		 ukackedPacket.datagram.getData()[x + 3] = newtimestamp[3]
        unackedPkt.setDgPacket(new DatagramPacket(newBytes, newBytes.length, addr));
        unackedPkt.setStamp(currentTime);
    }

    private boolean resendRequired(UtpTimestampedPacketDTO unackedPkt) {
        boolean fastResend = false;
        if (unackedPkt.getAckedAfterMeCounter() >= UtpAlgConfiguration.MIN_SKIP_PACKET_BEFORE_RESEND) {
            if (!unackedPkt.alreadyResendBecauseSkipped()) {
                fastResend = true;
                unackedPkt.setResendBecauseSkipped(true);
            }
        }
        boolean timedOut = isTimedOut(unackedPkt);

        if (!timedOut && fastResend) {
            unackedPkt.setReduceWindow(false);
        }
        if (timedOut && !unackedPkt.reduceWindow()) {
            unackedPkt.setReduceWindow(true);
        }

        return fastResend || timedOut;
    }

    public int getBytesOnfly() {
        return bytesOnFly;
    }

    private boolean isTimedOut(UtpTimestampedPacketDTO utpTimestampedPacketDTO) {
        return currentTime - utpTimestampedPacketDTO.stamp() > resendTimeOutMicros;
    }

    /**
     * @return the timestamp of the oldest unacked packet.
     */
    public long getOldestUnackedTimestamp() {
        if (!buffer.isEmpty()) {
            long timeStamp = Long.MAX_VALUE;
            for (UtpTimestampedPacketDTO pkt : buffer) {
                if (pkt.stamp() < timeStamp && !pkt.isPacketAcked()) {
                    timeStamp = pkt.stamp();
                }
            }
            return timeStamp;
        }
        return 0L;
    }

    /**
     * Returns the timestamp when this packet was send.
     *
     * @param seqNrToAck the seq. number.
     */
    public long getSendTimeStamp(int seqNrToAck) {
        UtpTimestampedPacketDTO pkt = findPacket(seqNrToAck);
        if (pkt != null) {
            return pkt.stamp();
        }
        return -1;
    }

    public void setRemoteAdress(SocketAddress addr) {
        this.addr = addr;

    }

    /**
     * @param seqNrToAck packet with that sequence number.
     * @return the number how many times this pkt was resend.
     */
    public int getResendCounter(int seqNrToAck) {
        UtpTimestampedPacketDTO pkt = findPacket(seqNrToAck);
        if (pkt != null) {
            return pkt.getResendCounter();
        }
        return 1;
    }

}
