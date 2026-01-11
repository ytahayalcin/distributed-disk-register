package com.hatokuse;

public enum IOMode {
    BUFFERED,      // BufferedWriter/Reader
    UNBUFFERED,    // FileOutputStream/InputStream
    ZERO_COPY      // FileChannel + MappedByteBuffer
}