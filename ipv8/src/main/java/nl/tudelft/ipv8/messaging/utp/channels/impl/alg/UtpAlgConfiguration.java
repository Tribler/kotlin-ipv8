package nl.tudelft.ipv8.messaging.utp.channels.impl.alg;

public class UtpAlgConfiguration {

    public static final int MAX_CONNECTION_ATTEMPTS = 5;
    public static final int CONNECTION_ATTEMPT_INTERVALL_MILLIS = 5000;

    public static long MINIMUM_DELTA_TO_MAX_WINDOW_MICROS = 1000000;
    // ack every second packets
    public static int SKIP_PACKETS_UNTIL_ACK = 1;

    /**
     * Auto ack every packet that is smaller than ACK_NR from ack packet.
     * Some Implementations like libutp do this.
     */
    public static boolean AUTO_ACK_SMALLER_THAN_ACK_NUMBER = true;
    /**
     * if oldest mindelay sample is older than that, update it.
     */
    public static long MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC = 120000000L;
    /**
     * timeout
     */
    public static int MINIMUM_TIMEOUT_MILLIS = 500;
    /**
     * Packet size mode
     */
    public static PacketSizeModus PACKET_SIZE_MODE = PacketSizeModus.CONSTANT_1472;
    /**
     * maximum packet size should be dynamically set once path mtu discovery
     * implemented.
     */
    public volatile static int MAX_PACKET_SIZE = 1472;
    /**
     * minimum packet size.
     */
    public volatile static int MIN_PACKET_SIZE = 150;
    /**
     * Minimum path MTU
     */
    public volatile static int MINIMUM_MTU = 576;
    /**
     * Maximal window increase per RTT - increase to allow uTP throttle up
     * faster.
     */
    public volatile static int MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000;
    /**
     * maximal buffering delay
     */
    public volatile static int C_CONTROL_TARGET_MICROS = 100000;
    /**
     * activate burst sending
     */
    public volatile static boolean SEND_IN_BURST = true;
    /**
     * Reduce burst sending artificially
     */
    public volatile static int MAX_BURST_SEND = 5;
    /**
     * Minimum number of acks past seqNr=x to trigger a resend of seqNr=x;
     */
    public volatile static int MIN_SKIP_PACKET_BEFORE_RESEND = 3;
    public volatile static long MICROSECOND_WAIT_BETWEEN_BURSTS = 28000;
    public volatile static long TIME_WAIT_AFTER_LAST_PACKET = 3000000;
    public volatile static boolean ONLY_POSITIVE_GAIN = false;
    public volatile static boolean DEBUG = false;
}
