package nl.tudelft.ipv8.messaging.utp.channels.impl.alg;

import java.util.LinkedList;
import java.util.Queue;

public class MinimumDelay {

    private static final int DELAY_SAMPLE_SIZE = 50;
    private long ourTimeStamp = 0;
    private long minDelay = 0;

    private long theirTimeStamp = 0;
    private long theirMinDelay = 0;
    private Queue<Long> ourLastDelays = new LinkedList<>();

    public long getCorrectedMinDelay() {
        return minDelay;
    }

    /**
     * Updates the sender-to-receiver delays
     *
     * @param difference delay
     * @param timestamp  now.
     */
    public void updateOurDelay(long difference, long timestamp) {
        if ((timestamp - this.ourTimeStamp >= UtpAlgConfiguration.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC)
            || (this.ourTimeStamp == 0 && this.minDelay == 0)) {
            this.ourTimeStamp = timestamp;
            this.minDelay = difference;
        } else {
            if (difference < this.minDelay) {
                this.ourTimeStamp = timestamp;
                this.minDelay = difference;
            }
        }

    }

    /**
     * Updates the receiver-to-sender delays
     */
    public void updateTheirDelay(long theirDifference, long timeStampNow) {
        if ((timeStampNow - this.theirTimeStamp >= UtpAlgConfiguration.MINIMUM_DIFFERENCE_TIMESTAMP_MICROSEC)
            || (this.theirTimeStamp == 0 && this.theirMinDelay == 0)) {
            theirMinDelay = theirDifference;
            this.theirTimeStamp = timeStampNow;
        } else {
            if (theirDifference < theirMinDelay) {
                theirTimeStamp = timeStampNow;
                minDelay += (theirMinDelay - theirDifference);
                theirMinDelay = theirDifference;
            }
        }
    }

    public long getTheirMinDelay() {
        return theirMinDelay;
    }

    /**
     * Adds an delay sample.
     *
     * @param ourDelay the delay
     */
    public void addSample(long ourDelay) {
        while (ourLastDelays.size() > DELAY_SAMPLE_SIZE) {
            ourLastDelays.poll();
        }
        ourLastDelays.add(ourDelay);

    }

    /**
     * Calcs an average of the last delay samples.
     *
     * @return avg delay.
     */
    public long getRecentAverageDelay() {
        long sum = 0;
        for (long delay : ourLastDelays) {
            sum += delay;
        }
        long sampleSize = ourLastDelays.size();
        if (sampleSize == 0) {
            return 0L;
        } else {
            return sum / sampleSize;
        }
    }

}
