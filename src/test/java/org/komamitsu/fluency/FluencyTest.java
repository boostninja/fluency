package org.komamitsu.fluency;

import org.junit.Test;
import org.komamitsu.fluency.buffer.Buffer;
import org.komamitsu.fluency.buffer.MessageBuffer;
import org.komamitsu.fluency.buffer.PackedForwardBuffer;
import org.komamitsu.fluency.flusher.AsyncFlusher;
import org.komamitsu.fluency.flusher.Flusher;
import org.komamitsu.fluency.flusher.SyncFlusher;
import org.komamitsu.fluency.sender.MultiSender;
import org.komamitsu.fluency.sender.Sender;
import org.komamitsu.fluency.sender.TCPSender;
import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FluencyTest
{
    private static final Logger LOG = LoggerFactory.getLogger(FluencyTest.class);
    private static final int SMALL_BUF_SIZE = 4 * 1024 * 1024;

    @Test
    public void testDefaultFluency()
            throws IOException
    {
        Fluency fluency = null;
        Fluency.defaultFluency().close();
        Fluency.defaultFluency(12345).close();
        Fluency.defaultFluency("333.333.333.333", 12345).close();
        Fluency.defaultFluency(Arrays.asList(new InetSocketAddress(43210))).close();
        Fluency.Config config = new Fluency.Config();
        config.setFlushIntervalMillis(200).setMaxBufferSize(Long.MAX_VALUE).setSenderMaxRetryCount(99);
        Fluency.defaultFluency(config).close();
        Fluency.defaultFluency(12345, config).close();
        Fluency.defaultFluency("333.333.333.333", 12345, config).close();
        Fluency.defaultFluency(Arrays.asList(new InetSocketAddress(43210)), config).close();
    }

    interface FluencyFactory
    {
        Fluency generate(List<Integer> localPort)
                throws IOException;
    }

    private Sender getSingleTCPSender(int port)
    {
        return new TCPSender.Config().setPort(port).createInstance();
    }

    private Sender getDoubleTCPSender(int firstPort, int secondPort)
    {
        return new MultiSender.Config(Arrays.asList(new TCPSender.Config().setPort(firstPort), new TCPSender.Config().setPort(secondPort))).
                createInstance();
    }

    @Test
    public void testFluencyUsingPackedForwardBufferAndAsyncFlusher()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config();
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndAsyncFlusher()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config();
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardBufferAndSyncFlusher()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config();
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusher()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config();
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndAsyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndSyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndAsyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardBufferAndAsyncFlusherWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndAsyncFlusherWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardBufferAndSyncFlusherWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusherWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusherWithAckResponseWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true).setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndAsyncFlusherWithAckResponseWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true).setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingMessageAndAsyncFlusherWithAckResponseWithMultiSender()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                int secondaryFluentdPort = localPorts.get(1);
                Sender sender = getDoubleTCPSender(fluentdPort, secondaryFluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        }, true, false);
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusherWithAckResponseWithMultiSender()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                int secondaryFluentdPort = localPorts.get(1);
                Sender sender = getDoubleTCPSender(fluentdPort, secondaryFluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        }, true, false);
    }

    @Test
    public void testFluencyUsingPackedForwardAndSyncFlusherWithAckResponseWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true).setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndAsyncFlusherWithAckResponseWithSmallBuffer()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true).setMaxBufferSize(SMALL_BUF_SIZE);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndAsyncFlusherWithAckResponseWithMultiSender()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                int secondaryFluentdPort = localPorts.get(1);
                Sender sender = getDoubleTCPSender(fluentdPort, secondaryFluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        }, true, false);
    }

    @Test
    public void testFluencyUsingPackedForwardAndSyncFlusherWithAckResponseWithMultiSender()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                int secondaryFluentdPort = localPorts.get(1);
                Sender sender = getDoubleTCPSender(fluentdPort, secondaryFluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        }, true, false);
    }

    @Test
    public void testFluencyUsingMessageAndSyncFlusherWithAckResponseWithFileBackup()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true).setFileBackupDir(System.getProperty("java.io.tmpdir"));
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        }, false, true);
    }

    /*
    @Test
    public void testFluencyUsingMessageAndAsyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new MessageBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndSyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new SyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }

    @Test
    public void testFluencyUsingPackedForwardAndAsyncFlusherWithAckResponse()
            throws Exception
    {
        testFluencyBase(new FluencyFactory() {
            @Override
            public Fluency generate(List<Integer> localPorts)
                    throws IOException
            {
                int fluentdPort = localPorts.get(0);
                Sender sender = getSingleTCPSender(fluentdPort);
                Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setAckResponseMode(true);
                Flusher.Config flusherConfig = new AsyncFlusher.Config();
                return new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
            }
        });
    }
    */

    private void testFluencyBase(FluencyFactory fluencyFactory)
            throws Exception
    {
        testFluencyBase(fluencyFactory, false, false);
    }

    private void testFluencyBase(final FluencyFactory fluencyFactory, final boolean testFailover, final boolean testFileBackup)
            throws Exception
    {
        final ArrayList<Integer> localPorts = new ArrayList<Integer>();

        final MockFluentdServer fluentd = new MockFluentdServer();
        fluentd.start();
        TimeUnit.MILLISECONDS.sleep(200);
        localPorts.add(fluentd.getLocalPort());

        final MockFluentdServer secondaryFluentd = new MockFluentdServer(fluentd);
        secondaryFluentd.start();
        TimeUnit.MILLISECONDS.sleep(200);
        localPorts.add(secondaryFluentd.getLocalPort());

        final AtomicReference<Fluency> fluency = new AtomicReference<Fluency>(fluencyFactory.generate(localPorts));

        final int maxNameLen = 200;
        final HashMap<Integer, String> nameLenTable = new HashMap<Integer, String>(maxNameLen);
        for (int i = 1; i <= maxNameLen; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int j = 0; j < i; j++) {
                stringBuilder.append('x');
            }
            nameLenTable.put(i, stringBuilder.toString());
        }

        final AtomicLong ageEventsSum = new AtomicLong();
        final AtomicLong nameEventsLength = new AtomicLong();
        final AtomicLong tag0EventsCounter = new AtomicLong();
        final AtomicLong tag1EventsCounter = new AtomicLong();
        final AtomicLong tag2EventsCounter = new AtomicLong();
        final AtomicLong tag3EventsCounter = new AtomicLong();

        try {
            final Random random = new Random();
            int concurrency = 10;
            final int reqNum = 6000;
            long start = System.currentTimeMillis();
            final CountDownLatch latch = new CountDownLatch(concurrency);
            final AtomicBoolean shouldFailOver = new AtomicBoolean(true);

            final AtomicBoolean shouldStopFluentd = new AtomicBoolean(true);
            final AtomicBoolean shouldStopFluency = new AtomicBoolean(true);
            final CountDownLatch fluentdCloseWaitLatch = new CountDownLatch(concurrency);
            final CountDownLatch fluencyCloseWaitLatch = new CountDownLatch(concurrency);

            ExecutorService es = Executors.newCachedThreadPool();
            for (int i = 0; i < concurrency; i++) {
                es.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        for (int i = 0; i < reqNum; i++) {
                            if (testFailover) {
                                if (i == reqNum / 4) {
                                    if (shouldFailOver.getAndSet(false)) {
                                        LOG.info("Failing over...");
                                        try {
                                            secondaryFluentd.stop();
                                        }
                                        catch (IOException e) {
                                            LOG.warn("Failed to stop secondary fluentd", e);
                                        }
                                    }
                                }
                            }
                            else if (testFileBackup) {
                                if (i == reqNum / 4) {
                                    if (shouldStopFluentd.getAndSet(false)) {
                                        LOG.info("Stopping Fluentd...");
                                        try {
                                            fluentd.stop();
                                            secondaryFluentd.stop();
                                        }
                                        catch (IOException e) {
                                            LOG.warn("Failed to stop Fluentd", e);
                                        }
                                    }

                                    fluentdCloseWaitLatch.countDown();
                                    try {
                                        assertTrue(fluentdCloseWaitLatch.await(20, TimeUnit.SECONDS));
                                    }
                                    catch (InterruptedException e) {
                                        LOG.warn("Interrupted", e);
                                    }

                                    if (shouldStopFluency.getAndSet(false)) {
                                        LOG.info("Stopping Fluency...");
                                        try {
                                            fluency.get().close();
                                            TimeUnit.SECONDS.sleep(2);
                                        }
                                        catch (Exception e) {
                                            LOG.warn("Failed to stop Fluency", e);
                                        }

                                        LOG.info("Restarting Fluentd...");
                                        try {
                                            fluentd.start();
                                            secondaryFluentd.start();
                                            TimeUnit.MILLISECONDS.sleep(200);
                                            LOG.info("Restarting Fluency...");
                                            fluency.set(fluencyFactory.generate(Arrays.asList(fluentd.getLocalPort(), secondaryFluentd.getLocalPort())));
                                            TimeUnit.SECONDS.sleep(2);
                                        }
                                        catch (Exception e) {
                                            LOG.warn("Failed to restart Fluentd", e);
                                        }
                                    }

                                    fluencyCloseWaitLatch.countDown();
                                    try {
                                        assertTrue(fluencyCloseWaitLatch.await(20, TimeUnit.SECONDS));
                                    }
                                    catch (InterruptedException e) {
                                        LOG.warn("Interrupted", e);
                                    }
                                }
                            }
                            int tagNum = i % 4;
                            final String tag = String.format("foodb%d.bartbl%d", tagNum, tagNum);
                            switch (tagNum) {
                                case 0:
                                    tag0EventsCounter.incrementAndGet();
                                    break;
                                case 1:
                                    tag1EventsCounter.incrementAndGet();
                                    break;
                                case 2:
                                    tag2EventsCounter.incrementAndGet();
                                    break;
                                case 3:
                                    tag3EventsCounter.incrementAndGet();
                                    break;
                                default:
                                    throw new RuntimeException("Never reach here");
                            }

                            int rand = random.nextInt(maxNameLen);
                            final Map<String, Object> hashMap = new HashMap<String, Object>();
                            String name = nameLenTable.get(rand + 1);
                            nameEventsLength.addAndGet(name.length());
                            hashMap.put("name", name);
                            rand = random.nextInt(100);
                            int age = rand;
                            ageEventsSum.addAndGet(age);
                            hashMap.put("age", age);
                            hashMap.put("comment", "hello, world");
                            hashMap.put("rate", 1.23);
                            try {
                                BufferFullException exception = null;
                                for (int retry = 0; retry < 10; retry++) {
                                    try {
                                        fluency.get().emit(tag, hashMap);
                                        exception = null;
                                        break;
                                    }
                                    catch (BufferFullException e) {
                                        exception = e;
                                        try {
                                            TimeUnit.SECONDS.sleep(1);
                                        }
                                        catch (InterruptedException e1) {
                                        }
                                    }
                                }
                                if (exception != null) {
                                    throw exception;
                                }
                            }
                            catch (IOException e) {
                                LOG.warn("IOException occurred", e);
                                // throw new RuntimeException("Failed", e);
                                // TODO: We can ignore it?
                            }
                        }
                        latch.countDown();
                    }
                });
            }

            for (int i = 0; i < 60; i++) {
                if (latch.await(1, TimeUnit.SECONDS)) {
                    break;
                }
            }
            assertEquals(0, latch.getCount());

            fluency.get().flush();
            for (int i = 0; i < 20; i++) {
                if (fluentd.ageEventsCounter.get() == (long)concurrency * reqNum) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
            fluentd.stop();
            secondaryFluentd.stop();
            TimeUnit.MILLISECONDS.sleep(1000);

            // Ignore these counters when testing failover
            if (!testFailover) {
                if (testFileBackup) {
                    assertEquals(2, fluentd.connectCounter.get());
                    assertEquals(2, fluentd.closeCounter.get());
                }
                else {
                    assertEquals(1, fluentd.connectCounter.get());
                    assertEquals(1, fluentd.closeCounter.get());
                }
            }
            assertEquals((long)concurrency * reqNum, fluentd.ageEventsCounter.get());
            assertEquals(ageEventsSum.get(), fluentd.ageEventsSum.get());
            assertEquals((long)concurrency * reqNum, fluentd.nameEventsCounter.get());
            assertEquals(nameEventsLength.get(), fluentd.nameEventsLength.get());
            assertEquals(tag0EventsCounter.get(), fluentd.tag0EventsCounter.get());
            assertEquals(tag1EventsCounter.get(), fluentd.tag1EventsCounter.get());
            assertEquals(tag2EventsCounter.get(), fluentd.tag2EventsCounter.get());
            assertEquals(tag3EventsCounter.get(), fluentd.tag3EventsCounter.get());

            System.out.println(System.currentTimeMillis() - start);
        } finally {
            fluency.get().close();
            fluentd.stop();
            secondaryFluentd.stop();
        }
    }

    private static class MockFluentdServer
            extends AbstractFluentdServer
    {
        private final AtomicLong connectCounter;
        private final AtomicLong ageEventsCounter;
        private final AtomicLong ageEventsSum;
        private final AtomicLong nameEventsCounter;
        private final AtomicLong nameEventsLength;
        private final AtomicLong tag0EventsCounter;
        private final AtomicLong tag1EventsCounter;
        private final AtomicLong tag2EventsCounter;
        private final AtomicLong tag3EventsCounter;
        private final AtomicLong closeCounter;
        private final long startTimestamp;

        public MockFluentdServer()
                throws IOException
        {
            connectCounter = new AtomicLong();
            ageEventsCounter = new AtomicLong();
            ageEventsSum = new AtomicLong();
            nameEventsCounter = new AtomicLong();
            nameEventsLength = new AtomicLong();
            tag0EventsCounter = new AtomicLong();
            tag1EventsCounter = new AtomicLong();
            tag2EventsCounter = new AtomicLong();
            tag3EventsCounter = new AtomicLong();
            closeCounter = new AtomicLong();
            startTimestamp = System.currentTimeMillis() / 1000;
        }

        public MockFluentdServer(MockFluentdServer base)
                throws IOException
        {
            connectCounter = base.connectCounter;
            ageEventsCounter = base.ageEventsCounter;
            ageEventsSum = base.ageEventsSum;
            nameEventsCounter = base.nameEventsCounter;
            nameEventsLength = base.nameEventsLength;
            tag0EventsCounter = base.tag0EventsCounter;
            tag1EventsCounter = base.tag1EventsCounter;
            tag2EventsCounter = base.tag2EventsCounter;
            tag3EventsCounter = base.tag3EventsCounter;
            closeCounter = base.closeCounter;
            startTimestamp = System.currentTimeMillis() / 1000;
        }

        @Override
        protected EventHandler getFluentdEventHandler()
        {
            return new EventHandler()
            {
                @Override
                public void onConnect(SocketChannel accpetSocketChannel)
                {
                    connectCounter.incrementAndGet();
                }

                @Override
                public void onReceive(String tag, long timestampMillis, MapValue data)
                {
                    if (tag.equals("foodb0.bartbl0")) {
                        tag0EventsCounter.incrementAndGet();
                    }
                    else if (tag.equals("foodb1.bartbl1")) {
                        tag1EventsCounter.incrementAndGet();
                    }
                    else if (tag.equals("foodb2.bartbl2")) {
                        tag2EventsCounter.incrementAndGet();
                    }
                    else if (tag.equals("foodb3.bartbl3")) {
                        tag3EventsCounter.incrementAndGet();
                    }
                    else {
                        throw new IllegalArgumentException("Unexpected tag: tag=" + tag);
                    }

                    assertTrue(startTimestamp <= timestampMillis && timestampMillis < startTimestamp + 60 * 1000);

                    assertEquals(4, data.size());
                    for (Map.Entry<Value, Value> kv : data.entrySet()) {
                        String key = kv.getKey().asStringValue().toString();
                        Value val = kv.getValue();
                        if (key.equals("comment")) {
                            assertEquals("hello, world", val.toString());
                        }
                        else if (key.equals("rate")) {
                            // Treating the value as String to avoid a failure of calling asFloatValue()...
                            assertEquals("1.23", val.toString());
                        }
                        else if (key.equals("name")) {
                            nameEventsCounter.incrementAndGet();
                            nameEventsLength.addAndGet(val.asRawValue().asString().length());
                        }
                        else if (key.equals("age")) {
                            ageEventsCounter.incrementAndGet();
                            ageEventsSum.addAndGet(val.asIntegerValue().asInt());
                        }
                    }
                }

                @Override
                public void onClose(SocketChannel accpetSocketChannel)
                {
                    closeCounter.incrementAndGet();
                }
            };
        }
    }

    private static class EmitTask implements Runnable
    {
        private final Fluency fluency;
        private final String tag;
        private Map<String, Object> data;
        private final int count;
        private final CountDownLatch latch;

        private EmitTask(Fluency fluency, String tag, Map<String, Object> data, int count, CountDownLatch latch)
        {
            this.fluency = fluency;
            this.tag = tag;
            this.data = data;
            this.count = count;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            for (int i = 0; i < count; i++) {
                try {
                    fluency.emit(tag, data);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Failed", e);
                }
            }
            latch.countDown();
        }
    }

    private static class StuckSender extends StubSender
    {
        private final CountDownLatch latch;

        public StuckSender(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void send(ByteBuffer data)
                throws IOException
        {
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                FluencyTest.LOG.warn("Interrupted in send()", e);
            }
        }
    }

    @Test
    public void testBufferFullException()
            throws IOException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        Sender stuckSender = new StuckSender(latch);

        try {
            Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setInitialBufferSize(64).setMaxBufferSize(256);
            Fluency fluency = new Fluency.Builder(stuckSender).setBufferConfig(bufferConfig).build();
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("name", "xxxx");
            for (int i = 0; i < 7; i++) {
                fluency.emit("tag", event);
            }
            try {
                fluency.emit("tag", event);
                assertTrue(false);
            }
            catch (BufferFullException e) {
                assertTrue(true);
            }
        }
        finally {
            latch.countDown();
        }
    }

    // @Test
    public void testWithRealFluentd()
            throws IOException, InterruptedException
    {
        int concurrency = 4;
        int reqNum = 1000000;
        // Fluency fluency = Fluency.defaultFluency();
        TCPSender sender = new TCPSender.Config().createInstance();
        Buffer.Config bufferConfig = new PackedForwardBuffer.Config();
        Flusher.Config flusherConfig = new AsyncFlusher.Config().setFlushIntervalMillis(200);
        Fluency fluency = new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("name", "komamitsu");
        data.put("age", 42);
        data.put("comment", "hello, world");
        CountDownLatch latch = new CountDownLatch(concurrency);
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < concurrency; i++) {
            executorService.execute(new EmitTask(fluency, "foodb.bartbl", data, reqNum, latch));
        }
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        fluency.close();
    }

    // @Test
    public void testWithRealMultipleFluentd()
            throws IOException, InterruptedException
    {
        int concurrency = 4;
        int reqNum = 1000000;
        /*
        MultiSender sender = new MultiSender(Arrays.asList(new TCPSender(24224), new TCPSender(24225)));
        Buffer.Config bufferConfig = new PackedForwardBuffer.Config().setMaxBufferSize(128 * 1024 * 1024).setAckResponseMode(true);
        Flusher.Config flusherConfig = new AsyncFlusher.Config().setFlushIntervalMillis(200);
        Fluency fluency = new Fluency.Builder(sender).setBufferConfig(bufferConfig).setFlusherConfig(flusherConfig).build();
        */
        Fluency fluency = Fluency.defaultFluency(
                Arrays.asList(new InetSocketAddress(24224), new InetSocketAddress(24225)),
                new Fluency.Config().setAckResponseMode(true));

        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("name", "komamitsu");
        data.put("age", 42);
        data.put("comment", "hello, world");
        CountDownLatch latch = new CountDownLatch(concurrency);
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < concurrency; i++) {
            executorService.execute(new EmitTask(fluency, "foodb.bartbl", data, reqNum, latch));
        }
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        fluency.close();
    }
}
