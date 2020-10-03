package nl.tudelft.ipv8.messaging.utp.old;

/*
Created by Peter Lipay. University of Washington. June 2010.
*/

import java.net.*;
import java.io.*;
import java.util.BitSet;
import java.util.Random;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;

/*
This is the UTPPacket class, it allows us to easily convert
between UDP packets and UTP packets without having to deal
with all of the annoying byte arithmetic
*/
public class UTPPacket {

    //This byte array stores the data portion of the packet
    private byte [] payload;
    //This byte array stores the header portion of the packet
    private byte [] header;
    //This is the packet type
    private int type;
    /*Whether the packet supports extensions or not. If set to 1, then
    immediately after the standard 20 byte header, there must be a linked
    list of extensions, where the first byte is the extension type,
    the second byte indicates the length of the current extension, with the
    actual bytes of the extension coming immediately afterwards. The list is
    terminated by an extension type byte set to 0, and immediately after this
    byte will be the data payload of the packet. In our case, the only extension
    used is a very basic form of selective acking, which allows acking a single packet
    ahead into the send window. The extension type for our form of selective acking is 1.
    */
    private int extension;
    //The 2 byte connection ID
    private byte[] connectionID;
    //The time this packet was sent
    private int timestamp;
    //The last timestamp difference that was computed by the sender
    private int timestampdifference;
    //The remaining number of bytes in the receive window of the sender
    private int windowsize;
    //The sequence number of this packet
    private int sequencenumber;
    //The sequence number of the next packet that the sender is waiting to receive
    private int acknumber;
    //The address of the sender
    private InetAddress address;
    //The port of the sender
    private int port;
    //The number of times this packet has been acked
    private int acks;
    //The number of times this packet has been resent
    private int resends;
    //Whether this packet has a selective ack extension
    private boolean hasSelectiveAck = false;
    //The selective acknumber. This allows a single packet to
    //be acked past the senders acknumber. This is the sequence number
    //of that packet
    private int selectiveacknumber;

    //The time the packet was sent in nanoseconds, this is set by the sender and
    //never actually gets sent to the other end
    //(the timestamp field is in microseconds and actually gets sent)
    private long sendtime;

    //This constructor takes a received UDP packet and pulls out the necessary header
    //information from it to create this UTP packet
    public UTPPacket(DatagramPacket p) {
        byte [] data = p.getData();
        byte[] temp = new byte[2];
        //If the extension bit is on, there are extensions
        if (data[1] == 1 && p.getLength() >= 21) {
            int cur = 20;
            //Until we detect an end-of-list extension type
            //(indicated by an extension type of 0), loop through the list
            //of extensions
            while (cur < p.getLength() && data[cur] != 0) {
                //If the extension type is 1, it means this is a selective ack extension (which we recognize),
                //so pull out the selective ack number and advance the cur pointer forward
                if (data[cur] == 1) {
                    temp[0] = data[cur+2];
                    temp[1] = data[cur+3];
                    selectiveacknumber = bytesToInt(temp);
                    hasSelectiveAck = true;
                    cur += 4;
                    //If we don't recognize the extension, read the length of the extension, and advance the pointer
                    //by the extension length to skip the current extention
                } else {
                    cur++;
                    if (cur >= p.getLength()) {
                        break;
                    }
                    cur += data[cur];
                }
            }
            cur++;
            cur = Math.min(cur,p.getLength());
            header = new byte[cur];
            payload = new byte[p.getLength()-cur];
        } else {
            header = new byte[20];
            payload = new byte[p.getLength()-20];
        }
        //copying to our payload and header arrays
        for (int i = 0;i < p.getLength();i++) {
            if (i < header.length) {
                header[i] = data[i];
            } else {
                payload[i-header.length] = data[i];
            }
        }
        //Initializing all of our fields
        address = p.getAddress();
        port = p.getPort();
        type = header[0];
        extension = header[1];
        connectionID = new byte[2];
        connectionID[0] = header[2];
        connectionID[1] = header[3];
        temp = new byte[4];
        temp[0] = header[4];
        temp[1] = header[5];
        temp[2] = header[6];
        temp[3] = header[7];
        timestamp = bytesToInt(temp);
        temp[0] = header[8];
        temp[1] = header[9];
        temp[2] = header[10];
        temp[3] = header[11];
        timestampdifference = bytesToInt(temp);
        temp[0] = header[12];
        temp[1] = header[13];
        temp[2] = header[14];
        temp[3] = header[15];
        windowsize = bytesToInt(temp);
        temp = new byte[2];
        temp[0] = header[16];
        temp[1] = header[17];
        sequencenumber = bytesToInt(temp);
        temp[0] = header[18];
        temp[1] = header[19];
        acknumber = bytesToInt(temp);
        acks = 0;
    }

    //Returns whether the packet has a selective ack number
    public boolean hasSelectiveAck() {
        return hasSelectiveAck;
    }

    //Returns the send address
    public InetAddress getAddress() {
        return address;
    }

    //Returns the send port
    public int getPort() {
        return port;
    }

    //Returns the timestamp difference value
    public int getTimeStampDifference() {
        return timestampdifference;
    }

    //Returns the timestamp value
    public int getTimeStamp() {
        return timestamp;
    }

    //Returns the packet type
    public int getType() {
        return type;
    }

    //Returns the total packet size
    public int getSize() {
        return payload.length + header.length;
    }

    //Returns the connection ID
    public byte[] getConnectionID() {
        return connectionID;
    }

    //Returns the ack number
    public int getAckNumber() {
        return acknumber;
    }

    //Returns the sequence number
    public int getSequenceNumber() {
        return sequencenumber;
    }

    //Returns the send time
    public long getSendTime() {
        return sendtime;
    }

    //Returns the data payload array
    public byte [] getPayload() {
        return payload;
    }

    //Returns the data payload array
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    //Returns the number of times the packet has been acked
    public int getAcks() {
        return acks;
    }

    //Sets the number of times the packet has been acked
    public void setAcks(int n) {
        acks = n;
    }

    //Returns the number of times the packet has been resent
    public int getReSends() {
        return resends;
    }

    //Returns the extension byte
    public int getExtension() {
        return extension;
    }

    //Returns the selective ack number
    public int getSelectiveAckNumber() {
        return selectiveacknumber;
    }

    //Returns the window size value
    public int getWindowSize() {
        return windowsize;
    }

    //Converts an int to byte array
    private byte [] intToBytes (int input) {
        byte[] result = new byte[4];
        result[0] = (byte)(input >>> 24);
        result[1] = (byte)(input >>> 16);
        result[2] = (byte)(input >>> 8);
        result[3] = (byte) input;
        return result;
    }

    //Converts an array of bytes to an int
    private int bytesToInt(byte [] input) {
        if (input == null || input.length <= 0) {
            return 0;
        } else if (input.length == 1) {
            return  (input[0] & 0xFF);
        } else if (input.length == 2) {
            return (input[0] & 0xFF) << 8 | (input[1] & 0xFF);
        } else if (input.length == 3) {
            return (input[0] & 0xFF) << 16 | (input[1] & 0xFF) << 8 | (input[2] & 0xFF);
        } else {
            return (input[0] & 0xFF) << 24 | (input[1] & 0xFF) << 16 | (input[2] & 0xFF) << 8 | (input[3] & 0xFF);
        }
    }

    //Creates a UTPPacket from the given parameters
    public UTPPacket(int type, byte[] currentconnectionID, int timestampdifference, int currentwindow, int seq, int ack, byte [] data, InetAddress address, int port,int extension,int selectiveacknumber) {
        this.type = type;
        this.extension = extension;
        this.selectiveacknumber = selectiveacknumber;
        connectionID = currentconnectionID;
        windowsize = currentwindow;
        sequencenumber = seq;
        acknumber = ack;
        this.timestampdifference = timestampdifference;
        sendtime = System.nanoTime();
        timestamp = (int)((sendtime-((sendtime/1000000000/1000)*1000000000*1000))/1000);
        payload = data;
        this.address = address;
        this.port = port;
        acks = 0;
    }

    //Converts this UTP packet to a UDP packet so it can be sent through our Datagram Sockets
    public DatagramPacket getUDPPacket() {
        if (extension == 0) {
            header = new byte[20];
        } else {
            header = new byte[25];
        }
//        byte version_type = (byte)((1 & 0xFF) << 4 | (1 & 0xFF));
        header[0] = intToBytes(type)[3];
        header[1] = intToBytes(extension)[3];
        header[2] = connectionID[0];
        header[3] = connectionID[1];
        byte[] temp;
        temp = intToBytes(timestampdifference);
        header[8] = temp[0];
        header[9] = temp[1];
        header[10] = temp[2];
        header[11] = temp[3];
        temp = intToBytes(windowsize);
        header[12] = temp[0];
        header[13] = temp[1];
        header[14] = temp[2];
        header[15] = temp[3];
        temp = intToBytes(sequencenumber);
        header[16] = temp[2];
        header[17] = temp[3];
        temp = intToBytes(acknumber);
        header[18] = temp[2];
        header[19] = temp[3];
        if (extension == 1) {
            temp = intToBytes(selectiveacknumber);
            header[20] = 1;
            header[21] = 3;
            header[22]= temp[2];
            header[23] = temp[3];
            header[24] = 0;
        }
        sendtime = System.nanoTime();
        timestamp = (int)((sendtime-((sendtime/1000000000/1000)*1000000000*1000))/1000);
        temp = intToBytes(timestamp);
        header[4] = temp[0];
        header[5] = temp[1];
        header[6] = temp[2];
        header[7] = temp[3];
        byte [] data = new byte[header.length+payload.length];
        for (int i = 0;i < data.length;i++) {
            if (i < header.length) {
                data[i] = header[i];
            } else {
                data[i] = payload[i-header.length];
            }
        }
        return new DatagramPacket(data, data.length,address, port);
    }

}
