package org.komamitsu.fluency.buffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.komamitsu.fluency.BufferFullException;
import org.komamitsu.fluency.sender.Sender;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class PackedForwardBuffer
    extends Buffer<PackedForwardBuffer.Config>
{
    public static final String FORMAT_TYPE = "packed_forward";
    private static final Logger LOG = LoggerFactory.getLogger(PackedForwardBuffer.class);
    private final Map<String, RetentionBuffer> retentionBuffers = new HashMap<String, RetentionBuffer>();
    private final LinkedBlockingQueue<TaggableBuffer> flushableBuffers = new LinkedBlockingQueue<TaggableBuffer>();
    private final BufferPool bufferPool;

    private PackedForwardBuffer(PackedForwardBuffer.Config bufferConfig)
    {
        super(bufferConfig);
        bufferPool = new BufferPool(bufferConfig.getInitialBufferSize(), bufferConfig.getMaxBufferSize());
    }

    private RetentionBuffer prepareBuffer(String tag, int writeSize)
            throws BufferFullException
    {
        RetentionBuffer retentionBuffer = retentionBuffers.get(tag);
        if (retentionBuffer != null && retentionBuffer.getByteBuffer().remaining() > writeSize) {
            return retentionBuffer;
        }

        int newRetentionBufferSize;
        if (retentionBuffer == null) {
            newRetentionBufferSize = bufferConfig.getInitialBufferSize();
        }
        else{
            newRetentionBufferSize = (int) (retentionBuffer.getByteBuffer().capacity() * bufferConfig.getBufferExpandRatio());
        }

        while (newRetentionBufferSize < writeSize) {
            newRetentionBufferSize *= bufferConfig.getBufferExpandRatio();
        }

        ByteBuffer acquiredBuffer = bufferPool.acquireBuffer(newRetentionBufferSize);
        if (acquiredBuffer == null) {
            throw new BufferFullException("Buffer is full. bufferConfig=" + bufferConfig + ", bufferPool=" + bufferPool);
        }
        RetentionBuffer newBuffer = new RetentionBuffer(acquiredBuffer);
        if (retentionBuffer != null) {
            retentionBuffer.getByteBuffer().flip();
            newBuffer.getByteBuffer().put(retentionBuffer.getByteBuffer());
            bufferPool.returnBuffer(retentionBuffer.getByteBuffer());
        }
        LOG.trace("prepareBuffer(): allocate a new buffer. tag={}, buffer={}", tag, newBuffer);

        retentionBuffers.put(tag, newBuffer);
        return newBuffer;
    }

    private void loadDataToRetentionBuffers(String tag, ByteBuffer src)
            throws IOException
    {
        synchronized (retentionBuffers) {
            RetentionBuffer buffer = prepareBuffer(tag, src.remaining());
            buffer.getByteBuffer().put(src);
            buffer.getLastUpdatedTimeMillis().set(System.currentTimeMillis());
            moveRetentionBufferIfNeeded(tag, buffer);
        }
    }

    private void loadDataToFlushableBuffers(String tag, ByteBuffer src)
            throws IOException, InterruptedException
    {
        flushableBuffers.put(new TaggableBuffer(tag, src));
    }

    @Override
    protected void loadBuffer(List<String> params, ByteBuffer buffer)
    {
        if (params.size() != 3) {
            throw new IllegalArgumentException("The number of params should be 3: params=" + params);
        }
        String bufferType = params.get(0);
        String tag = params.get(1);
        // params.get(2) is timestamp

        if (bufferType.equals("0")) {   // 0: flushableBuffers
            try {
                loadDataToFlushableBuffers(tag, buffer);
            }
            catch (Exception e) {
                LOG.error("Failed to load data to flushableBuffers: params={}, buffer={}", params, buffer);
            }
        }
        else if (bufferType.equals("1")) {  // 1: retentionBuffers
            try {
                loadDataToRetentionBuffers(tag, buffer);
            }
            catch (Exception e) {
                LOG.error("Failed to load data to retentionBuffers: params={}, buffer={}", params, buffer);
            }
        }
        else {
            LOG.error("Unexpected bufferType: params={}, buffer={}", params, buffer);
        }
    }

    @Override
    public void append(String tag, long timestamp, Map<String, Object> data)
            throws IOException
    {
        ObjectMapper objectMapper = objectMapperHolder.get();
        ByteArrayOutputStream outputStream = outputStreamHolder.get();
        outputStream.reset();
        objectMapper.writeValue(outputStream, Arrays.asList(timestamp, data));
        outputStream.close();

        loadDataToRetentionBuffers(tag, ByteBuffer.wrap(outputStream.toByteArray()));
    }

    private void moveRetentionBufferIfNeeded(String tag, RetentionBuffer buffer)
            throws IOException
    {
        if (buffer.getByteBuffer().position() > bufferConfig.getBufferRetentionSize()) {
            moveRetentionBufferToFlushable(tag, buffer);
        }
    }

    private void moveRetentionBuffersToFlushable(boolean force)
            throws IOException
    {
        long expiredThreshold = System.currentTimeMillis() - bufferConfig.getBufferRetentionTimeMillis();

        synchronized (retentionBuffers) {
            for (Map.Entry<String, RetentionBuffer> entry : retentionBuffers.entrySet()) {
                // it can be null because moveRetentionBufferToFlushable() can set null
                if (entry.getValue() != null) {
                    if (force || entry.getValue().getLastUpdatedTimeMillis().get() < expiredThreshold) {
                        moveRetentionBufferToFlushable(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    private void moveRetentionBufferToFlushable(String tag, RetentionBuffer buffer)
            throws IOException
    {
        try {
            LOG.trace("moveRetentionBufferToFlushable(): tag={}, buffer={}", tag, buffer);
            flushableBuffers.put(new TaggableBuffer(tag, buffer.getByteBuffer()));
            retentionBuffers.put(tag, null);
        }
        catch (InterruptedException e) {
            throw new IOException("Failed to move retention buffer due to interruption", e);
        }
    }

    @Override
    public String bufferFormatType()
    {
        return FORMAT_TYPE;
    }

    @Override
    public void flushInternal(Sender sender, boolean force)
            throws IOException
    {
        moveRetentionBuffersToFlushable(force);

        TaggableBuffer flushableBuffer = null;
        while ((flushableBuffer = flushableBuffers.poll()) != null) {
            try {
                // TODO: Reuse MessagePacker
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                MessagePacker messagePacker = MessagePack.newDefaultPacker(header);
                LOG.trace("flushInternal(): bufferUsage={}, flushableBuffer={}", getBufferUsage(), flushableBuffer);
                String tag = flushableBuffer.getTag();
                ByteBuffer byteBuffer = flushableBuffer.getByteBuffer();
                if (bufferConfig.isAckResponseMode()) {
                    messagePacker.packArrayHeader(3);
                }
                else {
                    messagePacker.packArrayHeader(2);
                }
                messagePacker.packString(tag);
                messagePacker.packRawStringHeader(byteBuffer.position());
                messagePacker.flush();

                synchronized (sender) {
                    ByteBuffer headerBuffer = ByteBuffer.wrap(header.toByteArray());
                    byteBuffer.flip();
                    if (bufferConfig.isAckResponseMode()) {
                        String uuid = UUID.randomUUID().toString();
                        sender.sendWithAck(Arrays.asList(headerBuffer, byteBuffer), uuid.getBytes(CHARSET));
                    }
                    else {
                        sender.send(Arrays.asList(headerBuffer, byteBuffer));
                    }
                }
            }
            finally {
                bufferPool.returnBuffer(flushableBuffer.getByteBuffer());
            }
        }
    }

    @Override
    protected synchronized void closeInternal()
    {
        retentionBuffers.clear();
        bufferPool.releaseBuffers();
    }

    @Override
    public long getAllocatedSize()
    {
        return bufferPool.getAllocatedSize();
    }

    private static class RetentionBuffer
    {
        private final AtomicLong lastUpdatedTimeMillis = new AtomicLong();
        private final ByteBuffer byteBuffer;

        public RetentionBuffer(ByteBuffer byteBuffer)
        {
            this.byteBuffer = byteBuffer;
        }

        public AtomicLong getLastUpdatedTimeMillis()
        {
            return lastUpdatedTimeMillis;
        }

        public ByteBuffer getByteBuffer()
        {
            return byteBuffer;
        }

        @Override
        public String toString()
        {
            return "RetentionBuffer{" +
                    "lastUpdatedTimeMillis=" + lastUpdatedTimeMillis +
                    ", byteBuffer=" + byteBuffer +
                    '}';
        }
    }

    private static class TaggableBuffer
    {
        private final String tag;
        private final ByteBuffer byteBuffer;

        public TaggableBuffer(String tag, ByteBuffer byteBuffer)
        {
            this.tag = tag;
            this.byteBuffer = byteBuffer;
        }

        public String getTag()
        {
            return tag;
        }

        public ByteBuffer getByteBuffer()
        {
            return byteBuffer;
        }

        @Override
        public String toString()
        {
            return "TaggableBuffer{" +
                    "tag='" + tag + '\'' +
                    ", byteBuffer=" + byteBuffer +
                    '}';
        }
    }

    public static class Config extends Buffer.Config<PackedForwardBuffer, Config>
    {
        private int initialBufferSize = 1024 * 1024;
        private float bufferExpandRatio = 2.0f;
        private int bufferRetentionSize = 4 * 1024 * 1024;
        private int bufferRetentionTimeMillis = 400;

        public int getInitialBufferSize()
        {
            return initialBufferSize;
        }

        public Config setInitialBufferSize(int initialBufferSize)
        {
            this.initialBufferSize = initialBufferSize;
            return this;
        }

        public float getBufferExpandRatio()
        {
            return bufferExpandRatio;
        }

        public Config setBufferExpandRatio(float bufferExpandRatio)
        {
            this.bufferExpandRatio = bufferExpandRatio;
            return this;
        }

        public int getBufferRetentionSize()
        {
            return bufferRetentionSize;
        }

        public Config setBufferRetentionSize(int bufferRetentionSize)
        {
            this.bufferRetentionSize = bufferRetentionSize;
            return this;
        }

        public int getBufferRetentionTimeMillis()
        {
            return bufferRetentionTimeMillis;
        }

        public Config setBufferRetentionTimeMillis(int bufferRetentionTimeMillis)
        {
            this.bufferRetentionTimeMillis = bufferRetentionTimeMillis;
            return this;
        }

        @Override
        public String toString()
        {
            return "Config{" +
                    "initialBufferSize=" + initialBufferSize +
                    ", bufferExpandRatio=" + bufferExpandRatio +
                    ", bufferRetentionSize=" + bufferRetentionSize +
                    ", bufferRetentionTimeMillis=" + bufferRetentionTimeMillis +
                    "} " + super.toString();
        }

        @Override
        public PackedForwardBuffer createInstance()
        {
            return new PackedForwardBuffer(this);
        }
    }
}
