package com.hatokuse;

abstract class Command {
    static Command parse(String line) {
        if (line == null || line.trim().isEmpty()) return null;

        String[] parts = line.trim().split(" ", 3);
        String cmd = parts[0].toUpperCase();

        if ("SET".equals(cmd) && parts.length == 3) {
            return new SetCommand(Integer.parseInt(parts[1]), parts[2]);
        } else if ("GET".equals(cmd) && parts.length == 2) {
            return new GetCommand(Integer.parseInt(parts[1]));
        }
        return null;
    }
}

class SetCommand extends Command {
    int id;
    String message;

    SetCommand(int id, String message) {
        this.id = id;
        this.message = message;
    }
}

class GetCommand extends Command {
    int id;

    GetCommand(int id) {
        this.id = id;
    }
}