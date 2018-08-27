/*
 * The MIT License
 * Copyright © 2018 Phillip Schichtel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tel.schich.javacan.test;

import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;

import tel.schich.javacan.ISOTPGateway;
import tel.schich.javacan.JavaCAN;
import tel.schich.javacan.RawCanSocket;

import static tel.schich.javacan.ISOTPAddress.ECU_1;
import static tel.schich.javacan.ISOTPAddress.EFF_FUNCTIONAL_ADDRESSING;
import static tel.schich.javacan.ISOTPAddress.EFF_TESTER;
import static tel.schich.javacan.ISOTPAddress.SFF_ECU_REQUEST_BASE;
import static tel.schich.javacan.ISOTPAddress.SFF_ECU_RESPONSE_BASE;
import static tel.schich.javacan.ISOTPAddress.SFF_FUNCTIONAL_ADDRESS;
import static tel.schich.javacan.ISOTPAddress.composeEffAddress;
import static tel.schich.javacan.test.CanTestHelper.CAN_INTERFACE;

class ISOTPGatewayTest {

    private static ThreadFactory threadFactory = r -> new Thread(r, "test-thread-" + r.toString());

    @Test
    void testWrite() throws Exception {
        JavaCAN.initialize();

        try (final RawCanSocket socket = RawCanSocket.create()) {
            socket.bind(CAN_INTERFACE);

            try (final ISOTPGateway isotp = new ISOTPGateway(socket, threadFactory, 100)) {

                try (final ISOTPGateway.ISOTPChannel eff = isotp.createChannel(composeEffAddress(0x18, EFF_FUNCTIONAL_ADDRESSING, EFF_TESTER, ECU_1))) {
                    eff.send(new byte[] { 0x11, 0x22, 0x33 });
                }

                try (final ISOTPGateway.ISOTPChannel sff = isotp.createChannel(SFF_ECU_REQUEST_BASE + ECU_1)) {
                    sff.send(new byte[] { 0x33, 0x22, 0x11 });
                }

                try (final ISOTPGateway.ISOTPChannel sff = isotp.createChannel(SFF_FUNCTIONAL_ADDRESS, SFF_ECU_RESPONSE_BASE)) {
                    sff.send(
                            new byte[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2 });
                }
            }
        }
    }

    @Test
    void testPolling() throws Exception {
        JavaCAN.initialize();

        try (final RawCanSocket socket = RawCanSocket.create()) {
            socket.bind(CAN_INTERFACE);

            try (final ISOTPGateway isotp = new ISOTPGateway(socket, threadFactory, 100)) {
                isotp.start(10000);


                try (final ISOTPGateway.ISOTPChannel channel = isotp.createChannel(SFF_FUNCTIONAL_ADDRESS, SFF_ECU_RESPONSE_BASE)) {
                    channel.send(new byte[] { 1, 2, 2 });
                }

                isotp.stop();
            }
        }
    }
}
