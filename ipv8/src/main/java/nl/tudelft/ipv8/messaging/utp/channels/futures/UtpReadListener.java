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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Listener, will be informed once the read operation is finished.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public abstract class UtpReadListener implements Runnable {

    protected ByteBuffer byteBuffer;
    protected IOException exception;
    protected boolean createExtraThread = false;
    protected Thread currentThread = null;

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer buffer) {
        this.byteBuffer = buffer;
    }

    public IOException getIOException() {
        return exception;
    }

    public void setIOException(IOException exp) {
        this.exception = exp;
    }

    /**
     * @return true if this task should be run in a separate thrad.
     */
    public boolean createExtraThread() {
        return createExtraThread;
    }

    /**
     * Set boolean flag to true, to run {@see #actionAfterReading()} in an separate therad after the read operation is finished.
     *
     * @param value
     */
    public void setCreateExtraThread(boolean value) {
        this.createExtraThread = value;
    }

    /**
     * @return the thread that called {@see #actionAfterReading()}.
     */
    public Thread getCurrentThread() {
        return currentThread;
    }

    public boolean hasException() {
        return exception != null;
    }

    public boolean isSuccessfull() {
        return exception == null;
    }

    @Override
    public void run() {
        this.currentThread = Thread.currentThread();
        actionAfterReading();
    }

    /**
     * Implement the action after reading.
     */
    public abstract void actionAfterReading();

    /**
     * @return an optional thread name if the action is set to run in a separate thread.
     */
    public abstract String getThreadName();
}
