package nl.tudelft.ipv8.messaging.utp.channels;

public enum UtpSocketState {

    /**
     * Indicates that a syn packet has been send.
     */
    SYN_SENT,

    /**
     * Indicates that a connection has been established.
     */
    CONNECTED,

    /**
     * Indicates that no connection is established.
     */
    CLOSED,

    /**
     * Indicates that a SYN has been received but could not be acked.
     */
    SYN_ACKING_FAILED,

    /**
     * Indicates that the fin Packet has been send.
     */
    FIN_SEND,

    /**
     * Indicates that a fin was received.
     */
    GOT_FIN,

}
