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
package nl.tudelft.ipv8.messaging.utp.channels.impl.conn;

import java.util.concurrent.locks.ReentrantLock;

import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;
import nl.tudelft.ipv8.messaging.utp.data.UtpPacket;

/**
 * Class that resends the syn packet a few times.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class ConnectionTimeOutRunnable implements Runnable {

    private UtpPacket synPacket;
    private UtpSocketChannelImpl channel;
    private volatile ReentrantLock lock;

    public ConnectionTimeOutRunnable(UtpPacket packet,
                                     UtpSocketChannelImpl channel, ReentrantLock lock) {
        this.synPacket = packet;
        this.lock = lock;
        this.channel = channel;
    }

    @Override
    public void run() {
        channel.resendSynPacket(synPacket);
    }


}
