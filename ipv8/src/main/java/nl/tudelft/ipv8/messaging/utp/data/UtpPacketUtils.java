package nl.tudelft.ipv8.messaging.utp.data;

import java.net.DatagramPacket;

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUbyte;

public class UtpPacketUtils {

    public static final byte VERSION = longToUbyte(1);

    public static final byte DATA = (byte) (VERSION | longToUbyte(0));
    public static final byte FIN = (byte) (VERSION | longToUbyte(16));
    public static final byte STATE = (byte) (VERSION | longToUbyte(32));
    public static final byte RESET = (byte) (VERSION | longToUbyte(48));
    public static final byte SYN = (byte) (VERSION | longToUbyte(64));

    public static final byte SELECTIVE_ACK = longToUbyte(1);

    public static final int DEF_HEADER_LENGTH = 20;

    public static byte[] joinByteArray(byte[] array1, byte[] array2) {

        int length1 = array1 == null ? 0 : array1.length;
        int length2 = array2 == null ? 0 : array2.length;


        int totalLength = length1 + length2;
        byte[] returnArray = new byte[totalLength];

        int i = 0;
        for (; i < length1; i++) {
            returnArray[i] = array1[i];
        }

        for (int j = 0; j < length2; j++) {
            returnArray[i] = array2[j];
            i++;
        }

        return returnArray;

    }

    /**
     * Creates an Utp-Packet to initialize a connection
     * Following values will be set:
     * <ul>
     * <li>Type and Version</li>
     * <li>Sequence Number</li>
     * </ul>
     *
     * @return {@link UtpPacket}
     */
    public static UtpPacket createSynPacket() {

        UtpPacket pkt = new UtpPacket();
        pkt.setTypeVersion(SYN);
        pkt.setSequenceNumber((short) (Short.MIN_VALUE + 1));
        byte[] pl = {1, 2, 3, 4, 5, 6};
        pkt.setPayload(pl);
        return pkt;
    }

    public static UtpPacket extractUtpPacket(DatagramPacket dgpkt) {

        UtpPacket pkt = new UtpPacket();
        byte[] pktb = dgpkt.getData();
        pkt.setFromByteArray(pktb, dgpkt.getLength(), dgpkt.getOffset());
        return pkt;
    }

    public static boolean isSynPkt(UtpPacket packet) {

        if (packet == null) {
            return false;
        }

        return packet.getTypeVersion() == SYN;

    }


    private static boolean isPacketType(DatagramPacket packet, byte flag) {
        if (packet == null) {
            return false;
        }

        byte[] data = packet.getData();

        if (data != null && data.length >= DEF_HEADER_LENGTH) {
            return data[0] == flag;
        }

        return false;
    }

    public static boolean isSynPkt(DatagramPacket packet) {
        return isPacketType(packet, SYN);
    }

    public static boolean isResetPacket(DatagramPacket udpPacket) {
        return isPacketType(udpPacket, RESET);
    }

    public static boolean isDataPacket(DatagramPacket udpPacket) {
        return isPacketType(udpPacket, DATA);
    }

    public static boolean isStatePacket(DatagramPacket udpPacket) {
        return isPacketType(udpPacket, STATE);
    }

    public static boolean isFinPacket(DatagramPacket udpPacket) {
        return isPacketType(udpPacket, FIN);
    }

}
