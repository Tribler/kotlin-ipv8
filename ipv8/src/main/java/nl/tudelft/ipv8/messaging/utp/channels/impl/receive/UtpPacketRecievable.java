package nl.tudelft.ipv8.messaging.utp.channels.impl.receive;

import java.net.DatagramPacket;

/**
 * All entities that can receive UDP packets implement this (channel and server)
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public interface UtpPacketRecievable {
    /**
     * Receive that packet.
     *
     * @param packet
     */
    void receivePacket(DatagramPacket packet);

}
