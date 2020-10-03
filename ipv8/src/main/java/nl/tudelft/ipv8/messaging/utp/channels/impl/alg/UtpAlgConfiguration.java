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

public class UtpAlgConfiguration {

    public static final int MAX_CONNECTION_ATTEMPTS = 5;
    public static final int CONNECTION_ATTEMPT_INTERVALL_MILLIS = 5000;

    public static long MINIMUM_DELTA_TO_MAX_WINDOW_MICROS = 1000000;
    // ack every second packets
    public static int SKIP_PACKETS_UNTIL_ACK = 2;


    /**
     * TWEAKING SECTION
     */

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
     * Packet size modus
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


    /**
     * @return information about the algorithm. This is only used for debugging
     */
    public static String getString() {
        String toReturn = "";
        toReturn += "MINIMUM_TIMEOUT_MILLIS: " + MINIMUM_TIMEOUT_MILLIS + " ";
        toReturn += "PACKET_SIZE_MODE: " + PACKET_SIZE_MODE + " ";
        toReturn += "MAX_PACKET_SIZE: " + MAX_PACKET_SIZE + " ";
        toReturn += "MIN_PACKET_SIZE: " + MIN_PACKET_SIZE + " ";
        toReturn += "MINIMUM_MTU: " + MINIMUM_MTU + " ";
        toReturn += "MAX_CWND_INCREASE_PACKETS_PER_RTT: " + MAX_CWND_INCREASE_PACKETS_PER_RTT + " ";
        toReturn += "C_CONTROL_TARGET_MICROS: " + C_CONTROL_TARGET_MICROS + " ";
        toReturn += "SEND_IN_BURST: " + SEND_IN_BURST + " ";
        toReturn += "MAX_BURST_SEND: " + MAX_BURST_SEND + " ";
        toReturn += "MIN_SKIP_PACKET_BEFORE_RESEND: " + MIN_SKIP_PACKET_BEFORE_RESEND + " ";
        toReturn += "MICROSECOND_WAIT_BETWEEN_BURSTS: " + MICROSECOND_WAIT_BETWEEN_BURSTS + " ";
        toReturn += "TIME_WAIT_AFTER_FIN_MICROS: " + TIME_WAIT_AFTER_LAST_PACKET + " ";
        toReturn += "ONLY_POSITIVE_GAIN: " + ONLY_POSITIVE_GAIN + " ";
        toReturn += "DEBUG: " + DEBUG + " ";
        return toReturn;
    }


}
