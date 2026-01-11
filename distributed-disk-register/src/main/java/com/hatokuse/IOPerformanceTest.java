package com.hatokuse;

import java.io.IOException;

public class IOPerformanceTest {
    public static void main(String[] args) throws IOException {
        int messageCount = 1000;
        String testMessage = "Bu bir test mesajıdır. IO performansını ölçüyoruz.";

        System.out.println("=== IO PERFORMANS TESTİ ===\n");

        // Test 1: Buffered IO
        testIOMode(IOMode.BUFFERED, messageCount, testMessage);

        // Test 2: Unbuffered IO
        testIOMode(IOMode.UNBUFFERED, messageCount, testMessage);

        // Test 3: Zero-Copy IO
        testIOMode(IOMode.ZERO_COPY, messageCount, testMessage);
    }

    private static void testIOMode(IOMode mode, int count, String message) throws IOException {
        StorageManager storage = new StorageManager("test_" + mode.name().toLowerCase(), mode);

        // WRITE TEST
        long writeStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            storage.write(i, message);
        }
        long writeEnd = System.nanoTime();
        double writeTime = (writeEnd - writeStart) / 1_000_000.0; // ms

        // READ TEST
        long readStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            storage.read(i);
        }
        long readEnd = System.nanoTime();
        double readTime = (readEnd - readStart) / 1_000_000.0; // ms

        System.out.println("--- " + mode + " ---");
        System.out.println("Write: " + String.format("%.2f", writeTime) + " ms (" + count + " mesaj)");
        System.out.println("Read:  " + String.format("%.2f", readTime) + " ms (" + count + " mesaj)");
        System.out.println("Toplam: " + String.format("%.2f", writeTime + readTime) + " ms");
        System.out.println("Mesaj başına: " + String.format("%.3f", (writeTime + readTime) / count) + " ms\n");
    }
}