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

/**
 * Header Extension, uTP might support other header extensions someday.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */
public abstract class UtpHeaderExtension {

    public static UtpHeaderExtension resolve(byte b) {
        if (b == longToUbyte(1)) {
            return new SelectiveAckHeaderExtension();
        } else {
            return null;
        }
    }

    public abstract byte getNextExtension();

    public abstract void setNextExtension(byte nextExtension);

    public abstract byte getLength();

    public abstract byte[] getBitMask();

    public abstract void setBitMask(byte[] bitMask);

    public abstract byte[] toByteArray();

}
