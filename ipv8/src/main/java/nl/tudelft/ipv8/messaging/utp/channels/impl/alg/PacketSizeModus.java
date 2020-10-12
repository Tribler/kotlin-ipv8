package nl.tudelft.ipv8.messaging.utp.channels.impl.alg;

public enum PacketSizeModus {
    /**
     * dynamic packet sizes, proportional to current buffering delay
     */
    DYNAMIC_LINEAR,
    /**
     * constant pkt sizes, 576 bytes
     */
    CONSTANT_576,

    /**
     * constant packet sizes, 576 bytes.
     */
    CONSTANT_1472
}
