package nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions;

public class ByteOverflowException extends RuntimeException {

    private static final long serialVersionUID = -2545130698255742704L;

    public ByteOverflowException(String message) {
        super(message);
    }

    public ByteOverflowException() {
        super("Overflow happened.");
    }
}
