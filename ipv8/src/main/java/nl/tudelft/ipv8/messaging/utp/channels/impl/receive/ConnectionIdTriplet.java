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
package nl.tudelft.ipv8.messaging.utp.channels.impl.receive;

import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel;
import nl.tudelft.ipv8.messaging.utp.channels.impl.UtpSocketChannelImpl;

/**
 * DTO to put channel and the connection id's in one class.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class ConnectionIdTriplet {

	private UtpSocketChannel channel;
	//incoming connection ID
	private long incoming;
	//outgoing is incoming connection ID + 1 as specified
	private long outGoing;

	public UtpSocketChannel getChannel() {
		return channel;
	}

	public ConnectionIdTriplet(UtpSocketChannel channel, int incoming, long outgoing) {
		this.channel = channel;
		this.incoming = incoming;
		this.outGoing = outgoing;
	}

	@Override
	public boolean equals(Object other) {

		if (other == null) {
			return false;
		} else if (this == other) {
			return true;
		} else if (!(other instanceof ConnectionIdTriplet)) {
			return false;
		} else {
			ConnectionIdTriplet otherTriplet = (ConnectionIdTriplet) other;
			return this.incoming == otherTriplet.getIncoming() &&
				   this.outGoing == otherTriplet.getOutGoing();
		}
	}

	@Override
	public int hashCode() {
		int hash = 23;
		hash = hash * 31 + (int) outGoing;
		hash = hash * 31 + (int) incoming;
		return hash;
	}

	public void setChannel(UtpSocketChannelImpl channel) {
		this.channel = channel;
	}


	public long getIncoming() {
		return incoming;
	}


	public void setIncoming(long incoming) {
		this.incoming = incoming;
	}


	public long getOutGoing() {
		return outGoing;
	}


	public void setOutgoing(long outGoing) {
		this.outGoing = outGoing;
	}
}
