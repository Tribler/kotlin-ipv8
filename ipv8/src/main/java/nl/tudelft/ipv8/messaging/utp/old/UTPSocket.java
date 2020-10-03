package nl.tudelft.ipv8.messaging.utp.old;

/*
Created by Peter Lipay. University of Washington. June 2010.
*/

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class UTPSocket {
    /**
     * Regular data packet. Socket is in connected state and has data to send. An ST_DATA packet always has a data payload.
     */
    public static final byte ST_DATA = 0;
    /**
     * Finalize the connection. This is the last packet. It closes the connection, similar to TCP FIN flag. This connection will never have a sequence number greater than the sequence number in this packet. The socket records this sequence number as eof_pkt. This lets the socket wait for packets that might still be missing and arrive out of order even after receiving the ST_FIN packet.
     **/
    public static final byte ST_FIN = 1;
    /**
     * State packet. Used to transmit an ACK with no data. Packets that don't include any payload do not increase the seq_nr.
     */
    public static final byte ST_STATE = 2;
    /**
     * Regular data packet. Socket is in connected state and has data to send. An ST_DATA packet always has a data payload.
     */
    public static final byte ST_RESET = 3;
    /**
     * Connect SYN. Similar to TCP SYN flag, this packet initiates a connection. The sequence number is initialized to 1. The connection ID is initialized to a random number. The syn packet is special, all subsequent packets sent on this connection (except for re-sends of the ST_SYN) are sent with the connection ID + 1. The connection ID is what the other end is expected to use in its responses.
     * <p>
     * When receiving an ST_SYN, the new socket should be initialized with the ID in the packet header. The send ID for the socket should be initialized to the ID + 1. The sequence number for the return channel is initialized to a random number. The other end expects an ST_STATE packet (only an ACK) in response.
     */
    public static final byte ST_SYN = 4;
    //Maximum size of a UDP packet
    public static final int MAXUDPLENGTH = 65515;
    //The send buffer which stores the bytes that are waiting to be sent
    private static CircularBuffer sendbuffer;
    //The receive buffer which stores the bytes that have been received and are waiting to be read by the user
    private static CircularBuffer receivebuffer;
    //This is the main socket which is used to send and receive all our packets
    DatagramSocket socket;
    int lasttimestampdifference = 0;
    //Used for debug purposes, if you want to print out what the send rate is
    double bytessent = 0;
    long debugtimer = 0;
    boolean waitedafterclose = false;
    BlockingQueue<UTPPacket> packetBuffer = new LinkedBlockingQueue<>();
    //To keep the connection stable when there are micro-spikes in latency, we buffer the last several
    //delay values and pick the smallest one from the buffer. The below constant adjusts the buffer size.
    //I've set it at 10 as that seemed to work best in my tests, but if you are getting weird behavior
    //(like utp not backing off properly or overshooting the connection),
    //you can try modifying this value downward to as low as 3.
    private int CUR_DELAY_BUFFER_SIZE = 10;
    //This is the physical buffer structure
    private ArrayList<mindelaytuple> curdelaylist = new ArrayList<mindelaytuple>();
    //The 2 byte long connection ID for the incoming connection
    private byte[] currentconnectionIDReceiveBytes;
    //The 2 byte long connection ID for the outgoing connection
    private byte[] currentconnectionIDSendBytes;
    //The current sec number
    private int currentSequenceNumber;
    //The latest received acknumber
    private int currentacknumber;
    //The last timestamp difference that was calculated by this socket
    private int timestampdifference;
    //The address being sent to
    private InetAddress sendAddress;
    //The port being sent to
    private int sendPort;
    //Self explanatory
    private int currentReceiveWindowSize;
    private int currentSendWindowSize;
    private int maxReceiveWindowSize;
    private int maxSendWindowSize;
    //Amount of bytes left in the receive buffer of the other end of the connection
    private int otherReceiveWindowRemainingSize = Integer.MAX_VALUE;
    //Maximum number of connection attempts when first initializing connection
    private int maxconnectretransmitions = 2;
    //Maximum timeout on the initializing connections
    private int maxconnecttimeout = 10000;
    //Whether the socket has sent out its fin packet to close the connection
    private boolean sentfin = false;
    //Whether the socket has received a fin packet
    private boolean gotfin = false;
    //Whether the socket has been closed by the user
    private boolean closed = false;
    //Whether the connection has been abruptly disconnected
    private boolean interupted = false;
    private ArrayList<Integer> mindelaylist = new ArrayList<Integer>();
    private long mindelayLastTimestamp = System.nanoTime();
    //The send window
    private NetworkingWindow sendwindow;
    //The receive window
    private NetworkingWindow receivewindow;
    //These values are used to compute the retransmission timeout
    private int rtt = 0;
    private int rtt_var = 0;

    //The packet types
    private int timeout = 1000;
    //Keeps track of the number of consecutive timeouts we've had
    private int consecutivetimeouts = 0;
    //Number of consecutive timeouts before we give up
    private int consecutivetimeoutlimit = 3;
    //The packet size being used
    private int packetsize = 1300;
    //Our latency target value, this is in milliseconds
    private int CCONTROL_TARGET = 100;
    //The maximum change in the max send window on any given retransmission
    private int MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000;
    //Stores the timestamp for the window decay timer,
    //which makes sure we don't throttle down too rapidly on packet-loss
    private long window_decay_timer = System.nanoTime();
    //The max decay in milliseconds
    private int MAX_WINDOW_DECAY = 100;
    //The base_delay for the socket
    private int base_delay = 0;
    //If this is the first ack we've processed, used to update the base_delay properly
    private boolean first_time_updating_delay = true;
    //The lock used to keep the two threads from causing trouble
    private ReentrantLock lock = new ReentrantLock(false);

    //This initializes a new UTPSocket, this is the initializer for use by the client
    public UTPSocket(DatagramSocket socket, InetAddress address, int port) throws Exception {
        this.socket = socket;
        maxReceiveWindowSize = 40000000;
        maxSendWindowSize = packetsize;
        currentReceiveWindowSize = 0;
        currentSendWindowSize = 0;
        receivebuffer = new CircularBuffer(40000000);
        sendbuffer = new CircularBuffer(40000000);
        sendwindow = new NetworkingWindow(100000);
        receivewindow = new NetworkingWindow(100000);
        sendAddress = address;
        sendPort = port;
        //Initializing a random connection ID
        Random rand = new Random(System.nanoTime());
        currentconnectionIDReceiveBytes = new byte[2];
        currentconnectionIDSendBytes = new byte[2];
        rand.nextBytes(currentconnectionIDReceiveBytes);
        int connectionIDReceive = bytesToInt(currentconnectionIDReceiveBytes);
        if (connectionIDReceive == Short.MAX_VALUE) {
            connectionIDReceive--;
            currentconnectionIDReceiveBytes[0] = intToBytes(connectionIDReceive)[2];
            currentconnectionIDReceiveBytes[1] = intToBytes(connectionIDReceive)[3];
        }
        currentconnectionIDSendBytes[0] = intToBytes(connectionIDReceive + 1)[2];
        currentconnectionIDSendBytes[1] = intToBytes(connectionIDReceive + 1)[3];
        currentSequenceNumber = 1;
    }

    //Initializes a UTPSocket, this constructor is used by the UTPServerSocket class
    public UTPSocket(DatagramSocket socket, InetAddress address, int port, byte[] connectionIDSendBytes, byte[] connectionIDReceiveBytes, int sequenceNumber) throws IOException {
        this.socket = socket;
        maxReceiveWindowSize = socket.getReceiveBufferSize() * 10;
        maxSendWindowSize = packetsize;
        currentReceiveWindowSize = 0;
        currentSendWindowSize = 0;
        receivebuffer = new CircularBuffer(4000000);
        sendbuffer = new CircularBuffer(4000000);
        sendwindow = new NetworkingWindow(100000);
        receivewindow = new NetworkingWindow(100000);
        sendAddress = address;
        sendPort = port;
        this.currentconnectionIDReceiveBytes = connectionIDReceiveBytes;
        this.currentconnectionIDSendBytes = connectionIDSendBytes;
        currentacknumber = 2;
        currentSequenceNumber = sequenceNumber;
        currentSequenceNumber++;
    }

    public void initializeSender() throws IOException {
        //Start the connection
        InitializeConnection(sendAddress, sendPort);
        socket.setSoTimeout(10000);
        ReceiverThread receive = new ReceiverThread();
        SenderThread send = new SenderThread();
        receive.start();
        send.start();
    }

    public void initializeReceiver() throws IOException {
        UTPPacket confirmConnection = new UTPPacket(ST_STATE, currentconnectionIDSendBytes, timestampdifference, currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], sendAddress, sendPort, 0, -1);
        //We send a connection confirm packet
        UTPSocketBinder.send(socket, confirmConnection);
        socket.setSoTimeout(maxconnecttimeout);
        //Now we wait for that packet to get acked
        long start = System.nanoTime();
        long current = start;
        boolean connected = false;
        while (current - start - 3000000000L < 0) {
            UTPPacket confirmation;
            try {
                confirmation = packetBuffer.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            if (confirmation.getType() == ST_STATE && confirmation.getAddress().equals(sendAddress) && confirmation.getConnectionID()[0] == currentconnectionIDSendBytes[0] && confirmation.getConnectionID()[1] == currentconnectionIDSendBytes[1]) {
                connected = true;
                break;
            }
            current = System.nanoTime();
        }
        //If no ack was received, we throw an Exception
        if (!connected) {
            throw new SocketTimeoutException("Did not receive ack for Connection Confirm packet");
        }
        //Otherwise, the connection was successfully established

        ReceiverThread receive = new ReceiverThread();
        SenderThread send = new SenderThread();
        receive.start();
        send.start();
    }

    //This establishes the connection by sending a SYN packet to the other end and waiting for a reply
    private void InitializeConnection(InetAddress address, int port) throws IOException {
        boolean connected = false;
        for (int i = 0; i < maxconnectretransmitions; i++) {
            UTPPacket initializeConnection = new UTPPacket(ST_SYN, currentconnectionIDReceiveBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], address, port, 0, -1);
            //Send the initial SYN packet
            UTPSocketBinder.send(socket, initializeConnection);
            //Wait for a connection confirm packet
            long start = System.nanoTime();
            long current = start;
            while (current - start - 3000000000L < 0) {
                socket.setSoTimeout(maxconnecttimeout);
                UTPPacket confirmation;
                try {
                    confirmation = packetBuffer.take();
                    System.out.println("hoi");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                System.out.println("hoi2");
                //If we get a legitimate connection confirm packet
                if (confirmation.getType() == ST_STATE && confirmation.getAddress().equals(address) && confirmation.getConnectionID()[0] == currentconnectionIDReceiveBytes[0] && confirmation.getConnectionID()[1] == currentconnectionIDReceiveBytes[1]) {
                    currentacknumber = confirmation.getAckNumber();
                    sendAddress = confirmation.getAddress();
                    sendPort = confirmation.getPort();
                    connected = true;
                    UTPPacket ConnectionConfirmAck = new UTPPacket(ST_STATE, currentconnectionIDReceiveBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], sendAddress, sendPort, 0, -1);
                    //Send an Ack of the Connection Confirm packet back to the other end
                    UTPSocketBinder.send(socket, ConnectionConfirmAck);
                    break;
                    //We're done, connection is established
                }
                current = System.nanoTime();
            }
            if (connected) {
                break;
            }
        }
        if (!connected) {
            throw new SocketException("Couldn't establish connection with remote host");
        }
    }

    //Returns a new InputStream for this socket
    public InputStream getInputStream() {
        return new UTPInputStream();
    }

    //Returns a new OutputStream for this socket
    public OutputStream getOutputStream() {
        return new UTPOutputStream();
    }

    //closes this socket, causing a FIN packet to be sent and the connection to be terminated
    //Any bytes already in the send buffer will be sent out before the connection terminates.
    public void close() {
        closed = true;
    }

    //Returns a byte array representing the given int
    private byte[] intToBytes(int input) {
        byte[] result = new byte[4];
        result[0] = (byte) (input >>> 24);
        result[1] = (byte) (input >>> 16);
        result[2] = (byte) (input >>> 8);
        result[3] = (byte) input;
        return result;
    }

    //Returns an int representing the given byte array (assuming it is up to 4 bytes long)
    private int bytesToInt(byte[] input) {
        if (input == null || input.length <= 0) {
            return 0;
        } else if (input.length == 1) {
            return (input[0] & 0xFF);
        } else if (input.length == 2) {
            return (input[0] & 0xFF) << 8 | (input[1] & 0xFF);
        } else if (input.length == 3) {
            return (input[0] & 0xFF) << 16 | (input[1] & 0xFF) << 8 | (input[2] & 0xFF);
        } else {
            return (input[0] & 0xFF) << 24 | (input[1] & 0xFF) << 16 | (input[2] & 0xFF) << 8 | (input[3] & 0xFF);
        }
    }

    //This Circular Buffer class is used by the send and receive buffers
    private static class CircularBuffer {
        private Byte[] data;
        private int head;
        private int tail;

        public CircularBuffer(Integer number) {
            data = new Byte[number];
            head = 0;
            tail = 0;
        }

        //Stores an array of bytes into the buffer (this cuts down on the number of function calls)
        public boolean storepacket(byte[] input) {
            synchronized (this) {
                if (data.length - this.size() < input.length) {
                    return false;
                }
                int i = 0;
                while (i < input.length) {
                    data[tail++] = input[i];
                    if (tail == data.length) {
                        tail = 0;
                    }
                    i++;
                }
                return true;
            }
        }

        //Stores a single byte into the buffer
        public boolean store(byte value) {
            synchronized (this) {
                if (bufferFull()) {
                    return false;
                }
                data[tail++] = value;
                if (tail == data.length) {
                    tail = 0;
                }
                return true;
            }
        }

        //returns the size of the buffer
        public int size() {
            synchronized (this) {
                if (head > tail) {
                    return data.length - head + tail;
                } else {
                    return tail - head;
                }
            }
        }

        //Returns a byte array of the given size from the buffer, assumes size of buffer is > than sizeofpacket
        public byte[] readpacket(int sizeofpacket) {
            synchronized (this) {
                byte[] newpacket = new byte[sizeofpacket];
                int i = 0;
                while (i < newpacket.length) {
                    newpacket[i] = data[head++];
                    if (head == data.length) {
                        head = 0;
                    }
                    i++;
                }
                return newpacket;
            }
        }

        //Returns a single byte, or -1 if the buffer is empty
        public byte read() {
            synchronized (this) {
                if (head != tail) {
                    byte value = data[head++];
                    if (head == data.length) {
                        head = 0;
                    }
                    return value;
                } else {
                    return -1;
                }
            }
        }

        //returns whether the buffer is full
        private boolean bufferFull() {
            synchronized (this) {
                if (tail + 1 == head) {
                    return true;
                }
                return tail == (data.length - 1) && head == 0;
            }
        }
    }

    //The units stored in the base-delay list, timestamp is used to evict older entries,
    //difference is the actual time-stamp difference value being stored
    private class mindelaytuple {
        public long timestamp;
        public int difference;

        public mindelaytuple(long x, int y) {
            timestamp = x;
            difference = y;
        }

    }

    //The send thread
    private class SenderThread extends Thread {
        public void run() {
            debugtimer = System.nanoTime();
            while (true) {
                lock.lock();
                System.out.println("nl.tudelft UTPSocket.SenderThread - New iteration");
                try {
                    if (System.nanoTime() - debugtimer >= 1000000000) {
                        bytessent = 0;
                        debugtimer = System.nanoTime();
                    }
                    System.out.println("nl.tudelft UTPSocket.SenderThread - 1:" + sendwindow.size());

                    //If we've timed out, we resend all packets in the send window and set the max window size to one packet size
                    if (sendwindow.size() > 0 && (System.nanoTime() - sendwindow.get(sendwindow.size() - 1).getSendTime()) / 1000000 >= timeout) {
                        System.out.println("nl.tudelft UTPSocket.SenderThread - 1.1");
                        maxSendWindowSize = packetsize;

                        //If we've exceeded the consecutive timeout limit, we assume the connection has been lost, and throw an exception
                        if (consecutivetimeouts >= consecutivetimeoutlimit) {
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 1.2");
                            interupted = true;
                            closed = true;
                            lock.unlock();
                            break;
                        } else if (consecutivetimeouts > 0) {
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 1.3");
                            timeout = timeout * 2;
                        }
                        for (int i = 0; i < sendwindow.size(); i++) {
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 1.4");
                            UTPSocketBinder.send(socket, sendwindow.get(i));
                        }
                        System.out.println("nl.tudelft UTPSocket.SenderThread - 1.5");
                        consecutivetimeouts++;
                    }
                    System.out.println("nl.tudelft UTPSocket.SenderThread - 2");

                    //if there's anything in the send-buffer that needs to be sent, we will send it
                    if (sendbuffer.size() > 0) {

                        System.out.println("nl.tudelft UTPSocket.SenderThread - 3");
                        //If the window has enough room for a full packet,
                        //and there's a full packetsize worth of data to be sent in the send buffer, send it
                        if (maxSendWindowSize - currentSendWindowSize >= packetsize && sendbuffer.size() >= packetsize - 20 && otherReceiveWindowRemainingSize >= packetsize) {
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 4");
                            lock.unlock();
                            int datatosendsize = packetsize - 20;
                            byte[] datatosend = sendbuffer.readpacket(datatosendsize);
                            currentSequenceNumber++;
                            //We initialize our UTP packet to be sent
                            UTPPacket datapacket = new UTPPacket(ST_DATA, currentconnectionIDSendBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, datatosend, sendAddress, sendPort, 0, -1);
                            consecutivetimeouts = 0;
                            lock.lock();
                            //Here we convert the UTP packet to a UDP packet so we can send it through our datagram socket
                            UTPSocketBinder.send(socket, datapacket);
                            //bytes sent is just for debug purposes, allows us to calculate the send rate
                            bytessent += datapacket.getSize() - 20;
                            //add the datapacket to the send window
                            sendwindow.add(datapacket);
                            //increment the current window size
                            currentSendWindowSize += datatosend.length + 20;
                            //Otherwise, if there's less than one packetsize of data to be sent, only send it if the window is currently empty, otherwise
                            //wait until we have a full packetsize of data to send
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 5");
                        } else if ((maxSendWindowSize > 0 && currentSendWindowSize == 0 && otherReceiveWindowRemainingSize > 20) || (closed && currentSendWindowSize + 20 < maxSendWindowSize)) {
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 6");
                            int datatosendsize = Math.min(Math.min(Math.min(sendbuffer.size(), packetsize - 20), maxSendWindowSize - currentSendWindowSize - 20), otherReceiveWindowRemainingSize - 20);
                            lock.unlock();
                            byte[] datatosend = sendbuffer.readpacket(datatosendsize);
                            currentSequenceNumber++;
                            //We initialize our UTP packet to be sent
                            UTPPacket datapacket = new UTPPacket(ST_DATA, currentconnectionIDSendBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, datatosend, sendAddress, sendPort, 0, -1);
                            lock.lock();
                            consecutivetimeouts = 0;
                            //Here we convert the UTP packet to a UDP packet so we can send it through our datagram socket
                            UTPSocketBinder.send(socket, datapacket);
                            //bytes sent is just for debug purposes, allows us to calculate the send rate
                            bytessent += datapacket.getSize() - 20;
                            //add the datapacket to the send window
                            sendwindow.add(datapacket);
                            //increment the current window size
                            currentSendWindowSize += datatosend.length + 20;
                            System.out.println("nl.tudelft UTPSocket.SenderThread - 7");
                        }
                        //If the window has been closed by the user, send a fin packet
                    } else if (closed && !sentfin && !gotfin && sendwindow.size() == 0) {
                        System.out.println("nl.tudelft UTPSocket.SenderThread - 8");
                        currentSequenceNumber++;
                        UTPPacket synpacket = new UTPPacket(ST_FIN, currentconnectionIDSendBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], sendAddress, sendPort, 0, -1);
                        UTPSocketBinder.send(socket, synpacket);
                        sendwindow.add(synpacket);
                        currentSendWindowSize += 20;
                        sentfin = true;
                        //Once the fin packet has been acknowledged by the other end, the send thread terminates
                        System.out.println("nl.tudelft UTPSocket.SenderThread - 9");
                    } else if (sendwindow.size() == 0 && closed) {
                        System.out.println("nl.tudelft UTPSocket.SenderThread - 10");
                        lock.unlock();
                        break;
                    }
                    System.out.println("nl.tudelft UTPSocket.SenderThread - 11");
                    sleep(1000);
                    lock.unlock();
                } catch (Exception e) {
                    lock.unlock();
                    e.printStackTrace();
                }
            }
        }
    }

    //The receive thread
    private class ReceiverThread extends Thread {
        public void run() {
            System.out.println("nl.tudelft UTPSocket.ReceiverThread - New iteration");
            while (true) {
                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 1");
                //If the user has closed this socket, we terminate the receive thread
                if ((closed && sendbuffer.size() == 0 && sendwindow.size() == 0) || interupted) {
                    if (waitedafterclose) {
                        break;
                    } else {
                        waitedafterclose = true;
                    }
                }
                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 2");
                try {
                    //Receive any incoming UDP packets
                    UTPPacket next = packetBuffer.take();
                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 3");
                    try {
                        //Make sure the packet is from the correct IP address and port number, and that
                        //its connection ID is correct, which means its part of this connection
                        if (sendAddress.equals(next.getAddress()) && sendPort == next.getPort() && next.getConnectionID()[0] == currentconnectionIDReceiveBytes[0] && next.getConnectionID()[1] == currentconnectionIDReceiveBytes[1]) {
                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 4");
                            lock.lock();
                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 4.1");
                            //Get the current timestamp
                            long systemtime = System.nanoTime();
                            //Convert it from nanoseconds into microseconds (we use microseconds for our timestamps to minimize wrapping)
                            int timestamp = (int) ((systemtime - ((systemtime / 1000000000 / 1000) * 1000000000 * 1000)) / 1000);
                            //Calculate the difference between the current timestamp and the timestamp in the received packet,
                            //this value will be sent back to the sender in our next packet
                            timestampdifference = timestamp - next.getTimeStamp();
                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 4.2");
                            lock.unlock();
                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 4.3");
                            //If it's a fin packet, we're entering closing mode
                            if (next.getType() == ST_FIN) {
                                gotfin = true;
                            }
                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 5");
                            //If its a Data Packet or Fin packet, we'll need to send an acknowledgement
                            if (next.getType() == ST_DATA || next.getType() == ST_FIN) {
                                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 6");
                                //If this is the next packet we're expecting (meaning its an in order packet)
                                if (next.getSequenceNumber() == currentacknumber) {
                                    //Read the data payload from the packet into our receivebuffer
                                    byte[] currentdata = next.getPayload();
                                    if (receivebuffer != null) {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 7");
                                        if (receivebuffer.storepacket(currentdata)) {
                                            currentacknumber++;
                                        }
                                    }
                                    //If this packet filled in a gap in our receive window and there's now consecutive packets
                                    //in the receivewindow, we remove them and read them into the receive buffer
                                    while (receivewindow.size() > 0 && receivewindow.get(0).getSequenceNumber() == currentacknumber) {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 8");
                                        UTPPacket removed = receivewindow.getAndRemove();
                                        currentdata = removed.getPayload();
                                        if (receivebuffer != null) {
                                            //if the receivebuffer is already full, it means the sender is sending to quickly
                                            //so we break and don't send an ack so that the sender will slow down
                                            if (!receivebuffer.storepacket(currentdata)) {
                                                receivewindow.add(removed, 0);
                                                break;
                                            }
                                        }
                                        currentReceiveWindowSize -= removed.getSize();
                                        currentacknumber++;
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 9");
                                    }
                                    if (gotfin && receivewindow.size() == 0) {
                                        closed = true;
                                    }
                                    //Send the ack for the current packet
                                    UTPPacket ack = new UTPPacket(ST_STATE, currentconnectionIDSendBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], sendAddress, sendPort, 0, -1);
                                    UTPSocketBinder.send(socket, ack);
                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 10");
                                    //If the packet was received out of sequence, we'll add it to our Receive Window
                                } else if (next.getSequenceNumber() > currentacknumber) {
                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 11");
                                    boolean wasadded = false;
                                    //If the receive window is empty, add the packet to the start of the receive-window
                                    if (receivewindow.size() == 0) {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 12");
                                        if (next.getSize() + (next.getSequenceNumber() - currentacknumber) * packetsize <= maxReceiveWindowSize) {
                                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 13");
                                            //This approximates the receive window size. Since the packets between acknumber and this packet
                                            //haven't arrived yet, we assume that all the packets in between were maximum size. As they come in
                                            //and fill the receive-window, if they are smaller than max packet size, we'll decrease the window size accordingly
                                            currentReceiveWindowSize = next.getSize() + (next.getSequenceNumber() - currentacknumber) * packetsize;
                                            receivewindow.add(next);
                                            wasadded = true;
                                        }
                                        //If the receivewindow isn't empty, we need to traverse it to insert this packet into the correct position
                                    } else {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 14");
                                        //The largest sequence number currently in the window
                                        int biggestsequencenumber = receivewindow.get(receivewindow.size() - 1).getSequenceNumber();
                                        //Max possible size is the largest possible size that the window will be after this operation
                                        int maxpossiblesize;
                                        //If the current packet is greater than biggestsequencenumber, it means we're expanding the receive window
                                        if (next.getSequenceNumber() - biggestsequencenumber > 0) {
                                            maxpossiblesize = currentReceiveWindowSize + next.getSize() + (next.getSequenceNumber() - 1 - biggestsequencenumber) * packetsize;
                                            //If not, it means we're inserting somewhere in the middle of the window, so if the current packetsize
                                            //is less than max packet size (which we had assumed it would be in its absence), we actually adjust the
                                            //current window size downward by the difference
                                        } else {
                                            maxpossiblesize = currentReceiveWindowSize - (packetsize - next.getSize());
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 15");
                                        //If we won't exceed the max receive size, we insert this packet into the window
                                        if (maxpossiblesize <= maxReceiveWindowSize) {
                                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 16");
                                            for (int i = 0; i < receivewindow.size(); i++) {
                                                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 17");
                                                if (receivewindow.get(i).getSequenceNumber() == next.getSequenceNumber()) {
                                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 18");
                                                    //If this packet is already in the window, we don't need to add it again
                                                    break;
                                                } else if (receivewindow.get(i).getSequenceNumber() > next.getSequenceNumber()) {
                                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 19");
                                                    receivewindow.add(next, i);
                                                    wasadded = true;
                                                    currentReceiveWindowSize = maxpossiblesize;
                                                    break;
                                                } else if (i + 1 == receivewindow.size()) {
                                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 20");
                                                    receivewindow.add(next);
                                                    wasadded = true;
                                                    currentReceiveWindowSize = maxpossiblesize;
                                                }
                                            }
                                        }
                                    }
                                    //If we added the packet to the window, send out an acknowledgement
                                    if (wasadded) {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 21");
                                        UTPPacket ack = new UTPPacket(ST_STATE, currentconnectionIDSendBytes, timestampdifference, maxReceiveWindowSize - currentReceiveWindowSize, currentSequenceNumber, currentacknumber, new byte[0], sendAddress, sendPort, 1, next.getSequenceNumber());
                                        UTPSocketBinder.send(socket, ack);
                                    }
                                }
                            }
                            //Here we process the acknumber in the received ack packet
                            if (next.getType() == ST_STATE) {
                                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 22");
                                lock.lock();
                                otherReceiveWindowRemainingSize = next.getWindowSize();
                                if (sendwindow.size() > 0) {
                                    System.out.println("nl.tudelft UTPSocket.ReceiverThread - 23");
                                    //If the acknumber is within the sendwindow
                                    if ((next.getAckNumber()) - sendwindow.get(0).getSequenceNumber() >= 0) {
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 24");
                                        //Find the packet that's being acked (as the next packet that's expected to be received)
                                        UTPPacket ackedpacket = sendwindow.find(next.getAckNumber());
                                        if (ackedpacket != null) {
                                            //Increment that packet's ack-counter, if this exceeds 3, this packet has likely been lost
                                            ackedpacket.setAcks(ackedpacket.getAcks() + 1);
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 25");
                                        //This tallies the number of bytes that were acked by this ack packet
                                        int bytesacked = 0;
                                        //Remove all the packets in the send window that have a smaller sequence number than
                                        //the acked packet
                                        while (next.getAckNumber() - sendwindow.get(0).getSequenceNumber() > 0) {
                                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 26");
                                            UTPPacket current = sendwindow.getAndRemove();
                                            if (current.getReSends() == 0) {
                                                //This re-calculates the retransmission timeout
                                                int rttpacket = ((int) (System.nanoTime() - current.getSendTime())) / 1000000;
                                                int delta = rtt - rttpacket;
                                                rtt_var += (Math.abs(delta) - rtt_var) / 4;
                                                rtt += (rttpacket - rtt) / 8;
                                                timeout = Math.max(rtt + (rtt_var * 4), 500);
                                            }
                                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 27");
                                            bytesacked += current.getSize();
                                            currentSendWindowSize -= current.getSize();
                                            if (sendwindow.size() == 0) {
                                                break;
                                            }
                                        }
                                        //This is a rough wrapping protection, if it looks like the timestamp difference values have started wrapping,
                                        //we'll reset our base_delay values
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 28");
                                        if ((lasttimestampdifference < 0 && next.getTimeStampDifference() > 0 && Math.abs(lasttimestampdifference) + Math.abs(next.getTimeStampDifference()) > ((double) Integer.MAX_VALUE * .8))
                                            || (lasttimestampdifference > 0 && next.getTimeStampDifference() < 0 && Math.abs(lasttimestampdifference) + Math.abs(next.getTimeStampDifference()) > ((double) Integer.MAX_VALUE * .8))) {
                                            while (mindelaylist.size() > 0) {
                                                mindelaylist.remove(0);
                                            }
                                            while (curdelaylist.size() > 0) {
                                                curdelaylist.remove(0);
                                            }
                                            first_time_updating_delay = true;
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 29");
                                        lasttimestampdifference = next.getTimeStampDifference();
                                        //If we're updating the delay for the first time, we'll set the base_delay to whatever the current
                                        //timestamp difference was
                                        if (first_time_updating_delay) {
                                            mindelaylist.add(lasttimestampdifference);
                                            base_delay = lasttimestampdifference;
                                            mindelayLastTimestamp = System.nanoTime();
                                            //Otherwise, we'll try to update the base_delay value for the current second.
                                        } else {
                                            int curindex = mindelaylist.size() - 1;
                                            mindelaylist.set(curindex, Math.min(mindelaylist.get(curindex), lasttimestampdifference));
                                            if (lasttimestampdifference < base_delay) {
                                                base_delay = lasttimestampdifference;
                                            }
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 30");
                                        //Once a minute, we'll shift our base_delay window forward by a minute, and recalculate our base_delay
                                        if (System.nanoTime() - 60000000000L > mindelayLastTimestamp) {
                                            mindelaylist.add(Integer.MAX_VALUE);
                                            //Our base_delay window is 13 minutes long. For each minute, we keep
                                            //track of the smallest base_delay value of that minute. Then, we
                                            //re-compute the base_delay to be the min of the values for all 13 minutes.
                                            if (mindelaylist.size() > 13) {
                                                mindelaylist.remove(0);
                                            }
                                            base_delay = mindelaylist.get(0);
                                            for (int i = 1; i < mindelaylist.size(); i++) {
                                                base_delay = Math.min(mindelaylist.get(i), base_delay);
                                            }
                                            mindelayLastTimestamp = System.nanoTime();
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 31");
                                        //our_delay will be equal to timestamp_difference - base_delay
                                        int our_delay = 0;
                                        if (first_time_updating_delay) {
                                            our_delay = 0;
                                            //We try to maintain stability when there are micro-spikes on the connection by
                                            //buffering the last couple our_delay values and always using the smallest one from the list
                                            //modifying the below number changes the size of this buffer
                                        } else if (curdelaylist.size() < CUR_DELAY_BUFFER_SIZE) {
                                            curdelaylist.add(new mindelaytuple(System.nanoTime(), next.getTimeStampDifference() - base_delay));
                                        } else {
                                            curdelaylist.remove(0);
                                            curdelaylist.add(new mindelaytuple(System.nanoTime(), next.getTimeStampDifference() - base_delay));
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 32");
                                        //Here we're picking the minimum our_delay from our buffer
                                        if (curdelaylist.size() > 0) {
                                            our_delay = curdelaylist.get(0).difference;
                                            for (int i = 1; i < curdelaylist.size(); i++) {
                                                our_delay = Math.min(our_delay, curdelaylist.get(i).difference);
                                            }
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 33");
                                        //Off_target is equal to the Control Target - our_delay, since CCONTROL_TARGET is in milliseconds
                                        //and our_delay is in microseconds, which is why we multiply CCONTROL_TARGET by 1000
                                        double off_target = CCONTROL_TARGET * 1000 - ((double) our_delay);
                                        //Here we clamp the off_target value so that it's within one CCONTROL_TARGET from 0
                                        if (off_target < 0) {
                                            off_target = Math.max(off_target, CCONTROL_TARGET * -1000);
                                        } else {
                                            off_target = Math.min(off_target, CCONTROL_TARGET * 1000);
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 34");
                                        //Now we scale off_target in units of the CCONTROL_TARGET. Because of the
                                        //above clamping, this means that delay_factor will always be between -1 and 1.
                                        double delay_factor = off_target / (double) (CCONTROL_TARGET * 1000);
                                        //Window factor is the percentage of our send window that the current packet has acked
                                        double window_factor = 1;
                                        if (maxSendWindowSize > 0) {
                                            window_factor = Math.min((double) bytesacked / (double) maxSendWindowSize, 1);
                                        }
                                        //This is the final value by which we will increment our send window
                                        int scaled_gain = (int) (delay_factor * window_factor * MAX_CWND_INCREASE_PACKETS_PER_RTT);
                                        if (!first_time_updating_delay) {
                                            maxSendWindowSize = Math.max(maxSendWindowSize + scaled_gain, 1300);
                                        } else {
                                            first_time_updating_delay = false;
                                        }
                                        System.out.println("nl.tudelft UTPSocket.ReceiverThread - 35");
                                        //If the packet's extension bit is turned on and it has a selective ack, it means it's selectively acking
                                        //a packet in our send_window, so we find that packet and remove it from our send window
                                        if (next.getExtension() == 1 && next.hasSelectiveAck()) {
                                            sendwindow.findAndRemove(next.getSelectiveAckNumber());
                                        }
                                        //If this is the 4'th duplicate ack in a row, we assume the packet is lost and need to resend it
                                        if (ackedpacket != null && ackedpacket.getAcks() >= 4 && ackedpacket.getAcks() % 4 == 0) {
                                            System.out.println("nl.tudelft UTPSocket.ReceiverThread - 36");
                                            //So we aren't constantly resending the same packet, we have a window_decay timer, which
                                            //we use to only resend the packet every MAX_WINDOW_DECAY interval
                                            if (System.nanoTime() - window_decay_timer >= MAX_WINDOW_DECAY * 1000000) {
                                                maxSendWindowSize = Math.max((int) (maxSendWindowSize * .78), 1300);
                                                window_decay_timer = System.nanoTime();
                                                //As packet loss often happens in bursts, we actually resend up to 3 packets
                                                for (int i = 0; i < Math.min(sendwindow.size(), 3); i++) {
                                                    UTPSocketBinder.send(socket, sendwindow.get(i));
                                                }
                                            }
                                        }
                                    }
                                }
                                lock.unlock();
                                System.out.println("nl.tudelft UTPSocket.ReceiverThread - 37");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //This is the UTPOutputStream class, it lets the user write bytes to the socket
    public class UTPOutputStream extends OutputStream {

        public void write(int towrite) throws IOException {
            byte[] bytes = intToBytes(towrite);
            synchronized (this) {
                if (interupted) {
                    throw new IOException("Connection Interrupted");
                } else if (sendbuffer == null || closed) {
                    System.out.println("!!!!!!!!!!!!!!!!!!!!!! Outputstream closed");
                    throw new IOException("Output stream has been closed");
                } else {
                    if (!sendbuffer.store(bytes[0])) {
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!! Sendbuffer full");
                        throw new IOException("Send Buffer is Already Full, you are writing faster than your connection can send out new packets");
                    }
                    if (!sendbuffer.store(bytes[1])) {
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!! Sendbuffer full");
                        throw new IOException("Send Buffer is Already Full, you are writing faster than your connection can send out new packets");
                    }
                    if (!sendbuffer.store(bytes[2])) {
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!! Sendbuffer full");
                        throw new IOException("Send Buffer is Already Full, you are writing faster than your connection can send out new packets");
                    }
                    if (!sendbuffer.store(bytes[3])) {
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!! Sendbuffer full");
                        throw new IOException("Send Buffer is Already Full, you are writing faster than your connection can send out new packets");
                    }
                }
            }
        }

        private byte[] intToBytes(int input) {
            byte[] result = new byte[4];
            result[0] = (byte) (input >>> 24);
            result[1] = (byte) (input >>> 16);
            result[2] = (byte) (input >>> 8);
            result[3] = (byte) input;
            return result;
        }

        public void close() throws IOException {
            super.close();

        }
    }

    //This is the first of the two custom Circular Buffer classes used by the UTPSocket.
    //Ideally, they could probably be combined into one Generic class, but Java Generic arrays
    //are a pain to code, and I take advantage of them being seperate to code more specific behavior into them
    //which allows me to minimize function calls somewhat.
    //This one is used by the Receive Window and the Send Window
    private class NetworkingWindow {
        private UTPPacket[] data;
        private int head;
        private int tail;

        public NetworkingWindow(Integer number) {
            data = new UTPPacket[number];
            head = 0;
            tail = 0;
        }

        //Adds a UTPPacket to the end of the buffer
        public void add(UTPPacket p) {
            synchronized (this) {
                if (bufferFull()) {
                    UTPPacket[] newdata = new UTPPacket[data.length * 2];
                    if (tail + 1 == head) {
                        for (int i = 0; i < tail + 1; i++) {
                            newdata[i] = data[i];
                        }
                        for (int i = data.length * 2 - (data.length - head); i < data.length * 2; i++) {
                            newdata[i] = data[head + (i - (data.length * 2 - (data.length - head)))];
                        }
                        head = data.length * 2 - (data.length - head);
                    }
                    data = newdata;
                }
                data[tail++] = p;
                if (tail == data.length) {
                    tail = 0;
                }
            }
        }

        //Adds a UTPPacket to the given index into the buffer
        public void add(UTPPacket p, int index) {
            synchronized (this) {
                if (bufferFull()) {
                    UTPPacket[] newdata = new UTPPacket[data.length * 2];
                    if (tail + 1 == head) {
                        for (int i = 0; i < tail + 1; i++) {
                            newdata[i] = data[i];
                        }
                        for (int i = data.length * 2 - (data.length - head); i < data.length * 2; i++) {
                            newdata[i] = data[head + (i - (data.length * 2 - (data.length - head)))];
                        }
                        head = data.length * 2 - (data.length - head);
                    }
                    data = newdata;
                }
                int pointer = head + index;
                if (pointer >= data.length) {
                    pointer -= data.length;
                }
                UTPPacket old = data[pointer];
                data[pointer] = p;
                while (pointer != tail) {
                    pointer++;
                    if (pointer == data.length) {
                        pointer = 0;
                    }
                    UTPPacket old2 = data[pointer];
                    data[pointer] = old;
                    old = old2;
                }
                tail++;
                if (tail == data.length) {
                    tail = 0;
                }
            }
        }

        //Removes and returns the first element from the buffer
        public UTPPacket getAndRemove() {
            synchronized (this) {
                if (head != tail) {
                    UTPPacket value = data[head++];
                    if (head == data.length) {
                        head = 0;
                    }
                    return value;
                } else {
                    return null;
                }
            }
        }

        //Finds and returns the UTPPacket with the given sequence number in the buffer.
        //Returns null if it isn't found
        public UTPPacket find(int sequencenumber) {
            synchronized (this) {
                if (head != tail) {
                    int pointer = head;
                    while (true) {
                        if (data[pointer].getSequenceNumber() == sequencenumber) {
                            return data[pointer];
                        } else {
                            pointer++;
                            if (pointer == data.length) {
                                pointer = 0;
                            }
                            if (pointer == tail) {
                                return null;
                            }
                        }

                    }
                } else {
                    return null;
                }
            }
        }

        //Deletes the UTPPacket with the given sequencenumber from the buffer
        public void findAndRemove(int sequencenumber) {
            synchronized (this) {
                if (head != tail) {
                    int pointer = head;
                    while (true) {
                        if (data[pointer].getSequenceNumber() == sequencenumber) {
                            currentSendWindowSize -= data[pointer].getSize();
                            int pointer2 = pointer + 1;
                            while (true) {
                                if (pointer2 == data.length) {
                                    pointer2 = 0;
                                }
                                if (pointer2 == tail) {
                                    break;
                                }
                                data[pointer2 - 1] = data[pointer2];
                                pointer2++;
                            }
                            tail--;
                            if (tail == -1) {
                                tail = data.length - 1;
                            }
                            break;
                        } else {
                            pointer++;
                            if (pointer == data.length) {
                                pointer = 0;
                            }
                            if (pointer == tail) {
                                break;
                            }
                        }

                    }
                }
            }
        }

        //Gets a UTPPacket from the given index into the buffer
        public UTPPacket get(int index) {
            synchronized (this) {
                if (this.size() > index) {
                    int pointer = head + index;
                    if (pointer >= data.length) {
                        pointer -= data.length;
                    }
                    return data[pointer];
                } else {
                    return null;
                }
            }
        }

        //Returns whether the buffer is full
        public boolean bufferFull() {
            synchronized (this) {
                if (tail + 1 == head) {
                    return true;
                }
                return tail == (data.length - 1) && head == 0;
            }
        }

        //Returns the size of the buffer
        public int size() {
            synchronized (this) {
                if (head > tail) {
                    return data.length - head + tail;
                } else {
                    return tail - head;
                }
            }
        }
    }

    //This is the UTP Input Stream class, it lets the user read bytes from the socket
    public class UTPInputStream extends InputStream {
        long timer = System.nanoTime();
        byte[] next = new byte[4];
        int len = 0;

        //Reads a byte from the socket, returns -1 if connection was closed
        public int read() throws IOException {
            while (true) {
                if (receivebuffer == null) {
                    return -1;
                } else {
                    if (receivebuffer.size() > 0) {
                        byte result = receivebuffer.read();
                        next[len] = result;
                        len++;
                        if (len == 4) {
                            int returnvalue = bytesToInt(next);
                            len = 0;
                            return returnvalue;
                        }
                    } else if (interupted) {
                        throw new IOException("Connection Interrupted");
                    } else if (closed) {
                        if (len > 0) {
                            byte[] temp = new byte[len];
                            System.arraycopy(next, 0, temp, 0, len);
                            len = 0;
                            return bytesToInt(temp);
                        }
                        return -1;
                    }
                }
            }
        }

        public int available() throws IOException {
            if (interupted) {
                throw new IOException("Connection Interrupted");
            } else if (receivebuffer == null || closed) {
                return -1;
            } else {
                return receivebuffer.size();
            }
        }

        public boolean markSupported() {
            return false;
        }
    }
}
