package nl.tudelft.ipv8.messaging.utp.data;

import java.util.Arrays;

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUbyte;

public class SelectiveAckHeaderExtension extends UtpHeaderExtension {

    /* Bit mappings */
    public static byte[] BITMAP = {1, 2, 4, 8, 16, 32, 64, (byte) 128};
    private byte nextExtension;
    private byte[] bitMask;

    /**
     * true if the ACK+number-th bit is set to 1 in the bitmask
     *
     * @param bitmask bitmask
     * @param number  number
     * @return true if bit is set, otherwise false.
     */
    public static boolean isBitMarked(byte bitmask, int number) {
        if (number < 2 || number > 9) {
            return false;
        } else {
            boolean returnvalue = (BITMAP[number - 2] & bitmask) == BITMAP[number - 2];
            return returnvalue;
        }
    }

    @Override
    public byte getNextExtension() {
        return nextExtension;
    }

    @Override
    public void setNextExtension(byte nextExtension) {
        this.nextExtension = nextExtension;
    }

    @Override
    public byte getLength() {
        return longToUbyte(bitMask.length);
    }

    @Override
    public byte[] getBitMask() {
        return bitMask;
    }

    @Override
    public void setBitMask(byte[] bitMask) {
        this.bitMask = bitMask;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SelectiveAckHeaderExtension)) {
            return false;
        }
        SelectiveAckHeaderExtension s = (SelectiveAckHeaderExtension) obj;
        return Arrays.equals(toByteArray(), s.toByteArray());
    }

    @Override
    public byte[] toByteArray() {
        UtpPacketLoggerKt.getLogger().debug("toByteArray: extension = " + nextExtension + ", length = " + bitMask.length + " or " + longToUbyte(bitMask.length));
        //TODO: not create a new byte array
        byte[] array = new byte[2 + bitMask.length];
        array[0] = nextExtension;
        if (bitMask.length > 255) {
            throw new RuntimeException("Bitmask too long");
        }
        array[1] = (byte) (bitMask.length - 128);
        System.arraycopy(bitMask, 0, array, 2, bitMask.length);
        return array;
    }
}
