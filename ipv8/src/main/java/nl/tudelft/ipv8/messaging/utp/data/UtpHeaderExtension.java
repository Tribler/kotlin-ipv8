package nl.tudelft.ipv8.messaging.utp.data;

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUbyte;

public abstract class UtpHeaderExtension {

    public static UtpHeaderExtension resolve(byte b) {
        if (b == longToUbyte(1)) {
            return new SelectiveAckHeaderExtension();
        } else {
            return null;
        }
    }

    public abstract byte getNextExtension();

    public abstract void setNextExtension(byte nextExtension);

    public abstract byte getLength();

    public abstract byte[] getBitMask();

    public abstract void setBitMask(byte[] bitMask);

    public abstract byte[] toByteArray();
}
