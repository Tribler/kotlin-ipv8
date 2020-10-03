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
package nl.tudelft.ipv8.messaging.utp.channels.impl.read;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpReadFuture;

/**
 * Implements the future and hides details from the super class.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class UtpReadFutureImpl extends UtpReadFuture {

    private final Consumer<byte[]> onFileReceived;

    public UtpReadFutureImpl(Consumer<byte[]> onFileReceived) throws InterruptedException {
        super();
        this.onFileReceived = onFileReceived;
    }

    /**
     * Releasing semaphore and running the listener if set.
     */
    public void finished(IOException exp, ByteArrayOutputStream bos) {
        this.bos = bos;
        this.exception = exp;
        isDone = true;
        semaphore.release();
        listenerLock.lock();
        onFileReceived.accept(bos.toByteArray());
        listenerLock.unlock();
    }
}
