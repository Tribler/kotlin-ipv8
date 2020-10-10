package nl.tudelft.ipv8.messaging.utp.channels.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MyQueue {
    private final Lock lock = new ReentrantLock();
    private Node first;

    public void offer(UtpTimestampedPacketDTO dto) {
        synchronized (lock) {
            if (first == null) {
                first = new Node(dto);
            } else {
                Node node = first;
                while (node.next != null) {
                    node = node.next;
                }
                node.next = new Node(dto);
            }
        }
    }

    public UtpTimestampedPacketDTO poll() {
        synchronized (lock) {
            while (true) {
                if (first != null) {
                    Node ret = first;
                    first = first.next;
                    return ret.elem;
                }
                try {
                    lock.wait(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public UtpTimestampedPacketDTO poll(long var1, TimeUnit var3) {
        long start = System.currentTimeMillis();
        synchronized (lock) {
            while (true) {
                if (first != null) {
                    Node ret = first;
                    first = first.next;
                    return ret.elem;
                }
                if (System.currentTimeMillis() - start > var1 / 1000) {
                    return null;
                }
                try {
                    lock.wait(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized UtpTimestampedPacketDTO peek() {
        synchronized (lock) {
            return first == null ? null : first.elem;
        }
    }

    private static class Node {
        private UtpTimestampedPacketDTO elem;
        private Node next;

        Node(UtpTimestampedPacketDTO elem) {
            this.elem = elem;
        }
    }
}
