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

import tel.schich.javacan.ISOTPChannel;
import tel.schich.javacan.ISOTPGateway;
import tel.schich.javacan.JavaCAN;
import tel.schich.javacan.MessageHandler;
import tel.schich.javacan.QueueSettings;

import static tel.schich.javacan.AggregatingFrameHandler.aggregateFrames;
import static tel.schich.javacan.ISOTPAddress.DESTINATION_ECU_1;
import static tel.schich.javacan.ISOTPAddress.EFF_TYPE_FUNCTIONAL_ADDRESSING;
import static tel.schich.javacan.ISOTPAddress.DESTINATION_EFF_TEST_EQUIPMENT;
import static tel.schich.javacan.ISOTPAddress.SFF_ECU_REQUEST_BASE;
import static tel.schich.javacan.ISOTPAddress.SFF_ECU_RESPONSE_BASE;
import static tel.schich.javacan.ISOTPAddress.SFF_FUNCTIONAL_ADDRESS;
import static tel.schich.javacan.ISOTPAddress.effAddress;
import static tel.schich.javacan.test.CanTestHelper.CAN_INTERFACE;

class ISOTPGatewayTest {

    private static ThreadFactory threadFactory = r -> new Thread(r, "test-thread-" + r.toString());

    @Test
    void testWrite() throws Exception {
        JavaCAN.initialize();

            try (final ISOTPGateway isotp = new ISOTPGateway(threadFactory, QueueSettings.DEFAULT, 1000)) {
                isotp.bind(CAN_INTERFACE);

                try (final ISOTPChannel eff = isotp.createChannel(effAddress(0x18,
                        EFF_TYPE_FUNCTIONAL_ADDRESSING, DESTINATION_EFF_TEST_EQUIPMENT, DESTINATION_ECU_1), ISOTPGateway.NOOP_HANDLER)) {
                    eff.send(new byte[] { 0x11, 0x22, 0x33 });
                }

                try (final ISOTPChannel sff = isotp.createChannel(SFF_ECU_REQUEST_BASE + DESTINATION_ECU_1, ISOTPGateway.NOOP_HANDLER)) {
                    sff.send(new byte[] { 0x33, 0x22, 0x11 });
                }

                try (final ISOTPChannel sff = isotp.createChannel(SFF_FUNCTIONAL_ADDRESS, SFF_ECU_RESPONSE_BASE, ISOTPGateway.NOOP_HANDLER)) {
                    sff.send(
                            new byte[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2 });
                }

        }
    }

    @Test
    void testPolling() throws Exception {
        JavaCAN.initialize();

        try (final ISOTPGateway isotp = new ISOTPGateway(threadFactory, QueueSettings.DEFAULT, 10000)) {
            isotp.bind(CAN_INTERFACE);
            isotp.setReceiveOwnMessages(true);

            isotp.start();

            final ISOTPChannel a = isotp.createChannel(0x7E8, 0x7E0, aggregateFrames(new PingPing()));
            final ISOTPChannel b = isotp.createChannel(0x7E0, 0x7E8, aggregateFrames(new PingPing()));

            a.send(new byte[] { 1 }).get();

            Thread.sleep(100000);
        }
    }

    public static String hexDump(byte[] data) {
        StringBuilder s = new StringBuilder(data.length * 2);
        if (data.length > 0) {
            s.append(String.format("%02X", data[0]));
            for (int i = 1; i < data.length; ++i) {
                s.append('.').append(String.format("%02X", data[i]));
            }
        }
        return s.toString();
    }

    private static final class PingPing implements MessageHandler {
        @Override
        public void handle(ISOTPChannel ch, int sender, byte[] payload) {
            if (payload.length % 200 == 0) {
                System.out.println(String.format("(%04d) -> %08X#%s", payload.length, sender, hexDump(payload)));
                System.out.flush();
            }
            byte[] newMessage = new byte[payload.length + 1];
            System.arraycopy(payload, 0, newMessage, 0, payload.length);
            newMessage[payload.length] = (byte)(Math.random() * 255);
            ch.send(newMessage).whenComplete((nothing, t) -> {
                if (t != null) {
                    System.err.println("Failed to send message:");
                    t.printStackTrace(System.err);
                }
            });
        }
    }
}
