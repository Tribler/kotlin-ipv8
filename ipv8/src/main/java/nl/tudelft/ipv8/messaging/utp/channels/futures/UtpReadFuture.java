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
package nl.tudelft.ipv8.messaging.utp.channels.futures;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Read future.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class UtpReadFuture extends UtpBlockableFuture {
    protected final ReentrantLock listenerLock = new ReentrantLock();
    protected volatile ByteArrayOutputStream bos;
    protected volatile UtpReadListener listener;

    public UtpReadFuture() throws InterruptedException {
        super();
    }

    /**
     * Sets a listener that will be informed once the future task is completed.
     */
    public void setListener(UtpReadListener listener) {
        listenerLock.lock();
        this.listener = listener;
        listenerLock.unlock();
    }
}
