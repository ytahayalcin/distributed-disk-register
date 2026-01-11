package com.hatokuse;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;

public class StorageManager {
    private final String storageDir;
    private final IOMode mode;

    public StorageManager(String storageDir, IOMode mode) {
        this.storageDir = storageDir;
        this.mode = mode;
        new File(storageDir).mkdirs();
    }

    public void write(int id, String text) throws IOException {
        String filePath = storageDir + "/" + id + ".msg";

        switch (mode) {
            case BUFFERED:
                writeBuffered(filePath, text);
                break;
            case UNBUFFERED:
                writeUnbuffered(filePath, text);
                break;
            case ZERO_COPY:
                writeZeroCopy(filePath, text);
                break;
        }
    }

    public String read(int id) throws IOException {
        String filePath = storageDir + "/" + id + ".msg";
        File file = new File(filePath);

        if (!file.exists()) {
            return null;
        }

        switch (mode) {
            case BUFFERED:
                return readBuffered(filePath);
            case UNBUFFERED:
                return readUnbuffered(filePath);
            case ZERO_COPY:
                return readZeroCopy(filePath);
            default:
                return null;
        }
    }

    // BUFFERED IO - Standart yaklaşım
    private void writeBuffered(String path, String text) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(text);
        }
    }

    private String readBuffered(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // UNBUFFERED IO - Doğrudan sistem çağrıları
    private void writeUnbuffered(String path, String text) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(text.getBytes());
            fos.getFD().sync(); // Disk'e zorla yaz
        }
    }

    private String readUnbuffered(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] data = new byte[fis.available()];
            fis.read(data);
            return new String(data);
        }
    }

    // ZERO-COPY - Memory-mapped IO
    private void writeZeroCopy(String path, String text) throws IOException {
        byte[] data = text.getBytes();

        try (RandomAccessFile raf = new RandomAccessFile(path, "rw");
             FileChannel channel = raf.getChannel()) {

            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, data.length);
            buffer.put(data);
            buffer.force(); // Disk'e yaz
        }
    }

    private String readZeroCopy(String path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r");
             FileChannel channel = raf.getChannel()) {

            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, channel.size());

            byte[] data = new byte[(int) channel.size()];
            buffer.get(data);
            return new String(data);
        }
    }

    public IOMode getMode() {
        return mode;
    }
}