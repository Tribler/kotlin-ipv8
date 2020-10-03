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
package nl.tudelft.ipv8.messaging.utp.data;

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUbyte;

import java.util.Arrays;
/**
 * Selective ACK header extension.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class SelectiveAckHeaderExtension extends UtpHeaderExtension {

	private byte nextExtension;
	private byte[] bitMask;

	/* Bit mappings */
	public static byte[] BITMAP = { 1, 2, 4, 8, 16, 32, 64, (byte) 128};

	/**
	 * true if the ACK+number-th bit is set to 1 in the bitmask
	 * @param bitmask bitmask
	 * @param number number
	 * @return true if bit is set, otherwise false.
	 */
	public static boolean isBitMarked(byte bitmask, int number) {
		if (number < 2 || number > 9 ) {
			return false;
		} else {
			boolean returnvalue = (BITMAP[number - 2] & bitmask) == BITMAP[number - 2];
			return returnvalue;
		}
	}

	@Override
	public byte getNextExtension() {
		return nextExtension;
	}
	@Override
	public void setNextExtension(byte nextExtension) {
		this.nextExtension = nextExtension;
	}
	@Override
	public byte getLength() {
		return longToUbyte(bitMask.length);
	}

	@Override
	public byte[] getBitMask() {
		return bitMask;
	}
	@Override
	public void setBitMask(byte[] bitMask) {
		this.bitMask = bitMask;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof SelectiveAckHeaderExtension)) {
			return false;
		}
		SelectiveAckHeaderExtension s = (SelectiveAckHeaderExtension) obj;
		return Arrays.equals(toByteArray(), s.toByteArray());
	}

	@Override
	public byte[] toByteArray() {
		//TODO: not create a new byte array
		byte[] array = new byte[2 + bitMask.length];
		array[0] = nextExtension;
		array[1] = longToUbyte(bitMask.length);
		for (int i = 0; i < bitMask.length; i++) {
			array[i + 2] = bitMask[i];
		}
		return array;
	}
}
