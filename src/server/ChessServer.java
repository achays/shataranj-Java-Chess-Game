package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChessServer {
    private static final int PORT = 8000;
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static ClientHandler whitePlayer = null;
    private static ClientHandler blackPlayer = null;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Chess server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler client = new ClientHandler(clientSocket);
            clients.add(client);
            new Thread(client).start();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private String color;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                assignColor();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("CHAT:")) {
                        // Relay chat to the other client
                        ClientHandler opponent = "white".equals(color) ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("CHAT:" + line.substring(5));
                        }
                    } else if (line.startsWith("MOVE:")) {
                        // Relay move to the other client and give them the turn
                        ClientHandler opponent = "white".equals(color) ? blackPlayer : whitePlayer;
                        if (opponent != null) {
                            opponent.out.println("MOVE:" + line.substring(5));
                            opponent.out.println("YOUR_TURN");
                        }
                    } else if (line.equals("RESET")) {
                        broadcastReset();
                    }
                }
            } catch (IOException ignored) {
            } finally {
                disconnect();
            }
        }

        private void assignColor() {
            synchronized (ChessServer.class) {
                System.out.println("New client connecting. White player: " + (whitePlayer != null) + ", Black player: " + (blackPlayer != null));
                
                if (whitePlayer == null) {
                    color = "white";
                    whitePlayer = this;
                    out.println("COLOR:white");
                    System.out.println("Assigned white color to new client");
                } else if (blackPlayer == null) {
                    color = "black";
                    blackPlayer = this;
                    out.println("COLOR:black");
                    whitePlayer.out.println("START");
                    blackPlayer.out.println("START");
                    whitePlayer.out.println("YOUR_TURN");
                    System.out.println("Assigned black color to new client. Game started!");
                } else {
                    out.println("GAME_FULL");
                    System.out.println("Game is full, rejecting new client");
                    disconnect();
                }
            }
        }

        private void broadcastMove(String move) {
            ClientHandler opponent = "white".equals(color) ? blackPlayer : whitePlayer;
            if (opponent != null) {
                opponent.out.println("MOVE:" + move.toUpperCase());
                opponent.out.println("YOUR_TURN");
            }
        }

        private void broadcastReset() {
            if (whitePlayer != null) {
                whitePlayer.out.println("RESET");
                whitePlayer.out.println("YOUR_TURN");
            }
            if (blackPlayer != null) {
                blackPlayer.out.println("RESET");
            }
        }

        private void disconnect() {
            try { socket.close(); } catch (IOException ignored) {}
            clients.remove(this);
            synchronized (ChessServer.class) {
                if (this == whitePlayer) {
                    whitePlayer = null;
                    System.out.println("White player disconnected");
                }
                if (this == blackPlayer) {
                    blackPlayer = null;
                    System.out.println("Black player disconnected");
                }
                
                // If both players are gone, reset the game state
                if (whitePlayer == null && blackPlayer == null) {
                    System.out.println("Both players disconnected. Game reset. Ready for new players.");
                }
            }
        }
    }
}
