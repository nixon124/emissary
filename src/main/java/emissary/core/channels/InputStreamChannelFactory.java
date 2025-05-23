package emissary.core.channels;

import jakarta.annotation.Nullable;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class InputStreamChannelFactory {
    private InputStreamChannelFactory() {}

    public static final int SIZE_IS_UNKNOWN = -1;

    /**
     * Creates a factory implementation based on an {@link InputStreamFactory}
     * 
     * @param size if known, else provide a negative value to allow the factory to work out the size upon first create
     * @param inputStreamFactory for the data
     * @return an InputStreamChannelFactory instance of the data
     */
    public static SeekableByteChannelFactory create(final long size, final InputStreamFactory inputStreamFactory) {
        return create(size, inputStreamFactory, false);
    }


    /**
     * Creates a factory implementation based on an {@link InputStreamFactory}
     *
     * @param size if known, else provide a negative value to allow the factory to work out the size upon first create
     * @param inputStreamFactory for the data
     * @param checkAvailable indicates whether to try to salvage truncated child entries
     * @return an InputStreamChannelFactory instance of the data
     */
    public static SeekableByteChannelFactory create(final long size, final InputStreamFactory inputStreamFactory, final boolean checkAvailable) {
        return new InputStreamChannelFactoryImpl(size, inputStreamFactory, checkAvailable);
    }

    private static class InputStreamChannelFactoryImpl implements SeekableByteChannelFactory {
        private final long size;
        @Nullable
        private final IOException ioException;
        private final InputStreamFactory inputStreamFactory;


        public InputStreamChannelFactoryImpl(final long size, final InputStreamFactory inputStreamFactory, final boolean checkAvailableWhenCreating) {
            Validate.notNull(inputStreamFactory, "Required: inputStream not null");

            // If the size is unknown then calculate it and save any IOException that occurs.
            if (size < 0 || checkAvailableWhenCreating) {
                long tempSize = SIZE_IS_UNKNOWN;
                IOException tempIoException = null;

                try (InputStream is = inputStreamFactory.create()) {
                    tempSize = SeekableByteChannelHelper.available(is);
                } catch (IOException e) {
                    tempIoException = e;
                }

                if (size < 0) {
                    this.size = tempSize;
                } else {
                    // use the smaller of the requested size and the available size
                    // this can salvage the available portion of a truncated entry
                    this.size = Math.min(size, tempSize);
                }
                ioException = tempIoException;
            } else {
                this.size = size;
                this.ioException = null;
            }

            this.inputStreamFactory = inputStreamFactory;
        }

        @Override
        public SeekableByteChannel create() {
            return new InputStreamChannel(size, inputStreamFactory, ioException);
        }
    }

    private static class InputStreamChannel extends AbstractSeekableByteChannel {
        /**
         * The InputStreamFactory used to get InputStream instances.
         */
        private final InputStreamFactory inputStreamFactory;
        private final long size;
        private final IOException ioException;

        /**
         * The current InputStream instance.
         */
        @Nullable
        private BoundedInputStream inputStream;

        /**
         * Create a new InputStreamChannel instance with a fixed size and data source
         * 
         * @param size of the InputStreamChannel
         * @param inputStreamFactory data source
         */
        public InputStreamChannel(final long size, final InputStreamFactory inputStreamFactory, final IOException ioException) {
            Validate.notNull(inputStreamFactory, "Required: inputStreamFactory not null!");

            this.size = size;
            this.inputStreamFactory = inputStreamFactory;
            this.ioException = ioException;
        }

        @Override
        protected final int readImpl(final ByteBuffer byteBuffer) throws IOException {
            if (inputStream != null && position() < inputStream.getCount()) {
                inputStream.close();
                inputStream = null;
            }

            if (inputStream == null) {
                inputStream = BoundedInputStream.builder()
                        .setInputStream(inputStreamFactory.create())
                        .get();
            }

            // Actually perform the read
            return SeekableByteChannelHelper.getFromInputStream(inputStream, byteBuffer, position() - inputStream.getCount());
        }


        @Override
        protected long sizeImpl() throws IOException {
            if (ioException != null) {
                throw ioException;
            }

            return size;
        }

        @Override
        protected final void closeImpl() throws IOException {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        }
    }
}
