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
package nl.tudelft.ipv8.messaging.utp.channels.impl.alg;

public enum PacketSizeModus {
    /**
     * dynamic packet sizes, proportional to current buffering delay
     */
    DYNAMIC_LINEAR,
    /**
     * constant pkt sizes, 576 bytes
     */
    CONSTANT_576,

    /**
     * constant packet sizes, 576 bytes.
     */
    CONSTANT_1472
}
