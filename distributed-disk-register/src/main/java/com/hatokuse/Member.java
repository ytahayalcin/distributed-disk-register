package com.hatokuse;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Member {
    private final int port;
    private final StorageManager storage;
    private Server server;
    private AtomicInteger messageCount = new AtomicInteger(0);

    public Member(int port) {
        this(port, IOMode.BUFFERED);
    }

    public Member(int port, IOMode ioMode) {
        this.port = port;
        this.storage = new StorageManager("messages_" + port, ioMode);
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new StorageServiceImpl())
                .build()
                .start();

        System.out.println("Üye " + port + " portu dinliyor (IO Mode: " + storage.getMode() + ")");

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

                storage.write(msg.getId(), msg.getText());
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
                String content = storage.read(req.getId());

                if (content == null) {
                    resp.onNext(StorageProto.RetrieveResponse.newBuilder()
                            .setFound(false)
                            .build());
                    resp.onCompleted();
                    return;
                }

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
            System.out.println("Kullanım: java Member <port> [IO_MODE]");
            System.out.println("IO_MODE: BUFFERED, UNBUFFERED, ZERO_COPY (varsayılan: BUFFERED)");
            return;
        }

        int port = Integer.parseInt(args[0]);
        IOMode ioMode = IOMode.BUFFERED;

        if (args.length > 1) {
            try {
                ioMode = IOMode.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Geçersiz IO mode, BUFFERED kullanılıyor");
            }
        }

        Member member = new Member(port, ioMode);
        member.start();
        member.blockUntilShutdown();
    }
}