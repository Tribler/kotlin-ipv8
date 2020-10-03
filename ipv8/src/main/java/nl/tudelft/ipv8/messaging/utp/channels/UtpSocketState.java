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
package nl.tudelft.ipv8.messaging.utp.channels;

/**
 * Possible states in which a Utp Socket can be.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public enum UtpSocketState {

		/**
		 * Indicates that a syn packet has been send.
		 */
		SYN_SENT,

		/**
		 * Indicates that a connection has been established.
		 */
		CONNECTED,

		/**
		 * Indicates that no connection is established.
		 */
		CLOSED,

		/**
		 * Indicates that a SYN has been received but could not be acked.
		 */
		SYN_ACKING_FAILED,

		/**
		 * Indicates that the fin Packet has been send.
		 */
		FIN_SEND,

		/**
		 * Indicates that a fin was received.
		 */
		GOT_FIN,

}
