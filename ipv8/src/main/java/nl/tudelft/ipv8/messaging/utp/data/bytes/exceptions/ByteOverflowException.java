package nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions;

/**
 * Exception that indicates that an arithmetic overflow happened.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */

public class ByteOverflowException extends RuntimeException {

    private static final long serialVersionUID = -2545130698255742704L;

    public ByteOverflowException(String message) {
        super(message);
    }

    public ByteOverflowException() {
        super("Overflow happened.");
    }
}
