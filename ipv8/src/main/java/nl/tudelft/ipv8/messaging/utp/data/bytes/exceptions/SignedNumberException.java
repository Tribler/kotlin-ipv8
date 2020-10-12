package nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions;

public class SignedNumberException extends RuntimeException {

    private static final long serialVersionUID = 1339267381376290545L;

    public SignedNumberException(String message) {
        super(message);
    }

    public SignedNumberException() {
        super("No negative values allowed");
    }
}
