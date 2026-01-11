package com.hatokuse;

import java.io.*;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Kullanım: TestClient <komut>");
            System.out.println("Örnek: TestClient \"SET 1 merhaba\"");
            System.out.println("Örnek: TestClient \"GET 1\"");
            return;
        }

        String command = args[0];

        try (Socket socket = new Socket("localhost", 8080);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(command);
            String response = in.readLine();
            System.out.println("Yanıt: " + response);

        } catch (Exception e) {
            System.err.println("Hata: " + e.getMessage());
        }
    }
}