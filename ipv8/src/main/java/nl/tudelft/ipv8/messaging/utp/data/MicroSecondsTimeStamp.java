package nl.tudelft.ipv8.messaging.utp.data;

import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;
import nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions.ByteOverflowException;

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUint;

public class MicroSecondsTimeStamp {

    private static long initDateMillis = System.currentTimeMillis();
    private static long startNs = System.nanoTime();

    /**
     * Returns a uTP time stamp for packet headers.
     *
     * @return timestamp
     */
    public int utpTimeStamp() {
        int returnStamp;
        //TODO: if performance issues, try bitwise & operator since constant MAX_UINT equals 0xFFFFFF
        // (see http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues )
        long stamp = timeStamp() % UnsignedTypesUtil.MAX_UINT;
        try {
            returnStamp = longToUint(stamp);
        } catch (ByteOverflowException exp) {
            stamp = stamp % MAX_UINT;
            returnStamp = longToUint(stamp);
        }
        return returnStamp;
    }

    /**
     * Calculates the Difference of the uTP timestamp between now and parameter.
     *
     * @param othertimestamp timestamp
     * @return difference
     */
    public int utpDifference(int othertimestamp) {
        return utpDifference(utpTimeStamp(), othertimestamp);
    }

    /**
     * calculates the utp Difference of timestamps (this - other)
     *
     * @return difference.
     */
    public int utpDifference(int thisTimeStamp, int othertimestamp) {
        int nowTime = thisTimeStamp;
        long nowTimeL = nowTime & 0xFFFFFFFF;
        long otherTimeL = othertimestamp & 0xFFFFFFFF;
        long differenceL = nowTimeL - otherTimeL;
        // TODO: POSSIBLE BUG NEGATIVE DIFFERENCE
        if (differenceL < 0) {
            differenceL += MAX_UINT;
        }
        return longToUint(differenceL);
    }

    /**
     * @return timestamp with micro second resulution
     */
    public long timeStamp() {

        long currentNs = System.nanoTime();
        long deltaMs = (currentNs - startNs) / 1000;
        return (initDateMillis * 1000 + deltaMs);
    }
}
