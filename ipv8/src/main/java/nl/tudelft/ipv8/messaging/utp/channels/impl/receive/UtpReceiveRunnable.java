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

//
//import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.MAX_UDP_HEADER_LENGTH;
//import static nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils.MAX_UTP_PACKET_LENGTH;
/*

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

*/
/**
 * Runs in a loop and listens on a {@see DataGramSocket} for incomming packets and passes them.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 *//*

public class UtpReceiveRunnable extends Thread implements Runnable {


	private DatagramSocket socket;
	private UtpPacketRecievable packetReciever;
	private boolean graceFullInterrupt = false;

	private final static Logger log = LoggerFactory.getLogger(UtpReceiveRunnable.class);


	public UtpReceiveRunnable(DatagramSocket socket, UtpPacketRecievable queueable) {
		this.socket = socket;
		this.packetReciever = queueable;
	}

	public void graceFullInterrupt() {
		super.interrupt();
		graceFullInterrupt = true;
		socket.close();
	}

	private boolean continueThread() {
		return !graceFullInterrupt;
	}

	@Override
	public void run() {
		while(continueThread()) {
			byte[] buffer = new byte[MAX_UDP_HEADER_LENGTH + MAX_UTP_PACKET_LENGTH];
			DatagramPacket dgpkt = new DatagramPacket(buffer, buffer.length);
			try {
				//the packet will be filled here
				socket.receive(dgpkt);
				//hand packet to the queue
				packetReciever.recievePacket(dgpkt);
//				(new UtpPassPacket(dgpkt, queueable)).start();

			} catch (IOException exp) {
				if (graceFullInterrupt) {
					log.debug("socket closing");
					break;
				} else {
					exp.printStackTrace();
				}
			}
		}
		log.debug("RECIEVER OUT");

	}

}
*/
