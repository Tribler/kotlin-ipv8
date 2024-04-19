# UTP in IPv8

uTP is a protocol based on the UDP used to share files in a peer-to-peer fashion. In this project, we have integrated an existing `utp4j` library with the IPv8 infrastructure to allow for binary data transfer to users on the existing network.

## UtpCommunity

To distinguish between the capabilities of different peers, we have created our own `UtpCommunity` to show that the peer supports uTP transfer. We use this community to implement some useful control messages in our infrastructure. These are:
- `UTP_HEARTBEAT` - used to signal that the peer has a working uTP endpoint and can accept connections.
- `UTP_TRANSFER_REQUEST` - used as a handshake for establishing a file transfer.

### UtpHelper

To separate some of the logic of sending messages over the protocol, we have created a `UtpHelper` class. It holds all the utility methods used by peers sending data and creates a coroutine to send heartbeat messages.

#### Heartbeats

The heartbeat service is started right after `UtpCommunity` initialization. It creates a separate coroutine which is active as long as the uTP endpoint is open. A heartbeat message is sent every 5 seconds to each peer in the community to signal own liveness.

For now the heartbeat has an empty payload, but could be used to hold some useful data to be sent to all the peers.

#### Data transfer

For file transfer we have two separate methods exposed to the other classes. One allows to send file data directly, the other is used to generate and send a random, hash verified data. The transfer works as follows:
- First the `UTP_TRANSFER_REQUEST` message is sent to request to the other peer that we want to send them a data with certain metadata (name, size, type (random/file)).
- We wait for the response from the other peer. They can either accept or decline.
- If they decline, we abort the transfer. If it is accepted, we send the data using the uTP endpoint.

Peers which have not established the handshake cannot transfer data. All the packets that would be sent directly to the endpoint, would be dropped. This mechanism is used as a basic protection against the DOS attacks. Current defaults always accept the connection, but further conditions could be added to combat malicious transfers.  

## UtpIPv8Endpoint

This endpoint is the main element of the uTP transfer. It uses the previously mentioned existing library to establish transfer of data between two peers using UDP. It is an extension of the existing `UdpEndpoint`. The existing endpoint creates an instance of `UtpIPv8Endpoint` similar to what TFTP has done. Then every packet that is intended to be sent to our endpoint is prefixed with a special byte (`0x42`) and routed to be further processed.

In the actual endpoint code, we have a logic for both sending (client) and receiving (server) data. Most of the code there is based on the examples shown in the library already, just refactored to use Kotlin features. 

Every routed packed is processed in the `onPacket` function. Stripped off the prefix, checked for the handshake details and finally forwarded to the custom client and server socket. 

### UtpSocket

As we are using IPv8 layer for our transfer, we could not use usual implementations of the `DatagramSocket`. We have created a middle layer in-between the IPv8 and `utp4j` library to allow for a seamless integration. It uses the raw IPv8 socket to send messages prefixed with out special byte. For receiving it has a separate receiving channel which is filled by the `onPacket` function in the endpoint. This gives us an elegant solution, which does not interfere with the code of the library.

## Testing

All the code has been tested and achieved 86% instruction coverage.

## Remaining issues

Unfortunately due to the timeframe we were not able to fully check the quality of every element and some solutions could be done better. Smaller existing issues are mentioned directly in the code.

The main improvements would be:
- code structure
- missing listener API (which would allow for displaying transfer data progress)
- more robust handshake architecture

