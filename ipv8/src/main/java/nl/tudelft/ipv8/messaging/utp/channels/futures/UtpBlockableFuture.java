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
import java.util.concurrent.Semaphore;

/**
 * Superclass to all blockable futures.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class UtpBlockableFuture {

	protected volatile boolean isDone;
	protected volatile IOException exception;
	protected volatile Semaphore semaphore = new Semaphore(1);

	public UtpBlockableFuture() throws InterruptedException {
		semaphore.acquire();
	}
	/**
	 * Returns true if this future task succeeded.
	 */
	public boolean isSuccessfull() {
		return exception == null;
	}

	/**
	 * Blocks the current thread until the future task is done.
	 * @throws InterruptedException
	 */
	public void block() throws InterruptedException {
		semaphore.acquire();
		semaphore.release();

	}

	public IOException getCause() {
		return exception;
	}

	public boolean isDone() {
		return isDone;
	}

	/**
	 * Unblocks the calling thread.
	 */
	public void unblock() {
		semaphore.release();
	}

}
