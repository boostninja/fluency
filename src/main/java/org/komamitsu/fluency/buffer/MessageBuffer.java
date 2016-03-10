package org.komamitsu.fluency.buffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.komamitsu.fluency.BufferFullException;
import org.komamitsu.fluency.sender.Sender;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageBuffer
    extends Buffer<MessageBuffer.Config>
{
    public static final String FORMAT_TYPE = "message";
    private static final Logger LOG = LoggerFactory.getLogger(MessageBuffer.class);
    private final AtomicInteger allocatedSize = new AtomicInteger();
    private final LinkedBlockingQueue<ByteBuffer> messages = new LinkedBlockingQueue<ByteBuffer>();
    private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final Object bufferLock = new Object();

    private MessageBuffer(MessageBuffer.Config bufferConfig)
    {
        super(bufferConfig);
    }

    @Override
    public void append(String tag, long timestamp, Map<String, Object> data)
            throws IOException
    {
        byte[] packedBytes = null;
        ObjectMapper objectMapper = objectMapperHolder.get();
        ByteArrayOutputStream outputStream = outputStreamHolder.get();
        outputStream.reset();
        objectMapper.writeValue(outputStream, Arrays.asList(tag, timestamp, data));
        outputStream.close();
        packedBytes = outputStream.toByteArray();

        if (bufferConfig.isAckResponseMode()) {
            if (packedBytes[0] != (byte)0x93) {
                throw new IllegalStateException("packedBytes[0] should be 0x93, but " + packedBytes[0]);
            }
            packedBytes[0] = (byte)0x94;
        }

        // TODO: Refactoring
        synchronized (bufferLock) {
            if (allocatedSize.get() + packedBytes.length > bufferConfig.getMaxBufferSize()) {
                throw new BufferFullException("Buffer is full. bufferConfig=" + bufferConfig + ", allocatedSize=" + allocatedSize);
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(packedBytes);
            messages.add(byteBuffer);
            allocatedSize.getAndAdd(packedBytes.length);
        }
    }

    @Override
    public String bufferType()
    {
        return FORMAT_TYPE;
    }

    @Override
    public void flushInternal(Sender sender, boolean force)
            throws IOException
    {
        ByteBuffer message = null;
        while ((message = messages.poll()) != null) {
            // TODO: Refactoring
            synchronized (bufferLock) {
                allocatedSize.addAndGet(-message.capacity());
                if (bufferConfig.isAckResponseMode()) {
                    String uuid = UUID.randomUUID().toString();
                    sender.sendWithAck(Arrays.asList(message), uuid.getBytes(CHARSET));
                }
                else {
                    sender.send(message);
                }
            }
        }
    }

    @Override
    public void closeInternal(Sender sender)
            throws IOException
    {
        messages.clear();
    }

    @Override
    public long getAllocatedSize()
    {
        return allocatedSize.get();
    }

    public static class Config extends Buffer.Config<MessageBuffer, Config>
    {
        @Override
        public MessageBuffer createInstance()
        {
            return new MessageBuffer(this);
        }
    }
}
