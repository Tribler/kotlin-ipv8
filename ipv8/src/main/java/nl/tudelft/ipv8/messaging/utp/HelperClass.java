package nl.tudelft.ipv8.messaging.utp;

public class HelperClass {
    //Converts an integer to a byte array
    public static byte[] intToBytes(int input) {
        byte[] result = new byte[4];
        result[0] = (byte) (input >>> 24);
        result[1] = (byte) (input >>> 16);
        result[2] = (byte) (input >>> 8);
        result[3] = (byte) input;
        return result;
    }

    //Converts a byte array to an integer
    public static int bytesToInt(byte[] input) {
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
}
