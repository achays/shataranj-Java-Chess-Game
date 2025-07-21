package engine;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class StockfishEngine implements AutoCloseable {
    private final Process engineProcess;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public StockfishEngine(String pathToStockfish) throws IOException {
        // Check if Stockfish executable exists
        File stockfishFile = new File(pathToStockfish);
        if (!stockfishFile.exists()) {
            throw new IOException("Stockfish executable not found at: " + pathToStockfish);
        }

        // Start Stockfish process
        ProcessBuilder processBuilder = new ProcessBuilder(pathToStockfish);
        processBuilder.redirectErrorStream(true);
        engineProcess = processBuilder.start();

        // Set up communication streams
        reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));

        // Initialize UCI protocol
        sendCommand("uci");
        waitForResponse("uciok");
        
        sendCommand("isready");
        waitForResponse("readyok");
        
        // Set some basic options for better play
        sendCommand("setoption name Hash value 64");
        sendCommand("setoption name Threads value 1");
        
        System.out.println("Stockfish engine initialized successfully");
    }

    /**
     * Get the best move for the current position
     * @param fen The position in FEN notation
     * @param thinkTimeMs Time to think in milliseconds
     * @return Best move in UCI format (e.g., "e2e4")
     * @throws IOException If communication with engine fails
     */
    public String getBestMove(String fen, int thinkTimeMs) throws IOException {
        // Set up the position
        sendCommand("position fen " + fen);
        
        // Start thinking
        sendCommand("go movetime " + thinkTimeMs);
        
        // Read output until we get the best move
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Stockfish: " + line); // Debug output
            
            if (line.startsWith("bestmove")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    return parts[1]; // Return the move (e.g., "e2e4")
                }
            }
        }
        
        return null; // No move found
    }

    /**
     * Get position evaluation
     * @param fen The position in FEN notation
     * @param depth Search depth
     * @return Evaluation in centipawns
     * @throws IOException If communication with engine fails
     */
    public int getEvaluation(String fen, int depth) throws IOException {
        sendCommand("position fen " + fen);
        sendCommand("go depth " + depth);
        
        String line;
        int evaluation = 0;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("info") && line.contains("cp")) {
                // Parse centipawn evaluation
                String[] parts = line.split(" ");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("cp".equals(parts[i])) {
                        try {
                            evaluation = Integer.parseInt(parts[i + 1]);
                        } catch (NumberFormatException e) {
                            // Ignore parsing errors
                        }
                        break;
                    }
                }
            } else if (line.startsWith("bestmove")) {
                break; // End of search
            }
        }
        
        return evaluation;
    }

    /**
     * Check if a move is legal
     * @param fen The current position
     * @param move The move to check
     * @return true if the move is legal
     * @throws IOException If communication with engine fails
     */
    public boolean isMoveLegal(String fen, String move) throws IOException {
        sendCommand("position fen " + fen);
        sendCommand("go perft 1");
        
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(move + ":")) {
                return true; // Move found in perft output
            }
            if (line.startsWith("Nodes")) {
                break; // End of perft output
            }
        }
        
        return false;
    }

    private void sendCommand(String command) throws IOException {
        writer.write(command + "\n");
        writer.flush();
        System.out.println("Sent to Stockfish: " + command); // Debug output
    }

    private void waitForResponse(String expectedResponse) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Stockfish: " + line); // Debug output
            if (line.trim().equals(expectedResponse)) {
                return;
            }
        }
        throw new IOException("Expected response '" + expectedResponse + "' not received");
    }

    @Override
    public void close() throws Exception {
        try {
            sendCommand("quit");
            
            // Wait for process to terminate gracefully
            if (!engineProcess.waitFor(2, TimeUnit.SECONDS)) {
                engineProcess.destroyForcibly();
            }
        } finally {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
        }
    }

    /**
     * Check if the engine is still running
     * @return true if the engine process is alive
     */
    public boolean isRunning() {
        return engineProcess.isAlive();
    }
}
