package nl.tudelft.ipv8.messaging.utp.data.bytes;


import nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions.ByteOverflowException;
import nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions.SignedNumberException;

/**
 *
 * Workaround to java's lack of unsigned types.
 * Methods provided here intend to return integers, bytes and shorts which can be interpreted as unsigned types.
 * E.G. Java's byte initialized with 0xFF will correspond to -1, with this workaround we can use more readable
 * decimal value of 255 to convert it to -1, which in binary form is 11111111 and interpreted as an unsigned
 * value, it would be 255.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */

public final class UnsignedTypesUtil {

	public static final long MAX_UBYTE = 255;
	public static final long MAX_USHORT = 65535;
	public static final long MAX_UINT = 4294967295L;


	public static byte longToUbyte(long longvalue) {
		if (longvalue > MAX_UBYTE) {
			throw new ByteOverflowException(getExceptionText(MAX_UBYTE, longvalue));
		} else if (longvalue < 0) {
			throw new SignedNumberException(getExceptionText(MAX_UBYTE, longvalue));
		}
		return (byte) (longvalue & 0xFF);
	}

	public static short longToUshort(long longvalue) {
		if (longvalue > MAX_USHORT) {
			throw new ByteOverflowException(getExceptionText(MAX_USHORT, longvalue));
		} else if (longvalue < 0) {
			throw new SignedNumberException(getExceptionText(MAX_USHORT, longvalue));
		}
		return (short) (longvalue & 0xFFFF);
	}

	public static int longToUint(long longvalue) {
		if (longvalue > MAX_UINT) {
			throw new ByteOverflowException(getExceptionText(MAX_UINT, longvalue));
		} else if (longvalue < 0) {
			throw new SignedNumberException(getExceptionText(MAX_UINT, longvalue));
		}
		return (int) (longvalue & 0xFFFFFFFF);
	}

	private static String getExceptionText(long max, long actual) {
		return "Cannot convert to unsigned type. " +
				"Possible values [0, " + max +  "] but got " + actual + ".";
	}

	public static short bytesToUshort(byte first, byte second) {
		short value = (short)( ((first & 0xFF) << 8) | (second & 0xFF) );
		return value;
	}

	public static int bytesToUint(byte first, byte second, byte third, byte fourth) {
		int firstI = (first & 0xFF) << 24;
		int secondI = (second & 0xFF) << 16;
		int thirdI = (third & 0xFF) << 8;
		int fourthI = (fourth & 0xFF);

		return firstI | secondI | thirdI | fourthI;


	}



 }
