package org.komamitsu.fluency.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileBackup
{
    private static final Logger LOG = LoggerFactory.getLogger(FileBackup.class);
    private static final String PARAM_DELIM_IN_FILENAME = "#";
    private static final String EXT_FILENAME = ".buf";
    private final File backupDir;
    private final Buffer buffer;
    private final Pattern pattern;

    public static class SavedBuffer
        implements Closeable
    {
        private final List<String> params;
        private final File savedFile;
        private FileChannel channel;

        public SavedBuffer(File savedFile, List<String> params)
        {
            this.savedFile = savedFile;
            this.params = params;
        }

        public void open(Callback callback)
        {
            try {
                channel = new RandomAccessFile(savedFile, "rw").getChannel();
                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.PRIVATE, 0, savedFile.length());
                LOG.info("Loading buffer: params={}, buffer={}", params, buffer);
                callback.process(params, buffer);
                success();
            }
            catch (Exception e) {
                LOG.error("Failed to process file. Skipping the file: file=" + savedFile, e);
            }
            finally {
                try {
                    close();
                }
                catch (IOException e) {
                    LOG.warn("Failed to close file: file=" + savedFile, e);
                }
            }
        }

        private void success()
        {
            try {
                close();
            }
            catch (IOException e) {
                LOG.warn("Failed to close file: file=" + savedFile, e);
            }
            finally {
                if (!savedFile.delete()) {
                    LOG.warn("Failed to delete file: file=" + savedFile);
                }
            }
        }

        @Override
        public void close()
                throws IOException
        {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        }

        public interface Callback
        {
            void process(List<String> params, ByteBuffer buffer);
        }
    }

    public FileBackup(File backupDir, Buffer buffer)
    {
        this.backupDir = backupDir;
        this.buffer = buffer;
        this.pattern = Pattern.compile(buffer.bufferFormatType() + PARAM_DELIM_IN_FILENAME + "([\\w" + PARAM_DELIM_IN_FILENAME + "]+)" + EXT_FILENAME);
        LOG.debug(this.toString());
    }

    @Override
    public String toString()
    {
        return "FileBackup{" +
                "backupDir=" + backupDir +
                ", buffer=" + buffer +
                ", pattern=" + pattern +
                '}';
    }

    public List<SavedBuffer> getSavedFiles()
    {
        File[] files = backupDir.listFiles();
        ArrayList<SavedBuffer> savedBuffers = new ArrayList<SavedBuffer>();
        for (File f : files) {
            Matcher matcher = pattern.matcher(f.getName());
            if (matcher.find()) {
                if (matcher.groupCount() != 1) {
                    LOG.warn("Invalid backup filename: file={}", f.getName());
                }
                else {
                    String concatParams = matcher.group(1);
                    String[] params = concatParams.split(PARAM_DELIM_IN_FILENAME);
                    LinkedList<String> paramList = new LinkedList<String>(Arrays.asList(params));
                    LOG.debug("Saved buffer params={}", paramList);
                    paramList.removeLast();
                    savedBuffers.add(new SavedBuffer(f, paramList));
                }
            }
        }
        return savedBuffers;
    }

    public void saveBuffer(List<String> params, ByteBuffer buffer)
    {
        LOG.info("Saving buffer: params={}, buffer={}", params, buffer);

        backupDir.mkdir();
        params.add(String.valueOf(System.currentTimeMillis()));

        boolean isFirst = true;
        StringBuilder sb = new StringBuilder();
        for (String param : params) {
            if (isFirst) {
                isFirst = false;
            }
            else {
                sb.append(PARAM_DELIM_IN_FILENAME);
            }
            sb.append(param);
        }
        String filename = this.buffer.bufferFormatType() + PARAM_DELIM_IN_FILENAME + sb.toString() + EXT_FILENAME;

        File file = new File(backupDir, filename);
        FileChannel channel = null;
        try {
            channel = new FileOutputStream(file).getChannel();
            channel.write(buffer);
        }
        catch (Exception e) {
            LOG.error("Failed to save buffer to file: params=" + params + ", path=" + file.getAbsolutePath() + ", buffer=" + buffer, e);
        }
        finally {
            if (channel != null) {
                try {
                    channel.close();
                }
                catch (IOException e) {
                    LOG.warn("Failed to close Channel: channel=" + channel);
                }
            }
        }
    }
}
