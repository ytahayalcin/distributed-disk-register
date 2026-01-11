package com.hatokuse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Leader {
    private final int clientPort;
    private final String storageDir = "messages_leader";
    private final Map<Integer, String> localMessages = new ConcurrentHashMap<>();
    private final Map<Integer, List<Integer>> messageToMembers = new ConcurrentHashMap<>();
    private final List<Integer> memberPorts = new ArrayList<>();
    private int tolerance = 2;
    private int roundRobinIndex = 0;

    public Leader(int clientPort) {
        this.clientPort = clientPort;
        new File(storageDir).mkdirs();
        loadTolerance();
        System.out.println("Tolerance: " + tolerance);
    }

    private void loadTolerance() {
        // Farklı konumları dene
        String[] paths = {
                "tolerance.conf",
                "../tolerance.conf",
                "../../tolerance.conf",
                System.getProperty("user.dir") + "/tolerance.conf",
                System.getProperty("user.dir") + "/../tolerance.conf"
        };

        for (String path : paths) {
            File confFile = new File(path);
            if (confFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(confFile.toPath()));
                    String[] parts = content.trim().split("=");
                    if (parts.length == 2) {
                        tolerance = Integer.parseInt(parts[1].trim());
                        System.out.println("✓ tolerance.conf okundu: " + confFile.getAbsolutePath());
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("Dosya okuma hatası: " + e.getMessage());
                }
            }
        }

        System.out.println("⚠ tolerance.conf bulunamadı, varsayılan kullanılıyor: " + tolerance);
        System.out.println("Aranan dizin: " + System.getProperty("user.dir"));
    }

    public void addMember(int port) {
        memberPorts.add(port);
        System.out.println("Üye eklendi: " + port);
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(clientPort);
        System.out.println("Lider " + clientPort + " portunda istemci bekliyor");

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000);
                    printStats();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }

    private void handleClient(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String line = in.readLine();
            Command cmd = Command.parse(line);

            if (cmd instanceof SetCommand) {
                SetCommand set = (SetCommand) cmd;
                handleSet(set.id, set.message, out);
            } else if (cmd instanceof GetCommand) {
                GetCommand get = (GetCommand) cmd;
                handleGet(get.id, out);
            } else {
                out.println("ERROR");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSet(int id, String message, PrintWriter out) {
        try {
            String filePath = storageDir + "/" + id + ".msg";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(message);
            }
            localMessages.put(id, message);

            List<Integer> selectedMembers = selectMembers();
            List<Integer> successMembers = new ArrayList<>();

            for (int port : selectedMembers) {
                if (storeToMember(port, id, message)) {
                    successMembers.add(port);
                }
            }

            if (successMembers.size() >= tolerance) {
                messageToMembers.put(id, successMembers);
                out.println("OK");
            } else {
                out.println("ERROR");
            }
        } catch (Exception e) {
            out.println("ERROR");
        }
    }

    private void handleGet(int id, PrintWriter out) {
        if (localMessages.containsKey(id)) {
            out.println(localMessages.get(id));
            return;
        }

        List<Integer> members = messageToMembers.get(id);
        if (members != null) {
            for (int port : members) {
                String msg = retrieveFromMember(port, id);
                if (msg != null) {
                    out.println(msg);
                    return;
                }
            }
        }
        out.println("NOT_FOUND");
    }

    private List<Integer> selectMembers() {
        List<Integer> selected = new ArrayList<>();
        int size = memberPorts.size();

        for (int i = 0; i < tolerance && i < size; i++) {
            selected.add(memberPorts.get((roundRobinIndex + i) % size));
        }
        roundRobinIndex = (roundRobinIndex + 1) % size;
        return selected;
    }

    private boolean storeToMember(int port, int id, String text) {
        try {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);

            StorageProto.StoreResponse resp = stub.store(
                    StorageProto.StoreRequest.newBuilder()
                            .setMessage(StorageProto.StoredMessage.newBuilder()
                                    .setId(id)
                                    .setText(text)
                                    .build())
                            .build());

            channel.shutdown();
            return resp.getSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private String retrieveFromMember(int port, int id) {
        try {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
            StorageServiceGrpc.StorageServiceBlockingStub stub = StorageServiceGrpc.newBlockingStub(channel);

            StorageProto.RetrieveResponse resp = stub.retrieve(
                    StorageProto.RetrieveRequest.newBuilder()
                            .setId(id)
                            .build());

            channel.shutdown();
            return resp.getFound() ? resp.getMessage().getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void printStats() {
        System.out.println("\n=== LİDER İSTATİSTİKLER ===");
        System.out.println("Toplam mesaj: " + localMessages.size());
        for (int port : memberPorts) {
            int count = 0;
            for (List<Integer> members : messageToMembers.values()) {
                if (members.contains(port)) count++;
            }
            System.out.println("Üye " + port + ": " + count + " mesaj");
        }

        // Mesaj 4501'in yerini göster
        List<Integer> msg4501 = messageToMembers.get(4501);
        if (msg4501 != null) {
            System.out.println("\n>> Mesaj 4501 şu üyelerde: " + msg4501);
        }
    }

    public static void main(String[] args) throws Exception {
        Leader leader = new Leader(8080);
        leader.addMember(9001);
        leader.addMember(9002);
        leader.addMember(9003);
        leader.addMember(9004);
        leader.addMember(9005);
        leader.addMember(9006);
        leader.start();
    }
}