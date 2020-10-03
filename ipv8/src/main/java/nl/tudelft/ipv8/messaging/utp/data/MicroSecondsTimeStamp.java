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

import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.MAX_UINT;
import static nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil.longToUint;
import nl.tudelft.ipv8.messaging.utp.data.bytes.UnsignedTypesUtil;
import nl.tudelft.ipv8.messaging.utp.data.bytes.exceptions.ByteOverflowException;
/**
 * Implements micro second accuracy timestamps for uTP headers and to measure time elapsed.
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 *
 */
public class MicroSecondsTimeStamp {

	private static long initDateMillis = System.currentTimeMillis();
	private static long startNs = System.nanoTime();

	/**
	 * Returns a uTP time stamp for packet headers.
	 * @return timestamp
	 */
	public int utpTimeStamp() {
		int returnStamp;
		//TODO: if performance issues, try bitwise & operator since constant MAX_UINT equals 0xFFFFFF
		// (see http://en.wikipedia.org/wiki/Modulo_operation#Performance_issues )
		long stamp = timeStamp() % UnsignedTypesUtil.MAX_UINT;
		try {
			returnStamp  = longToUint(stamp);
		} catch (ByteOverflowException exp) {
			stamp = stamp % MAX_UINT;
			returnStamp = longToUint(stamp);
		}
		return returnStamp;
	}

	/**
	 * Calculates the Difference of the uTP timestamp between now and parameter.
	 * @param othertimestamp timestamp
	 * @return difference
	 */
	public int utpDifference(int othertimestamp) {
		return utpDifference(utpTimeStamp(), othertimestamp);
	}
	/**
	 * calculates the utp Difference of timestamps (this - other)
	 * @param thisTimeStamp
	 * @param othertimestamp
	 * @return difference.
	 */
	public int utpDifference(int thisTimeStamp, int othertimestamp) {
		int nowTime = thisTimeStamp;
		long nowTimeL = nowTime & 0xFFFFFFFF;
		long otherTimeL = othertimestamp & 0xFFFFFFFF;
		long differenceL = nowTimeL - otherTimeL;
		// TODO: POSSIBLE BUG NEGATIVE DIFFERENCE
		if (differenceL < 0) {
			differenceL += MAX_UINT;
		}
		return longToUint(differenceL);
	}


	/**
	 * @return timestamp with micro second resulution
	 */
	public long timeStamp() {

		long currentNs = System.nanoTime();
		long deltaMs = (currentNs - startNs)/1000;
		return (initDateMillis*1000 + deltaMs);
	}


	public long getBegin() {
		return (initDateMillis * 1000);
	}

	public long getStartNs() {
		return startNs;
	}

	public long getInitDateMs() {
		return initDateMillis;
	}

}
