package com.hatokuse;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Member {
    private final int port;
    private final String storageDir;
    private Server server;
    private AtomicInteger messageCount = new AtomicInteger(0);

    public Member(int port) {
        this.port = port;
        this.storageDir = "messages_" + port;
        new File(storageDir).mkdirs();
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new StorageServiceImpl())
                .build()
                .start();

        System.out.println("Üye " + port + " portu dinliyor");

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                    System.out.println("[Üye " + port + "] Toplam mesaj: " + messageCount.get());
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {
        @Override
        public void store(StorageProto.StoreRequest req, StreamObserver<StorageProto.StoreResponse> resp) {
            try {
                StorageProto.StoredMessage msg = req.getMessage();
                String filePath = storageDir + "/" + msg.getId() + ".msg";

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                    writer.write(msg.getText());
                }

                messageCount.incrementAndGet();

                resp.onNext(StorageProto.StoreResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("OK")
                        .build());
                resp.onCompleted();
            } catch (Exception e) {
                resp.onNext(StorageProto.StoreResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("ERROR")
                        .build());
                resp.onCompleted();
            }
        }

        @Override
        public void retrieve(StorageProto.RetrieveRequest req, StreamObserver<StorageProto.RetrieveResponse> resp) {
            try {
                String filePath = storageDir + "/" + req.getId() + ".msg";
                File file = new File(filePath);

                if (!file.exists()) {
                    resp.onNext(StorageProto.RetrieveResponse.newBuilder()
                            .setFound(false)
                            .build());
                    resp.onCompleted();
                    return;
                }

                String content = new String(Files.readAllBytes(Paths.get(filePath)));

                resp.onNext(StorageProto.RetrieveResponse.newBuilder()
                        .setFound(true)
                        .setMessage(StorageProto.StoredMessage.newBuilder()
                                .setId(req.getId())
                                .setText(content)
                                .build())
                        .build());
                resp.onCompleted();
            } catch (Exception e) {
                resp.onNext(StorageProto.RetrieveResponse.newBuilder()
                        .setFound(false)
                        .build());
                resp.onCompleted();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Kullanım: java Member <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Member member = new Member(port);
        member.start();
        member.blockUntilShutdown();
    }
}