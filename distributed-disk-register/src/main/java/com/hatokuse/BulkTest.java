package com.hatokuse;

import java.io.*;
import java.net.Socket;

public class BulkTest {
    public static void main(String[] args) {
        int messageCount = 1000;

        if (args.length > 0) {
            messageCount = Integer.parseInt(args[0]);
        }

        System.out.println(messageCount + " mesaj gönderiliyor...");
        long start = System.currentTimeMillis();

        int successCount = 0;

        for (int i = 1; i <= messageCount; i++) {
            String command = "SET " + i + " mesaj_" + i;

            try (Socket socket = new Socket("localhost", 8080);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(command);
                String response = in.readLine();

                if ("OK".equals(response)) {
                    successCount++;
                }

                if (i % 100 == 0) {
                    System.out.println(i + " mesaj gönderildi...");
                }

            } catch (Exception e) {
                System.err.println("Hata mesaj " + i + ": " + e.getMessage());
                // Bağlantı hatası varsa biraz bekle ve tekrar dene
                try {
                    Thread.sleep(10);
                    // Tekrar dene
                    try (Socket socket = new Socket("localhost", 8080);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        out.println(command);
                        String response = in.readLine();
                        if ("OK".equals(response)) {
                            successCount++;
                        }
                    } catch (Exception ex) {
                        // Yine olmadı, geç
                    }
                } catch (InterruptedException ie) {}
            }
        }

        long end = System.currentTimeMillis();

        System.out.println("\n=== SONUÇ ===");
        System.out.println("Toplam: " + messageCount);
        System.out.println("Başarılı: " + successCount);
        System.out.println("Başarısız: " + (messageCount - successCount));
        System.out.println("Süre: " + (end - start) + " ms");
        System.out.println("\nLider konsoluna bak, mesaj dağılımını göreceksin!");
    }
}