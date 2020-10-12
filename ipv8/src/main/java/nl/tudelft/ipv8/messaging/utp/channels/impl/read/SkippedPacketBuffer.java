package nl.tudelft.ipv8.messaging.utp.channels.impl.read;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpTimestampedPacketDTO;
import nl.tudelft.ipv8.messaging.utp.channels.impl.alg.UtpAlgConfiguration;
import nl.tudelft.ipv8.messaging.utp.data.SelectiveAckHeaderExtension;

public class SkippedPacketBuffer {

    private static final int SIZE = 2000;
    private UtpTimestampedPacketDTO[] buffer = new UtpTimestampedPacketDTO[SIZE];
    private short expectedSequenceNumber = Short.MIN_VALUE;
    private int elementCount = 0;
    private short debug_lastSeqNumber;
    private short debug_lastPosition;

    /**
     * puts the packet in the buffer.
     *
     * @param pkt the packet with meta data.
     */
    public void bufferPacket(UtpTimestampedPacketDTO pkt) throws IOException {
        short sequenceNumber = pkt.utpPacket().getSequenceNumber();
        short position;
        debug_lastSeqNumber = sequenceNumber;
        if (sequenceNumber - expectedSequenceNumber < 0) {
            position = mapOverflowPosition(sequenceNumber);
        } else {
            position = (short) (sequenceNumber - expectedSequenceNumber);
        }
        debug_lastPosition = position;
        elementCount++;
        try {
            buffer[position] = pkt;
        } catch (ArrayIndexOutOfBoundsException ioobe) {
            ioobe.printStackTrace();

            dumpBuffer("oob: " + ioobe.getMessage());
            throw new IOException();
        }

    }

    private short mapOverflowPosition(int sequenceNumber) {
        return (short) (Short.MAX_VALUE - expectedSequenceNumber + sequenceNumber);
    }

    public short getExpectedSequenceNumber() {
        return expectedSequenceNumber;
    }

    public void setExpectedSequenceNumber(short seq) {
        this.expectedSequenceNumber = seq;
    }

    public SelectiveAckHeaderExtension createHeaderExtension() {
        SelectiveAckHeaderExtension header = new SelectiveAckHeaderExtension();
        int length = calculateHeaderLength();
        byte[] bitMask = new byte[length];
        fillBitMask(bitMask);
        header.setBitMask(bitMask);

        return header;
    }

    private void fillBitMask(byte[] bitMask) {
        int bitMaskIndex = 0;
        for (int i = 1; i < SIZE; i++) {
            int bitMapIndex = (i - 1) % 8;
            boolean hasReceived = buffer[i] != null;

            if (hasReceived) {
                int bitPattern = (SelectiveAckHeaderExtension.BITMAP[bitMapIndex] & 0xFF) & 0xFF;
                bitMask[bitMaskIndex] = (byte) ((bitMask[bitMaskIndex] & 0xFF) | bitPattern);
            }

            if (i % 8 == 0) {
                bitMaskIndex++;
            }
        }
    }


    private int calculateHeaderLength() {
        int size = getRange();
        return (((size - 1) / 32) + 1) * 4;
    }

    private int getRange() {
        int range = 0;
        for (int i = 0; i < SIZE; i++) {
            if (buffer[i] != null) {
                range = i;
            }
        }
        return range;
    }


    public boolean isEmpty() {
        return elementCount == 0;
    }

    public Queue<UtpTimestampedPacketDTO> getAllUntillNextMissing() {
        Queue<UtpTimestampedPacketDTO> queue = new LinkedList<UtpTimestampedPacketDTO>();
        for (int i = 1; i < SIZE; i++) {
            if (buffer[i] != null) {
                queue.add(buffer[i]);
                buffer[i] = null;
            } else {
                break;
            }
        }
        elementCount -= queue.size();
        return queue;
    }

    public void reindex(int lastSeqNumber) throws IOException {
        short expectedSequenceNumber;
        if (lastSeqNumber == Short.MAX_VALUE) {
            expectedSequenceNumber = 1;
        } else {
            expectedSequenceNumber = (short) (lastSeqNumber + 1);
        }
        setExpectedSequenceNumber(expectedSequenceNumber);
        UtpTimestampedPacketDTO[] oldBuffer = buffer;
        buffer = new UtpTimestampedPacketDTO[SIZE];
        elementCount = 0;
        for (UtpTimestampedPacketDTO utpTimestampedPacket : oldBuffer) {
            if (utpTimestampedPacket != null) {
                bufferPacket(utpTimestampedPacket);
            }
        }


    }

    public int getFreeSize() throws IOException {
        if (SIZE - elementCount < 0) {
            dumpBuffer("freesize negative");
        }
        if (SIZE - elementCount < 50) {
            return 0;
        }
        return SIZE - elementCount - 1;
    }

    private void dumpBuffer(String string) throws IOException {
        if (UtpAlgConfiguration.DEBUG) {

            RandomAccessFile aFile = new RandomAccessFile("testData/auto/bufferdump.txt", "rw");
            FileChannel inChannel = aFile.getChannel();
            inChannel.truncate(0);
            ByteBuffer bbuffer = ByteBuffer.allocate(100000);
            bbuffer.put((new SimpleDateFormat("dd_MM_hh_mm_ss")).format(new Date()).getBytes());
            bbuffer.put((string + "\n").getBytes());
            bbuffer.put(("SIZE: " + SIZE + "\n").getBytes());
            bbuffer.put(("count: " + elementCount + "\n").getBytes());
            bbuffer.put(("expect: " + expectedSequenceNumber + "\n").getBytes());
            bbuffer.put(("lastSeq: " + debug_lastSeqNumber + "\n").getBytes());
            bbuffer.put(("lastPos: " + debug_lastPosition + "\n").getBytes());

            for (int i = 0; i < SIZE; i++) {
                String seq;
                if (buffer[i] == null) {
                    seq = "_; ";
                } else {
                    seq = buffer[i].utpPacket().getSequenceNumber() + "; ";
                }
                bbuffer.put((i + " -> " + seq).getBytes());
                if (i % 50 == 0) {
                    bbuffer.put("\n".getBytes());
                }
            }
            bbuffer.flip();
            while (bbuffer.hasRemaining()) {
                inChannel.write(bbuffer);
            }
            aFile.close();
            inChannel.close();
        }

    }


}
