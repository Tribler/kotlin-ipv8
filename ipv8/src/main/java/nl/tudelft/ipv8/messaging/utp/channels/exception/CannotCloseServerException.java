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
package nl.tudelft.ipv8.messaging.utp.channels.exception;

import java.util.Collection;
import java.util.LinkedList;

import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel;
import nl.tudelft.ipv8.messaging.utp.channels.impl.receive.ConnectionIdTriplet;

/**
 * Exception that indicates that the closing of a server failed because there are still channels assigned to this server.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public class CannotCloseServerException extends RuntimeException {

    /**
     * serialVersion
     */
    private static final long serialVersionUID = 2445217336379114305L;
    private Collection<UtpSocketChannel> openChannels = new LinkedList<UtpSocketChannel>();

    public CannotCloseServerException(Collection<ConnectionIdTriplet> values) {
        for (ConnectionIdTriplet connectionIdTriplet : values) {
            openChannels.add(connectionIdTriplet.getChannel());
        }
    }

    /**
     * Returns a list of open channels.
     */
    public Collection<UtpSocketChannel> getOpenChannels() {
        return openChannels;
    }


}
