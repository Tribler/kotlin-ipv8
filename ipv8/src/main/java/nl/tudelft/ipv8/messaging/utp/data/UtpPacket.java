package nl.tudelft.ipv8.messaging.utp.data;

import java.util.ArrayList;
import java.util.Arrays;

import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;

import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.DEF_HEADER_LENGTH;
import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.joinByteArray;

public class UtpPacket {

    private byte typeVersion;
    private byte firstExtension;
    private short connectionId;
    private int timestamp;
    private int timestampDifference;
    private int windowSize;
    private short sequenceNumber;
    private short ackNumber;
    private UtpHeaderExtension[] extensions;
    private byte[] payload;


    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public UtpHeaderExtension[] getExtensions() {
        return this.extensions;
    }

    public void setExtensions(UtpHeaderExtension[] extensions) {
        this.extensions = extensions;
    }

    public void setFirstExtension(byte firstExtension) {
        this.firstExtension = firstExtension;
    }

    public byte getTypeVersion() {
        return typeVersion;
    }

    public void setTypeVersion(byte typeVersion) {
        this.typeVersion = typeVersion;
    }

    public short getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(short connectionId) {
        this.connectionId = connectionId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getTimestampDifference() {
        return timestampDifference;
    }

    public void setTimestampDifference(int timestampDifference) {
        this.timestampDifference = timestampDifference;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public short getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(short sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public short getAckNumber() {
        return ackNumber;
    }

    public void setAckNumber(short ackNumber) {
        this.ackNumber = ackNumber;
    }

    public byte[] toByteArray() {
        if (noExtensions()) {
            return joinByteArray(getExtensionlessByteArray(), getPayload());
        }

        int offset = DEF_HEADER_LENGTH;
        int headerLength = offset + getTotalLengthOfExtensions();
        byte[] header = new byte[headerLength];
        byte[] extensionlessArray = getExtensionlessByteArray();

        System.arraycopy(extensionlessArray, 0, header, 0, extensionlessArray.length);

        for (UtpHeaderExtension extension : extensions) {
            byte[] extensionBytes = extension.toByteArray();
            for (byte extensionByte : extensionBytes) {
                header[offset++] = extensionByte;
            }
        }
        UtpPacketLoggerKt.getLogger().debug("header: " + Arrays.toString(header));
        return joinByteArray(header, getPayload());
    }

    private byte[] getExtensionlessByteArray() {
        return new byte[]{
            typeVersion,
            firstExtension,
            (byte) (connectionId >> 8), (byte) (connectionId),
            (byte) (timestamp >> 24), (byte) (timestamp >> 16), (byte) (timestamp >> 8), (byte) (timestamp),
            (byte) (timestampDifference >> 24), (byte) (timestampDifference >> 16), (byte) (timestampDifference >> 8), (byte) (timestampDifference),
            (byte) (windowSize >> 24), (byte) (windowSize >> 16), (byte) (windowSize >> 8), (byte) (windowSize),
            (byte) (sequenceNumber >> 8), (byte) (sequenceNumber), (byte) (ackNumber >> 8), (byte) (ackNumber)
        };
    }

    private boolean noExtensions() {
        return (extensions == null || extensions.length == 0) && firstExtension == 0;
    }

    private int getTotalLengthOfExtensions() {
        if (noExtensions()) {
            return 0;
        }
        int length = 0;
        for (UtpHeaderExtension extension : extensions) {
            length += 2 + extension.getBitMask().length;
        }
        return length;
    }

    /**
     * returns the total packet length.
     *
     * @return bytes.
     */
    public int getPacketLength() {
        byte[] pl = getPayload();
        int plLength = pl != null ? pl.length : 0;
        return DEF_HEADER_LENGTH + getTotalLengthOfExtensions() + plLength;
    }

    /**
     * Sets the packet data from the array
     *
     * @param array  the array
     * @param length total packet length
     * @param offset the packet starts here
     */
    public void setFromByteArray(byte[] array, int length, int offset) {
        if (array == null) {
            return;
        }
        typeVersion = array[0];
        firstExtension = array[1];
        connectionId = UnsignedTypesUtil.bytesToUshort(array[2], array[3]);
        timestamp = UnsignedTypesUtil.bytesToUint(array[4], array[5], array[6], array[7]);
        timestampDifference = UnsignedTypesUtil.bytesToUint(array[8], array[9], array[10], array[11]);
        windowSize = UnsignedTypesUtil.bytesToUint(array[12], array[13], array[14], array[15]);
        sequenceNumber = UnsignedTypesUtil.bytesToUshort(array[16], array[17]);
        ackNumber = UnsignedTypesUtil.bytesToUshort(array[18], array[19]);

        int utpOffset = offset + UtpPacketUtils.DEF_HEADER_LENGTH;
        if (firstExtension != 0) {
            utpOffset += loadExtensions(array);
        }

        payload = new byte[length - utpOffset];
        System.arraycopy(array, utpOffset, payload, 0, length - utpOffset);
    }

    private int loadExtensions(byte[] array) {
        byte extensionType = array[1];
        int extensionStartIndex = 20;
        int totalLength = 0;

        ArrayList<UtpHeaderExtension> list = new ArrayList<>();
        UtpHeaderExtension extension = UtpHeaderExtension.resolve(extensionType);

        if (extension != null) {
            int extensionLength = array[extensionStartIndex + 1] + 128;
            byte[] bitmask = new byte[extensionLength];
            UtpPacketLoggerKt.getLogger().debug("extension: " + Arrays.toString(array));
            System.arraycopy(array, extensionStartIndex + 2, bitmask, 0, extensionLength);
            extension.setNextExtension(array[extensionStartIndex]);
            extension.setBitMask(bitmask);
            list.add(extension);
            totalLength = extensionLength + 2;
        }

        UtpHeaderExtension[] extensions = list.toArray(new UtpHeaderExtension[list.size()]);
        setExtensions(extensions);
        return totalLength;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UtpPacket)) {
            return false;
        } else {
            UtpPacket pkt = (UtpPacket) obj;

            byte[] their = pkt.toByteArray();
            byte[] mine = this.toByteArray();

            if (their.length != mine.length) {
                return false;
            }
            for (int i = 0; i < mine.length; i++) {
                if (mine[i] != their[i]) {
                    return false;
                }
            }

            return true;
        }

    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("[Type: " + (typeVersion & 0xFF) + "] " +
            "[FirstExt: " + (firstExtension & 0xFF) + "] " +
            "[ConnId: " + connectionId + "] " +
            "[Wnd: " + (windowSize & 0xFFFFFFFF) + " " +
            "[Seq: " + sequenceNumber + "] " +
            "[Ack: " + ackNumber + "] ");

        if (extensions != null) {
            for (int i = 0; i < extensions.length; i++) {
                ret.append("[Ext_").append(i).append(": ").append(extensions[i].getNextExtension() & 0xFF).append(" ").append(extensions[i].getLength()).append("] ");
            }
        }
        return ret.toString();
    }


    @Override
    public int hashCode() {
        int code = 0;
        //TB: multiply with prime and xor
        //TODO: check if hashCode is needed
        code = typeVersion + 10 * firstExtension + 100 * connectionId
            + 1000 * timestamp + 10000 * timestampDifference + 100000 * windowSize
            + 1000000 * sequenceNumber + 10000000 * ackNumber + 1000000000 * toByteArray().length;
        return code;
    }

}
