package nl.tudelft.ipv8.jvm.demo.examples.force_walk

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address

class ConnectionCommunity : Community() {
    override val serviceId = "02313685c1912a141279f8248fc8db5899c5df5b"

    override fun walkTo(address: IPv4Address) {
        val packet = createIntroductionRequest(address)
        this.endpoint.send(address, packet)
    }

}

