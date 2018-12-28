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
package tel.schich.javacan.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import tel.schich.javacan.IsotpCanChannel;

public class IsotpBroker implements Closeable {
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(4096);
    private final ThreadFactory threadFactory;
    private final AbstractSelector selector;
    private final Duration timeout;

    private final Set<IsotpCanChannel> channels;
    private final Map<IsotpCanChannel, MessageHandler> handlerMap;

    private PollingThread poller;

    public IsotpBroker(ThreadFactory threadFactory, SelectorProvider provider, Duration timeout) throws IOException {
        this.threadFactory = threadFactory;
        this.selector = provider.openSelector();
        this.timeout = timeout;
        this.channels = new HashSet<>();
        this.handlerMap = new IdentityHashMap<>();
    }

    public void addChannel(IsotpCanChannel ch, MessageHandler handler) throws IOException {
        if (ch.isRegistered()) {
            throw new IllegalArgumentException("Channel already registered!");
        }
        ch.configureBlocking(false);
        ch.register(this.selector, SelectionKey.OP_READ);
        this.channels.add(ch);
        this.handlerMap.put(ch, handler);
        this.start();
    }

    public void removeChannel(IsotpCanChannel ch) {
        if (!this.channels.contains(ch)) {
            throw new IllegalArgumentException("Channel not known!");
        }

        this.channels.remove(ch);
        this.handlerMap.remove(ch);
        ch.keyFor(selector).cancel();

        if (this.channels.isEmpty()) {
            try {
                this.shutdown();
            } catch (InterruptedException ignored) {}
        }
    }

    public void start() {
        if (poller != null) {
            // already running
            return;
        }


        this.poller = PollingThread.create("primary-poller", timeout.toMillis(), threadFactory, this::poll, this::handle);
        this.poller.start();
    }

    private boolean poll(long timeout) {
        if (channels.isEmpty()) {
            try {
                shutdown();
            } catch (InterruptedException ignored) {}
            return false;
        }
        try {
            int n = selector.select(timeout);
            if (n > 0) {
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    SelectableChannel ch = key.channel();
                    if (ch instanceof IsotpCanChannel) {
                        IsotpCanChannel isotp = (IsotpCanChannel) ch;
                        MessageHandler handler = handlerMap.get(ch);
                        if (handler != null) {
                            readBuffer.rewind();
                            final int bytesRead = ((IsotpCanChannel) ch).read(readBuffer);
                            readBuffer.rewind();
                            handler.handle(isotp, readBuffer, 0, bytesRead);
                        } else {
                            System.err.println("Handler not found for channel: " + ch);
                        }
                    } else {
                        System.err.println("Unsupported channel: " + ch);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return true;
    }

    private boolean handle(Thread thread, Throwable t, boolean terminal) {
        System.err.println("Polling thread failed: " + thread.getName());
        t.printStackTrace(System.err);
        System.err.println("Terminating other threads.");
        try {
            shutdown();
        } catch (InterruptedException e) {
            System.err.println("Got interrupted while stopping the threads");
        }
        return true;
    }

    public void shutdown() throws InterruptedException {
        if (this.poller == null) {
            // already stopped
            return;
        }
        try {
            this.poller.stop();
            this.selector.wakeup();
            this.poller.join();
        } finally {
            this.poller = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            shutdown();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
