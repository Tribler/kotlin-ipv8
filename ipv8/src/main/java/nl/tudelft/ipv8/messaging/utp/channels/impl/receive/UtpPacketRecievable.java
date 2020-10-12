package nl.tudelft.ipv8.messaging.utp.channels.impl.receive;

import java.net.DatagramPacket;

public interface UtpPacketRecievable {
    void receivePacket(DatagramPacket packet);

}
