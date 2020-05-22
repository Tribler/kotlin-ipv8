package nl.tudelft.ipv8.peerdiscovery

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.payload.ConnectionType
import org.junit.Assert
import org.junit.Test
import java.util.*

class WanEstimationLogTest {
    @Test
    fun addItem_duplicate() {
        val log = WanEstimationLog()
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234))
        )
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234))
        )
        Assert.assertEquals(1, log.getLog().size)
    }

    @Test
    fun estimateWan() {
        val log = WanEstimationLog()
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234))
        )
        Assert.assertEquals(IPv4Address("3.2.3.4", 3234), log.estimateWan())
    }

    @Test
    fun estimateConnectionType_unknown() {
        val log = WanEstimationLog()
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234))
        )
        Assert.assertEquals(ConnectionType.UNKNOWN, log.estimateConnectionType())
    }

    @Test
    fun estimateConnectionType_unknown_robust() {
        val log = WanEstimationLog()
        for (i in 0..10) {
            log.addItem(WanEstimationLog.WanLogItem(Date(),
                IPv4Address("1.2.3.4", i),
                IPv4Address("2.2.3.4", 2234),
                IPv4Address("3.2.3.4", 3234))
            )
        }

        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 9876))
        )

        Assert.assertEquals(ConnectionType.UNKNOWN, log.estimateConnectionType())
    }

    @Test
    fun estimateConnectionType_symmetric() {
        val log = WanEstimationLog()
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234))
        )
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("10.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3235))
        )
        Assert.assertEquals(ConnectionType.SYMMETRIC_NAT, log.estimateConnectionType())
    }

    @Test
    fun estimateConnectionType_public() {
        val log = WanEstimationLog()
        log.addItem(WanEstimationLog.WanLogItem(Date(),
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("2.2.3.4", 2234))
        )
        Assert.assertEquals(ConnectionType.PUBLIC, log.estimateConnectionType())
    }
}
