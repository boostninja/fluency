package org.komamitsu.fluency.sender;

import org.komamitsu.fluency.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TCPSender
    implements Sender
{
    private static final Logger LOG = LoggerFactory.getLogger(TCPSender.class);
    private static final Charset CHARSET_FOR_ERRORLOG = Charset.forName("UTF-8");
    private final AtomicReference<SocketChannel> channel = new AtomicReference<SocketChannel>();
    private final String host;
    private final int port;
    private final byte[] optionBuffer = new byte[256];
    private final AckTokenSerDe ackTokenSerDe = new MessagePackAckTokenSerDe();

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public TCPSender(String host, int port)
            throws IOException
    {
        this.port = port;
        this.host = host;
    }

    public TCPSender(int port)
            throws IOException
    {
        this(Constants.DEFAULT_HOST, port);
    }

    public TCPSender(String host)
            throws IOException
    {
        this(host, Constants.DEFAULT_PORT);
    }

    public TCPSender()
            throws IOException
    {
        this(Constants.DEFAULT_HOST, Constants.DEFAULT_PORT);
    }

    private SocketChannel getOrOpenChannel()
            throws IOException
    {
        if (channel.get() == null) {
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
            socketChannel.socket().setTcpNoDelay(true);
            socketChannel.socket().setSoTimeout(5000);
            channel.set(socketChannel);
        }
        return channel.get();
    }

    @Override
    public synchronized void send(ByteBuffer data)
            throws IOException
    {
        try {
            LOG.trace("send(): sender.host={}, sender.port={}", getHost(), getPort());
            getOrOpenChannel().write(data);
        }
        catch (IOException e) {
            channel.set(null);
            throw e;
        }
    }

    @Override
    public synchronized void send(List<ByteBuffer> dataList)
            throws IOException
    {
        for (ByteBuffer data : dataList) {
            send(data);
        }
    }

    @Override
    public synchronized void sendWithAck(List<ByteBuffer> dataList, byte[] ackToken)
            throws IOException
    {
        send(dataList);
        send(ByteBuffer.wrap(ackTokenSerDe.pack(ackToken)));

        ByteBuffer byteBuffer = ByteBuffer.wrap(optionBuffer);
        // TODO: Set timeout
        getOrOpenChannel().read(byteBuffer);
        byte[] unpackedToken = ackTokenSerDe.unpack(optionBuffer);
        if (!Arrays.equals(ackToken, unpackedToken)) {
            throw new UnmatchedAckException("Ack tokens don't matched: expected=" + new String(ackToken, CHARSET_FOR_ERRORLOG) + ", got=" + new String(unpackedToken, CHARSET_FOR_ERRORLOG));
        }
    }

    @Override
    public synchronized void close()
            throws IOException
    {
        SocketChannel socketChannel;
        if ((socketChannel = channel.getAndSet(null)) != null) {
            socketChannel.close();
        }
    }

    public static class UnmatchedAckException
            extends IOException
    {
        public UnmatchedAckException(String message)
        {
            super(message);
        }
    }
}
