package client;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.*;
import engine.StockfishEngine;
import java.awt.*;
import java.io.*;
import java.io.File;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;




// Custom button class for chess squares that can draw dots and borders
class ChessSquareButton extends JButton {
    private boolean showDot = false;
    private boolean showBorder = false;
    private Color dotColor = new Color(100, 100, 100, 150); // Semi-transparent gray
    private Color borderColor = new Color(255, 255, 0); // Yellow border
    
    public ChessSquareButton() {
        setOpaque(true);
        setContentAreaFilled(true);
        setBorderPainted(false);
        setFocusPainted(false);
    }
    
    public void setShowDot(boolean show) {
        this.showDot = show;
        repaint();
    }
    
    public void setShowBorder(boolean show) {
        this.showBorder = show;
        repaint();
    }
    
    public void setDotColor(Color color) {
        this.dotColor = color;
        if (showDot) repaint();
    }
    
    public void setBorderColor(Color color) {
        this.borderColor = color;
        if (showBorder) repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw dot in center of square
        if (showDot) {
            int dotSize = 18; // Bigger dot
            int x = (getWidth() - dotSize) / 2;
            int y = (getHeight() - dotSize) / 2;
            
            // Draw black border first
            g2d.setColor(new Color(0, 0, 0, 200)); // Black border
            g2d.setStroke(new BasicStroke(1)); // Thin border
            g2d.drawOval(x, y, dotSize, dotSize);
            
            // Draw the brown dot
            g2d.setColor(dotColor);
            g2d.fillOval(x, y, dotSize, dotSize);
        }
        
        // Draw border around square
        if (showBorder) {
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(4)); // Thick border
            g2d.drawRect(2, 2, getWidth() - 4, getHeight() - 4);
        }
        
        g2d.dispose();
    }
}

public class ChessClient extends JFrame {
    private static final int BOARD_SIZE = 8;
    private static final int SQUARE_SIZE = 85; // Increased board square size to fill gaps
    private static final String IMAGE_PATH = "images/";

    private final ChessSquareButton[][] squares = new ChessSquareButton[BOARD_SIZE][BOARD_SIZE];
    private final Map<String, ImageIcon> icons = new HashMap<>();
    private final Board game = new Board();
    private JLabel statusLabel;
    private JLabel gameInfoLabel;
    private JComboBox<String> modeSelector;
    private JComboBox<String> difficultySelector;
    private JButton startButton;
    private JButton resetButton;
    private JButton showMovesButton;
    private JButton saveButton;
    private JButton loadButton;
    private JButton tutorButton;
    private JButton showBestMoveButton; // Add this field
    private boolean isPaused = false;

    private boolean isMyTurn = true;
    private String selectedSquare = null;
    private String playerColor = "white";
    private boolean gameEnded = false;
    private boolean isAIMode = false;
    private boolean isLocalMode = false;
    private boolean isBoardFlipped = false;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private StockfishEngine stockfish;
    private int aiThinkTime = 1000;

    // Highlight colors and tracking - now using dots instead of background colors
    private Color legalMoveDotColor = new Color(80, 50, 30, 200);   // Darker brown dot for legal moves
    private Color captureMoveDotColor = new Color(255, 0, 0, 200);  // Red dot for capture moves
    private java.util.List<ChessSquareButton> highlightedSquares = new java.util.ArrayList<>();
    
    // Last move tracking for border highlighting
    private String lastMoveFrom = null;
    private String lastMoveTo = null;
    
    // Check highlighting
    private ChessSquareButton kingInCheckHighlight = null;
    private Color checkHighlightColor = new Color(255, 0, 0); // Red border for check

    // Add these fields to ChessClient
    private JDialog chatDialog;
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendChatButton;
    private JLabel aiDifficultyLabel; // Add this field

    private String dragFromSquare = null;
    private ChessSquareButton dragFromButton = null;
    private JPanel boardPanel;

    private JWindow dragWindow = null;
    private JLabel dragLabel = null;
    private ImageIcon draggingIcon = null;
    
    // Drag and drop improvements
    private static final int DRAG_THRESHOLD = 5; // pixels
    private Point dragStartPoint = null;
    private boolean isDragging = false;
    private Color dragHighlightColor = new Color(255, 255, 0, 100); // Semi-transparent yellow

    // Captured pieces tracking
    private java.util.List<Piece> whiteCaptured = new java.util.ArrayList<>();
    private java.util.List<Piece> blackCaptured = new java.util.ArrayList<>();
    private int whiteScore = 0;
    private int blackScore = 0;
    private JPanel topCapturedPanel;
    private JPanel bottomCapturedPanel;

    // AI tutor improvements
    private String fenBeforeMove = null;
    private String lastPlayerMove = null;
    private ChessSquareButton bestMoveFromHighlight = null;
    private ChessSquareButton bestMoveToHighlight = null;
    private Color bestMoveFromColor = new Color(100, 149, 237); // Cornflower blue
    private Color bestMoveToColor = new Color(255, 165, 0);     // Orange

    // Timer functionality
    private JLabel whiteClockLabel;
    private JLabel blackClockLabel;
    private javax.swing.Timer clockTimer;
    private int whiteTimeLeft = 300; // seconds
    private int blackTimeLeft = 300; // seconds
    private int initialTime = 300; // seconds
    private boolean whiteClockRunning = false;
    private boolean blackClockRunning = false;
    private JLayeredPane layeredBoardPane;

    // Add these fields to ChessClient:
    private JPanel aiHintOverlayPanel;
    private String aiHintFromSquare = null, aiHintToSquare = null, aiHintLabel = null;
    private javax.swing.Timer aiHintTimer = null;

    public ChessClient() {
        super("Chess Game - Local/Online/AI");
        loadIcons();
        initUI();
        initializeStockfish();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(800, 800); // Increased default height for larger board
        setMinimumSize(new Dimension(600, 800)); // Updated minimum height to match default
        setLocationRelativeTo(null);
        
        // Add component listener to dynamically adjust minimum size based on board requirements
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // Only adjust minimum size if window is getting too small
                if (getWidth() < 400 || getHeight() < 800) {
                    SwingUtilities.invokeLater(() -> {
                        setMinimumSize(new Dimension(400, 800));
                        if (getWidth() < 400 || getHeight() < 800) {
                            setSize(400, 800);
                        }
                    });
                }
            }
        });
        
        setVisible(true);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(
                    ChessClient.this,
                    "Are you sure you want to quit the game?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION
                );
                if (result == JOptionPane.YES_OPTION) {
                    // Optionally: send a disconnect message to the server here
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void loadIcons() {
        String[] types = {"K", "Q", "R", "B", "N", "P"};
        for (String c : new String[]{"w", "b"}) {
            for (String t : types) {
                String key = c + t;
                File imgFile = new File(IMAGE_PATH + key + ".png");
                if (imgFile.exists()) {
                    ImageIcon originalIcon = new ImageIcon(imgFile.getAbsolutePath());
                    Image scaledImage = originalIcon.getImage().getScaledInstance(
                            60, 60, Image.SCALE_SMOOTH); // Keep original piece size
                    icons.put(key, new ImageIcon(scaledImage));
                }
            }
        }
    }

    private void initializeStockfish() {
        try {
            stockfish = new StockfishEngine("/Users/achaysumal/Downloads/final.boss/stockfishfinal");
            System.out.println("Stockfish initialized successfully");
        } catch (Exception e) {
            stockfish = null;
            System.err.println("Stockfish not found: " + e.getMessage());
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel(new BorderLayout());
        gameInfoLabel = new JLabel("Select mode and start game", SwingConstants.CENTER);
        gameInfoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        controlPanel.add(gameInfoLabel, BorderLayout.NORTH);

        JPanel modePanel = new JPanel(new FlowLayout());
        modeSelector = new JComboBox<>(new String[]{"Local Two Player", "Online Two Player", "Single-Player vs AI"});
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(modeSelector);

        aiDifficultyLabel = new JLabel("AI Difficulty:");
        modePanel.add(aiDifficultyLabel);
        difficultySelector = new JComboBox<>(new String[]{
                "Easy (500ms)", "Medium (1000ms)", "Hard (2000ms)", "Expert (3000ms)"
        });
        difficultySelector.setSelectedIndex(1);
        modePanel.add(difficultySelector);

        // Hide AI difficulty by default
        aiDifficultyLabel.setVisible(false);
        difficultySelector.setVisible(false);

        // Add listener to modeSelector to show/hide AI difficulty
        modeSelector.addItemListener(e -> {
            String selected = (String) modeSelector.getSelectedItem();
            boolean showAI = "Single-Player vs AI".equals(selected);
            aiDifficultyLabel.setVisible(showAI);
            difficultySelector.setVisible(showAI);
            modePanel.revalidate();
            modePanel.repaint();
        });

        startButton = new JButton("Start Game");
        startButton.addActionListener(e -> startGame());
        modePanel.add(startButton);

        controlPanel.add(modePanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.NORTH);

        boardPanel = new JPanel(null); // Use absolute positioning for dynamic resizing
        boardPanel.setPreferredSize(new Dimension(BOARD_SIZE * SQUARE_SIZE, BOARD_SIZE * SQUARE_SIZE));
        
        // Create layered pane for board and clocks
        layeredBoardPane = new JLayeredPane();
        layeredBoardPane.setPreferredSize(new Dimension(BOARD_SIZE * SQUARE_SIZE, BOARD_SIZE * SQUARE_SIZE));
        
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                ChessSquareButton btn = new ChessSquareButton();
                btn.setPreferredSize(new Dimension(SQUARE_SIZE, SQUARE_SIZE));
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setBackground((r + c) % 2 == 0 ? new Color(240, 217, 181) : new Color(181, 136, 99));
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBorderPainted(false);
                btn.setFocusPainted(false);
                btn.setHorizontalAlignment(SwingConstants.CENTER);
                btn.setVerticalAlignment(SwingConstants.CENTER);
                final int row = r, col = c;
                btn.addActionListener(e -> onSquareClick(row, col));
                btn.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        // Store drag start point
                        dragStartPoint = e.getPoint();
                        isDragging = false;
                        
                        // Calculate the square coordinates
                        int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - row : row;
                        int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - col : col;
                        String square = ("" + (char)('a' + displayCol) + (8 - displayRow)).toUpperCase();
                        
                        // Check if piece belongs to current player
                        Piece piece = game.getPiece(Square.fromValue(square));
                        Side currentPlayerSide;
                        
                        if (isLocalMode) {
                            // In local mode, determine side based on whose turn it is
                            currentPlayerSide = isMyTurn ? Side.WHITE : Side.BLACK;
                        } else if (isAIMode) {
                            Side humanSide = Side.valueOf(playerColor.toUpperCase());
                            if (!isMyTurn) {
                                statusLabel.setText("Not your turn!");
                                return;
                            }
                            if (piece == Piece.NONE || piece.getPieceSide() != humanSide) {
                                statusLabel.setText("You can only move your own pieces!");
                                return;
                            }
                            currentPlayerSide = humanSide;
                        } else {
                            // In online mode, player moves their assigned color
                            currentPlayerSide = Side.valueOf(playerColor.toUpperCase());
                        }
                        
                        if (piece == Piece.NONE || piece.getPieceSide() != currentPlayerSide) {
                            statusLabel.setText("You can only move your own pieces!");
                            return;
                        }
                        
                        // Store drag information
                        dragFromButton = btn;
                        dragFromSquare = square;
                        
                        // Show legal moves for visual feedback
                        highlightLegalMoves(square);
                    }
                    
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (dragFromSquare == null || !isDragging) {
                            // This was a click, not a drag - let the action listener handle it
                            dragFromSquare = null;
                            dragFromButton = null;
                            dragStartPoint = null;
                            return;
                        }
                        
                        // Clean up drag window
                        if (dragWindow != null) {
                            dragWindow.setVisible(false);
                            dragWindow.dispose();
                            dragWindow = null;
                            dragLabel = null;
                            draggingIcon = null;
                        }
                        
                        // Find which button the mouse was released over
                        Point boardPoint = SwingUtilities.convertPoint(btn, e.getPoint(), boardPanel);
                        Component comp = boardPanel.getComponentAt(boardPoint);
                        for (int r2 = 0; r2 < BOARD_SIZE; r2++) {
                            for (int c2 = 0; c2 < BOARD_SIZE; c2++) {
                                if (squares[r2][c2] == comp) {
                                    int displayRow2 = isBoardFlipped ? BOARD_SIZE - 1 - r2 : r2;
                                    int displayCol2 = isBoardFlipped ? BOARD_SIZE - 1 - c2 : c2;
                                    String dragToSquare = ("" + (char)('a' + displayCol2) + (8 - displayRow2)).toUpperCase();
                                    handleDragAndDropMove(dragFromSquare, dragToSquare);
                                    break;
                                }
                            }
                        }
                        
                        // Reset drag state
                        dragFromSquare = null;
                        dragFromButton = null;
                        dragStartPoint = null;
                        isDragging = false;
                        clearHighlights();
                        clearLastMoveHighlight(); // This will clear the drag border
                    }
                });
                btn.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        if (dragFromSquare == null || dragStartPoint == null) return;
                        
                        // Check if we've moved enough to start dragging
                        int distance = (int) Math.sqrt(
                            Math.pow(e.getX() - dragStartPoint.x, 2) + 
                            Math.pow(e.getY() - dragStartPoint.y, 2)
                        );
                        
                        if (distance > DRAG_THRESHOLD && !isDragging) {
                            isDragging = true;
                            // Highlight the source square with border to show drag is active
                            if (dragFromButton != null) {
                                dragFromButton.setShowBorder(true);
                                dragFromButton.setBorderColor(new Color(255, 255, 0)); // Yellow border
                            }
                        }
                        
                        if (!isDragging) return;
                        
                        // Create drag window if it doesn't exist
                        if (dragWindow == null) {
                            Piece piece = game.getPiece(Square.fromValue(dragFromSquare));
                            String iconKey = getPieceTypeKey(piece);
                            draggingIcon = icons.get(iconKey);
                            if (draggingIcon == null) return;
                            
                            dragWindow = new JWindow();
                            dragWindow.setBackground(new Color(0, 0, 0, 0)); // Transparent background
                            dragLabel = new JLabel(draggingIcon);
                            dragLabel.setOpaque(false);
                            dragWindow.getContentPane().add(dragLabel);
                            dragWindow.setSize(draggingIcon.getIconWidth(), draggingIcon.getIconHeight());
                            dragWindow.setAlwaysOnTop(true);
                        }
                        
                        // Move the drag window to follow the mouse
                        Point screenPoint = e.getLocationOnScreen();
                        dragWindow.setLocation(
                            screenPoint.x - dragWindow.getWidth() / 2, 
                            screenPoint.y - dragWindow.getHeight() / 2
                        );
                        dragWindow.setVisible(true);
                    }
                });
                squares[r][c] = btn;
                btn.setBounds(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                boardPanel.add(btn);
            }
        }
        
        // Add board to layered pane
        boardPanel.setBounds(0, 0, BOARD_SIZE * SQUARE_SIZE, BOARD_SIZE * SQUARE_SIZE);
        layeredBoardPane.add(boardPanel, JLayeredPane.DEFAULT_LAYER);
        
        // Create clock labels
        whiteClockLabel = createStyledClockLabel("05:00");
        blackClockLabel = createStyledClockLabel("05:00");
        whiteClockLabel.setVisible(false);
        blackClockLabel.setVisible(false);
        
        // Position clocks at top left (black) and top right (white)
        int clockW = 90, clockH = 40, pad = 10;
        blackClockLabel.setBounds(pad, pad, clockW, clockH);
        whiteClockLabel.setBounds(BOARD_SIZE * SQUARE_SIZE - clockW - pad, pad, clockW, clockH);
        layeredBoardPane.add(blackClockLabel, JLayeredPane.PALETTE_LAYER);
        layeredBoardPane.add(whiteClockLabel, JLayeredPane.PALETTE_LAYER);
        
        // Initialize captured panels (above and below board)
        topCapturedPanel = new JPanel();
        topCapturedPanel.setLayout(new BoxLayout(topCapturedPanel, BoxLayout.X_AXIS));
        topCapturedPanel.setPreferredSize(new Dimension(BOARD_SIZE * SQUARE_SIZE, 50));
        topCapturedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Captured Pieces"));

        bottomCapturedPanel = new JPanel();
        bottomCapturedPanel.setLayout(new BoxLayout(bottomCapturedPanel, BoxLayout.X_AXIS));
        bottomCapturedPanel.setPreferredSize(new Dimension(BOARD_SIZE * SQUARE_SIZE, 50));
        bottomCapturedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Captured Pieces"));

        // Initialize captured display
        updateCapturedDisplay();

        // Center panel for the board with captured pieces above and below
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(topCapturedPanel, BorderLayout.NORTH);
        centerPanel.add(layeredBoardPane, BorderLayout.CENTER);
        centerPanel.add(bottomCapturedPanel, BorderLayout.SOUTH);
        
        // Make the center panel resizable and maintain aspect ratio
        centerPanel.setLayout(new BorderLayout() {
            @Override
            public void layoutContainer(Container target) {
                super.layoutContainer(target);
                
                // Get available space
                int availableWidth = target.getWidth();
                int availableHeight = target.getHeight();
                
                // Check if we're in fullscreen mode
                boolean isFullscreen = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
                
                // Account for captured panels and some padding
                int reservedHeight = 120; // 50 for top panel + 50 for bottom panel + 20 padding
                int reservedWidth = 40; // Some padding on sides
                
                // Calculate board size maintaining aspect ratio
                int maxBoardWidth = availableWidth - reservedWidth;
                int maxBoardHeight = availableHeight - reservedHeight;
                
                // Ensure we have enough space
                if (maxBoardWidth <= 0 || maxBoardHeight <= 0) {
                    System.out.println("Not enough space for layout: " + availableWidth + "x" + availableHeight);
                    return; // Not enough space, don't layout
                }
                
                // If window is too small, enforce minimum size
                if (availableWidth < 400 || availableHeight < 800) {
                    // Force window to minimum size
                    SwingUtilities.invokeLater(() -> {
                        Dimension minSize = new Dimension(400, 800);
                        if (getWidth() < minSize.width || getHeight() < minSize.height) {
                            setSize(minSize);
                        }
                    });
                    // Don't return, continue with layout to ensure board is visible
                }
                
                int squareSize;
                int actualBoardSize;
                
                if (isFullscreen) {
                    // In fullscreen, use a predetermined default size
                    squareSize = 100; // Increased fixed square size for fullscreen
                    actualBoardSize = squareSize * BOARD_SIZE;
                    
                    // Ensure the board fits within available space
                    if (actualBoardSize > maxBoardWidth || actualBoardSize > maxBoardHeight) {
                        // If the default size is too large, scale it down
                        int maxPossibleSize = Math.min(maxBoardWidth, maxBoardHeight);
                        squareSize = maxPossibleSize / BOARD_SIZE;
                        squareSize = Math.max(squareSize, 40); // Minimum size
                        actualBoardSize = squareSize * BOARD_SIZE;
                    }
                } else {
                    // Normal window mode - calculate optimal size
                    int boardSize = Math.min(maxBoardWidth, maxBoardHeight);
                    squareSize = boardSize / BOARD_SIZE;
                    
                    // Ensure minimum and maximum square size
                    squareSize = Math.max(squareSize, 40);
                    squareSize = Math.min(squareSize, 120); // Reduced max size for normal mode
                    
                    // Recalculate board size with the final square size
                    actualBoardSize = squareSize * BOARD_SIZE;
                    
                    // Ensure board fits within available space with safety margin
                    if (actualBoardSize > maxBoardWidth - 20 || actualBoardSize > maxBoardHeight - 20) {
                        // Recalculate with safety margin
                        int safeMaxWidth = maxBoardWidth - 20;
                        int safeMaxHeight = maxBoardHeight - 20;
                        actualBoardSize = Math.min(safeMaxWidth, safeMaxHeight);
                        squareSize = actualBoardSize / BOARD_SIZE;
                        // Ensure square size is still reasonable
                        squareSize = Math.max(squareSize, 40);
                        squareSize = Math.min(squareSize, 120);
                        actualBoardSize = squareSize * BOARD_SIZE;
                    }
                }
                
                // Center the board with proper spacing
                int boardX = (availableWidth - actualBoardSize) / 2;
                int boardY = 60; // Start below the top panel with some padding
                
                // Ensure board doesn't go outside window bounds
                if (boardX < 0) boardX = 10;
                if (boardY < 0) boardY = 10;
                if (boardX + actualBoardSize > availableWidth) {
                    boardX = availableWidth - actualBoardSize - 10;
                }
                if (boardY + actualBoardSize > availableHeight) {
                    boardY = availableHeight - actualBoardSize - 10;
                }
                
                // Position captured panels to stick to the board
                if (topCapturedPanel != null) {
                    int topPanelY = Math.max(10, boardY - 50); // Don't go above window
                    int topPanelWidth = Math.min(actualBoardSize, availableWidth - 20);
                    topCapturedPanel.setBounds(boardX, topPanelY, topPanelWidth, 50);
                }
                if (bottomCapturedPanel != null) {
                    int bottomPanelY = Math.min(availableHeight - 60, boardY + actualBoardSize); // Don't go below window
                    int bottomPanelWidth = Math.min(actualBoardSize, availableWidth - 20);
                    bottomCapturedPanel.setBounds(boardX, bottomPanelY, bottomPanelWidth, 50);
                }
                if (layeredBoardPane != null) {
                    layeredBoardPane.setBounds(boardX, boardY, actualBoardSize, actualBoardSize);
                    // Update square positions for the new size
                    updateSquarePositions(squareSize);
                }
            }
        });
        
        // Add window listener to handle fullscreen transitions
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowStateChanged(java.awt.event.WindowEvent e) {
                // Force layout recalculation when window state changes (like going fullscreen)
                SwingUtilities.invokeLater(() -> {
                    centerPanel.revalidate();
                    centerPanel.repaint();
                });
            }
        });
        
        add(centerPanel, BorderLayout.CENTER);

        // Create a single bottom panel with status and buttons side by side
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Add padding
        
        // Status label (left side)
        statusLabel = new JLabel("Select mode and click Start Game");
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // Button panel (right side)
        JPanel buttonPanel = new JPanel(new FlowLayout());
        resetButton = new JButton("Reset Game");
        resetButton.addActionListener(e -> resetGame());
        buttonPanel.add(resetButton);

        showMovesButton = new JButton("Show Legal Moves");
        showMovesButton.addActionListener(e -> showLegalMoves());
        buttonPanel.add(showMovesButton);

        // Add Save/Load buttons
        saveButton = new JButton("Save Game");
        saveButton.addActionListener(e -> saveGame());
        buttonPanel.add(saveButton);

        loadButton = new JButton("Load Game");
        loadButton.addActionListener(e -> loadGame());
        buttonPanel.add(loadButton);

        tutorButton = new JButton("AI Tutor");
        tutorButton.addActionListener(e -> showAITutorSuggestion());
        buttonPanel.add(tutorButton);

        // Add Show Best Move button
        showBestMoveButton = new JButton("Show Best Move");
        showBestMoveButton.addActionListener(e -> showBestMoveHint());
        buttonPanel.add(showBestMoveButton);

        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateBoard();
        updateGameInfo();

        // In initUI(), after adding boardPanel to layeredBoardPane, add:
        aiHintOverlayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (aiHintFromSquare != null && aiHintToSquare != null) {
                    drawHintArrow((Graphics2D) g, aiHintFromSquare, aiHintToSquare, aiHintLabel);
                }
            }
            @Override
            public boolean contains(int x, int y) {
                // Only block mouse events if a hint is showing
                return aiHintFromSquare != null && aiHintToSquare != null;
            }
        };
        aiHintOverlayPanel.setOpaque(false);
        aiHintOverlayPanel.setBounds(boardPanel.getBounds());
        layeredBoardPane.add(aiHintOverlayPanel, JLayeredPane.PALETTE_LAYER);

        // Update overlay bounds on resize
        layeredBoardPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent e) {
                aiHintOverlayPanel.setBounds(0, 0, layeredBoardPane.getWidth(), layeredBoardPane.getHeight());
            }
        });

        // Hide overlay on click or when drag starts
        aiHintOverlayPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                clearAiHintOverlay();
            }
        });
        
        // Also clear overlay when mouse drag starts on the board
        boardPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // Clear hint overlay when user starts interacting with the board
                if (aiHintFromSquare != null && aiHintToSquare != null) {
                    clearAiHintOverlay();
                }
            }
        });
    }

    private void initChatDialog() {
        if (chatDialog != null) return;
        chatDialog = new JDialog(this, "Chat with Friend", false);
        chatDialog.setSize(300, 400);
        chatDialog.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        chatInput = new JTextField();
        sendChatButton = new JButton("Send");
        sendChatButton.addActionListener(e -> sendChatMessage());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendChatButton, BorderLayout.EAST);

        chatDialog.add(scrollPane, BorderLayout.CENTER);
        chatDialog.add(inputPanel, BorderLayout.SOUTH);

        chatInput.addActionListener(e -> sendChatMessage());
    }

    private void startGame() {
        String mode = (String) modeSelector.getSelectedItem();
        isLocalMode = "Local Two Player".equals(mode);
        isAIMode = "Single-Player vs AI".equals(mode);

        resetGame();

        if (isLocalMode) {
            // For local mode, show simple message that white starts
            JOptionPane.showMessageDialog(this, 
                "White to play first!", 
                "Local Two Player Game", 
                JOptionPane.INFORMATION_MESSAGE);
            playerColor = "white";
            isBoardFlipped = false;
            isMyTurn = true;
            statusLabel.setText("White's turn (Local)");
            gameInfoLabel.setText("Local Two Player Mode");
            updateBoard();
        } else if (isAIMode) {
            playerColor = promptForColor();
            isBoardFlipped = "black".equalsIgnoreCase(playerColor);
            if (stockfish == null) {
                JOptionPane.showMessageDialog(this, "Stockfish engine not available.", "AI Unavailable", JOptionPane.ERROR_MESSAGE);
                return;
            }
            isMyTurn = "white".equalsIgnoreCase(playerColor);
            String difficulty = (String) difficultySelector.getSelectedItem();
            if (difficulty.contains("500ms")) aiThinkTime = 500;
            else if (difficulty.contains("1000ms")) aiThinkTime = 1000;
            else if (difficulty.contains("2000ms")) aiThinkTime = 2000;
            else if (difficulty.contains("3000ms")) aiThinkTime = 3000;
            statusLabel.setText("Your turn (" + playerColor + " vs AI)");
            gameInfoLabel.setText("Single-player vs AI - Difficulty: " + difficulty);
            updateBoard();
            if ("black".equalsIgnoreCase(playerColor)) {
                isMyTurn = false;
                statusLabel.setText("AI is thinking...");
                SwingUtilities.invokeLater(this::makeAIMove);
            }
        } else {
            // --- Prompt for host or join ---
            String[] options = {"Host", "Join"};
            int choice = JOptionPane.showOptionDialog(
                this,
                "Do you want to host the game (server) or join (client)?",
                "Online Two Player Setup",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            );
            if (choice == 0) {
                // Host: Start server in a new process
                try {
                    String localIP = getLocalIpAddress();
                    String javaHome = System.getProperty("java.home");
                    String javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
                    String classpath = System.getProperty("java.class.path");
                    String mainClass = "server.ChessServer";
                    ProcessBuilder builder = new ProcessBuilder(
                        javaBin, "-cp", classpath, mainClass
                    );
                    builder.inheritIO();
                    Process serverProcess = builder.start();
                    Thread.sleep(2000);
                    // Check if server is actually listening
                    try (Socket testSocket = new Socket()) {
                        testSocket.connect(new java.net.InetSocketAddress("127.0.0.1", 8000), 5000);
                        testSocket.close();
                    } catch (Exception e) {
                        throw new Exception("Server failed to start properly: " + e.getMessage());
                    }
                    JOptionPane.showMessageDialog(this, 
                        "Server started successfully!\n\n" +
                        "Your friend can join using this IP address:\n" +
                        localIP + "\n\n" +
                        "Port: 8000",
                        "Server Started", 
                        JOptionPane.INFORMATION_MESSAGE);
                    startTwoPlayerGame(localIP);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage());
                }
            } else if (choice == 1) {
                String ip = JOptionPane.showInputDialog(this, "Enter host IP address:", "Connect to Server", JOptionPane.QUESTION_MESSAGE);
                if (ip != null && !ip.trim().isEmpty()) {
                    startTwoPlayerGame(ip.trim());
                }
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String ip = JOptionPane.showInputDialog(this, "Could not detect LAN IP address. Please enter your IP address:", "Enter IP Address", JOptionPane.QUESTION_MESSAGE);
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.trim();
        }
        return "";
    }

    private void startTwoPlayerGame(String serverIp) {
        statusLabel.setText("Connecting to server...");
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(serverIp, 8000), 10000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String hello = in.readLine();
                System.out.println("DEBUG: Handshake received: " + hello);
                if (hello == null || !hello.startsWith("COLOR:")) {
                    throw new IOException("Connected, but not a valid ChessServer (no handshake).\nFirst message: " + hello);
                }
                handleServerMessage(hello);
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleServerMessage(msg));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Client error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Client error: " + ex.getMessage(),
                            "Client Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        SwingUtilities.invokeLater(() -> {
            if (chatDialog == null) initChatDialog();
            chatDialog.setVisible(true);
        });
    }

    private void handleServerMessage(String msg) {
        System.out.println("DEBUG: Received server message: " + msg);
        if (msg.startsWith("COLOR:")) {
            playerColor = msg.substring(6);
            isBoardFlipped = "black".equalsIgnoreCase(playerColor);
            isMyTurn = "white".equalsIgnoreCase(playerColor);
            statusLabel.setText("You are playing as " + playerColor);
            gameInfoLabel.setText("Two-player online - You are " + playerColor);
            updateBoard();
            System.out.println("DEBUG: Set player color to " + playerColor + ", board flipped: " + isBoardFlipped + ", my turn: " + isMyTurn);
        } else if (msg.equals("START")) {
            statusLabel.setText("Game started! You are " + playerColor);
            System.out.println("DEBUG: Game started");
        } else if (msg.equals("YOUR_TURN")) {
            isMyTurn = true;
            statusLabel.setText("Your turn (" + playerColor + ")");
            checkGameState();
            System.out.println("DEBUG: It's my turn now");
        } else if (msg.startsWith("MOVE:")) {
            String move = msg.substring(5);
            applyMove(move);
            statusLabel.setText("Opponent moved: " + move);
            checkGameState();
            isMyTurn = true;
            System.out.println("DEBUG: Applied opponent move: " + move + ", now my turn: " + isMyTurn);
        } else if (msg.equals("RESET")) {
            resetGame();
            statusLabel.setText("Game reset");
            System.out.println("DEBUG: Game reset");
        } else if (msg.equals("GAME_FULL")) {
            statusLabel.setText("Game is full - cannot join");
            System.out.println("DEBUG: Game is full");
        } else if (msg.startsWith("CHAT:")) {
            String chatMsg = msg.substring(5);
            if (chatArea != null) {
                chatArea.append("Friend: " + chatMsg + "\n");
            }
            System.out.println("DEBUG: Received chat: " + chatMsg);
        }
    }

    private void resetGame() {
        game.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        selectedSquare = null;

        // Set turn based on game mode
        if (isLocalMode) {
            isMyTurn = true;
        } else if (isAIMode) {
            isMyTurn = "white".equalsIgnoreCase(playerColor);
        } else {
            // In online mode, wait for server to tell us whose turn it is
            isMyTurn = false;
        }

        gameEnded = false;
        // Don't change isBoardFlipped here; let handleServerMessage set it for online mode
        clearHighlights();
        clearLastMoveHighlight();
        clearCheckHighlight(); // Clear check highlight on reset
        lastMoveFrom = null;
        lastMoveTo = null;
        
        // Clear drag state
        dragFromSquare = null;
        dragFromButton = null;
        dragStartPoint = null;
        isDragging = false;
        if (dragWindow != null) {
            dragWindow.setVisible(false);
            dragWindow.dispose();
            dragWindow = null;
            dragLabel = null;
            draggingIcon = null;
        }
        
        // Reset captured pieces and scores
        whiteCaptured.clear();
        blackCaptured.clear();
        whiteScore = 0;
        blackScore = 0;
        updateCapturedDisplay();
        
        updateBoard();
        updateGameInfo();
    }

    private void onSquareClick(int row, int col) {
        if (gameEnded) {
            statusLabel.setText("Game has ended!");
            return;
        }
        if (isLocalMode) {
            handleLocalMove(row, col);
        } else if (isAIMode) {
            handleAIMove(row, col);
        } else {
            handleOnlineMove(row, col);
        }
    }

    // Local two-player with board flip
    private void handleLocalMove(int row, int col) {
        int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - row : row;
        int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - col : col;
        String sq = ("" + (char)('a' + displayCol) + (8 - displayRow)).toUpperCase();

        Side movingSide = isMyTurn ? Side.valueOf(playerColor.toUpperCase()) : (playerColor.equalsIgnoreCase("white") ? Side.BLACK : Side.WHITE);

        if (selectedSquare == null) {
            Piece piece = game.getPiece(Square.fromValue(sq));
            if (piece != Piece.NONE && piece.getPieceSide() == movingSide) {
                selectedSquare = sq;
                highlightLegalMoves(sq);
                statusLabel.setText((isMyTurn ? "White" : "Black") + " selected " + sq);
            }
        } else {
            if (sq.equals(selectedSquare)) {
                clearHighlights();
                selectedSquare = null;
                statusLabel.setText("Selection cleared.");
                return;
            }
            String moveStr = selectedSquare + sq;
            if (isPawnPromotion(selectedSquare, sq)) {
                String promo = promptForPromotion();
                if (promo != null) moveStr += promo;
            }
            Move move = new Move(moveStr, movingSide);
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
            boolean isLegal = false;
            for (Move legal : legalMoves) {
                if (legal.equals(move)) {
                    isLegal = true;
                    break;
                }
            }
            if (isLegal) {
                // Store board state before move for capture detection
                Piece[][] beforeBoard = getBoardMatrix(game);
                
                game.doMove(move);
                
                // Update captures after move
                Piece[][] afterBoard = getBoardMatrix(game);
                updateCaptures(beforeBoard, afterBoard);
                
                updateBoard();
                updateGameInfo();
                clearHighlights();
                highlightLastMove(selectedSquare, sq);
                selectedSquare = null;
                isMyTurn = !isMyTurn;
                statusLabel.setText((isMyTurn ? "White's" : "Black's") + " turn (Local)");
                checkGameState();
                
                // Flip board after a delay
                Timer flipTimer = new Timer(500, e -> {
                    isBoardFlipped = !isBoardFlipped;
                    updateBoard();
                    ((Timer)e.getSource()).stop();
                });
                flipTimer.setRepeats(false);
                flipTimer.start();
            } else {
                statusLabel.setText("Illegal move! Try again.");
                clearHighlights();
                selectedSquare = null;
            }
        }
    }

    // AI mode
    private void handleAIMove(int row, int col) {
        if (!isMyTurn) {
            statusLabel.setText("Not your turn!");
            return;
        }
        int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - row : row;
        int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - col : col;
        String clickedSquare = ("" + (char)('a' + displayCol) + (8 - displayRow)).toUpperCase();
        if (selectedSquare == null) {
            Piece piece = game.getPiece(Square.fromValue(clickedSquare));
            if (piece != Piece.NONE && piece.getPieceSide().toString().equalsIgnoreCase(playerColor)) {
                selectedSquare = clickedSquare;
                highlightLegalMoves(clickedSquare);
                statusLabel.setText("Selected " + clickedSquare + ". Click destination square.");
            }
        } else {
            if (clickedSquare.equals(selectedSquare)) {
                clearHighlights();
                selectedSquare = null;
                statusLabel.setText("Selection cleared. Choose a piece to move.");
                return;
            }
            String moveStr = selectedSquare + clickedSquare;
            if (isPawnPromotion(selectedSquare, clickedSquare)) {
                String promotion = promptForPromotion();
                if (promotion != null) moveStr += promotion.toLowerCase();
            }
            Move move = new Move(moveStr, Side.valueOf(playerColor.toUpperCase()));
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
            boolean isLegal = false;
            for (Move legal : legalMoves) {
                if (legal.equals(move)) {
                    isLegal = true;
                    break;
                }
            }
            if (isLegal) {
                // Store board state before move for capture detection
                Piece[][] beforeBoard = getBoardMatrix(game);
                
                game.doMove(move);
                
                // Update captures after move
                Piece[][] afterBoard = getBoardMatrix(game);
                updateCaptures(beforeBoard, afterBoard);
                
                updateBoard();
                updateGameInfo();
                clearHighlights();
                highlightLastMove(selectedSquare, clickedSquare);
                selectedSquare = null;
                isMyTurn = false;
                statusLabel.setText("AI is thinking...");
                SwingUtilities.invokeLater(this::makeAIMove);
                checkGameState();
            } else {
                statusLabel.setText("Illegal move! Try again.");
                clearHighlights();
                selectedSquare = null;
            }
        }
    }

    // Online mode
    private void handleOnlineMove(int row, int col) {
        if (!isMyTurn) {
            statusLabel.setText("Not your turn!");
            return;
        }
        
        // Check if server connection is available
        if (out == null) {
            statusLabel.setText("Not connected to server!");
            return;
        }
        
        int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - row : row;
        int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - col : col;
        String clickedSquare = ("" + (char)('a' + displayCol) + (8 - displayRow)).toUpperCase();
        if (selectedSquare == null) {
            Piece piece = game.getPiece(Square.fromValue(clickedSquare));
            if (piece != Piece.NONE && piece.getPieceSide().toString().equalsIgnoreCase(playerColor)) {
                selectedSquare = clickedSquare;
                highlightLegalMoves(clickedSquare);
                statusLabel.setText("Selected " + clickedSquare + ". Click destination square.");
            }
        } else {
            if (clickedSquare.equals(selectedSquare)) {
                clearHighlights();
                selectedSquare = null;
                statusLabel.setText("Selection cleared. Choose a piece to move.");
                return;
            }
            String moveStr = selectedSquare + clickedSquare;
            if (isPawnPromotion(selectedSquare, clickedSquare)) {
                String promotion = promptForPromotion();
                if (promotion != null) moveStr += promotion.toLowerCase();
            }
            Move move = new Move(moveStr, game.getSideToMove());
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
            boolean isLegal = false;
            for (Move legal : legalMoves) {
                if (legal.equals(move)) {
                    isLegal = true;
                    break;
                }
            }
            if (isLegal) {
                // Store board state before move for capture detection
                Piece[][] beforeBoard = getBoardMatrix(game);
                
                game.doMove(move);
                
                // Update captures after move
                Piece[][] afterBoard = getBoardMatrix(game);
                updateCaptures(beforeBoard, afterBoard);
                
                updateBoard();
                updateGameInfo();
                clearHighlights();
                highlightLastMove(selectedSquare, clickedSquare);
                selectedSquare = null;
                isMyTurn = false;
                
                // Send move to server with null check
                try {
                    out.println("MOVE:" + moveStr);
                    statusLabel.setText("Waiting for opponent...");
                } catch (Exception e) {
                    statusLabel.setText("Error sending move to server: " + e.getMessage());
                    isMyTurn = true; // Allow retry
                }
                
                checkGameState();
            } else {
                statusLabel.setText("Illegal move! Try again.");
                clearHighlights();
                selectedSquare = null;
            }
        }
    }

    private void makeAIMove() {
        if (stockfish == null || gameEnded) return;
        new Thread(() -> {
            try {
                String aiMove = stockfish.getBestMove(game.getFen(), aiThinkTime);
                if (aiMove != null && !aiMove.equals("(none)")) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            Move move = new Move(aiMove, playerColor.equalsIgnoreCase("white") ? Side.BLACK : Side.WHITE);
                            List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
                            boolean isLegal = false;
                            for (Move legal : legalMoves) {
                                if (legal.equals(move)) {
                                    isLegal = true;
                                    break;
                                }
                            }
                            if (isLegal) {
                                // Store board state before move for capture detection
                                Piece[][] beforeBoard = getBoardMatrix(game);
                                
                                game.doMove(move);
                                
                                // Update captures after move
                                Piece[][] afterBoard = getBoardMatrix(game);
                                updateCaptures(beforeBoard, afterBoard);
                                
                                updateBoard();
                                updateGameInfo();
                                highlightLastMove(aiMove.substring(0, 2), aiMove.substring(2, 4));
                                isMyTurn = true;
                                statusLabel.setText("AI moved: " + aiMove + ". Your turn.");
                                checkGameState();
                            } else {
                                statusLabel.setText("AI generated invalid move");
                                isMyTurn = true;
                            }
                        } catch (Exception e) {
                            statusLabel.setText("Error processing AI move: " + e.getMessage());
                            isMyTurn = true;
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("AI couldn't find a move");
                        isMyTurn = true;
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("AI error: " + e.getMessage());
                    isMyTurn = true;
                });
            }
        }).start();
    }

    private boolean isPawnPromotion(String from, String to) {
        Square fromSquare = Square.fromValue(from);
        Square toSquare = Square.fromValue(to);
        Piece piece = game.getPiece(fromSquare);

        if (piece.getPieceType() == PieceType.PAWN) {
            if (piece.getPieceSide() == Side.WHITE && toSquare.getRank().ordinal() == 7) {
                return true;
            }
            if (piece.getPieceSide() == Side.BLACK && toSquare.getRank().ordinal() == 0) {
                return true;
            }
        }
        return false;
    }

    private String promptForPromotion() {
        String[] options = {"q", "r", "b", "n"};
        String[] descriptions = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Choose piece for pawn promotion:",
                "Pawn Promotion",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                descriptions,
                descriptions[0]
        );
        return (choice >= 0) ? options[choice] : "q";
    }

    private String promptForColor() {
        String[] options = {"White", "Black"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Choose your color:",
                "Select Color",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        return (choice == 1) ? "black" : "white";
    }

    private void sendChatMessage() {
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty() && out != null) {
            out.println("CHAT:" + msg);
            chatArea.append("Me: " + msg + "\n");
            chatInput.setText("");
        }
    }

    // --- HIGHLIGHTING FEATURE, FLIP-SYNCED ---
    private void highlightLegalMoves(String fromSquare) {
        // Clear only the dots, not the borders
        for (ChessSquareButton btn : highlightedSquares) {
            btn.setShowDot(false);
        }
        highlightedSquares.clear();
        
        // Clear previous piece border highlights
        clearPreviousPieceHighlight();
        
        if (fromSquare == null) return;

        // Highlight the selected piece with a yellow border
        try {
            int fromFile = fromSquare.charAt(0) - 'A';
            int fromRank = 8 - Character.getNumericValue(fromSquare.charAt(1));
            int fromDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - fromRank : fromRank;
            int fromDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - fromFile : fromFile;
            
            // Check bounds before accessing array
            if (fromDisplayRow >= 0 && fromDisplayRow < BOARD_SIZE && 
                fromDisplayCol >= 0 && fromDisplayCol < BOARD_SIZE) {
                ChessSquareButton selectedButton = squares[fromDisplayRow][fromDisplayCol];
                selectedButton.setShowBorder(true);
                selectedButton.setBorderColor(new Color(255, 255, 0)); // Yellow border
                highlightedSquares.add(selectedButton);
            }
        } catch (Exception e) {
            System.err.println("Error highlighting selected square: " + fromSquare + " - " + e.getMessage());
            return;
        }

                List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
        Square from = Square.fromValue(fromSquare);

        for (Move move : legalMoves) {
            if (move.getFrom().equals(from)) {
                try {
                String toSquare = move.getTo().toString();
                int toFile = toSquare.charAt(0) - 'A';
                int toRank = 8 - Character.getNumericValue(toSquare.charAt(1));
                int toDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - toRank : toRank;
                int toDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - toFile : toFile;

                    // Check bounds before accessing array
                    if (toDisplayRow >= 0 && toDisplayRow < BOARD_SIZE && 
                        toDisplayCol >= 0 && toDisplayCol < BOARD_SIZE) {
                        ChessSquareButton destButton = squares[toDisplayRow][toDisplayCol];
                        destButton.setShowDot(true);
                        
                        // Check if this is a capture move
                        Square toSquareObj = Square.fromValue(toSquare);
                        Piece targetPiece = game.getPiece(toSquareObj);
                        boolean isCapture = (targetPiece != Piece.NONE);
                        
                        // Set dot color based on whether it's a capture
                        if (isCapture) {
                            destButton.setDotColor(captureMoveDotColor); // Red for captures
                        } else {
                            destButton.setDotColor(legalMoveDotColor); // Brown for regular moves
                        }
                        
                        highlightedSquares.add(destButton);
                    }
                } catch (Exception e) {
                    System.err.println("Error highlighting legal move destination: " + move.getTo() + " - " + e.getMessage());
                }
            }
        }
    }

        private void clearHighlights() {
        for (ChessSquareButton btn : highlightedSquares) {
            btn.setShowDot(false);
        }
        highlightedSquares.clear();

        // Also clear best move highlights
        if (bestMoveFromHighlight != null) {
            bestMoveFromHighlight.setShowBorder(false);
            resetButtonColor(bestMoveFromHighlight);
            bestMoveFromHighlight = null;
        }
        if (bestMoveToHighlight != null) {
            bestMoveToHighlight.setShowBorder(false);
            resetButtonColor(bestMoveToHighlight);
            bestMoveToHighlight = null;
        }
        
        // Don't clear check highlight here - it should persist
    }
    
    private void clearPreviousPieceHighlight() {
        // Clear all border highlights except check highlight and last move highlights
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                ChessSquareButton btn = squares[r][c];
                if (btn != kingInCheckHighlight && 
                    !btn.equals(lastMoveFrom != null ? getButtonForSquare(lastMoveFrom) : null) &&
                    !btn.equals(lastMoveTo != null ? getButtonForSquare(lastMoveTo) : null)) {
                    btn.setShowBorder(false);
                }
            }
        }
    }
    
    private ChessSquareButton getButtonForSquare(String square) {
        try {
            int file = square.charAt(0) - 'A';
            int rank = 8 - Character.getNumericValue(square.charAt(1));
            int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - rank : rank;
            int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - file : file;
            
            if (displayRow >= 0 && displayRow < BOARD_SIZE && 
                displayCol >= 0 && displayCol < BOARD_SIZE) {
                return squares[displayRow][displayCol];
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return null;
    }
    
    private void highlightLastMove(String fromSquare, String toSquare) {
        // Clear previous last move highlighting
        clearLastMoveHighlight();
        
        // Set new last move
        lastMoveFrom = fromSquare;
        lastMoveTo = toSquare;
        
        // Highlight the squares with yellow borders
        if (fromSquare != null) {
            try {
                int fromFile = fromSquare.charAt(0) - 'A';
                int fromRank = 8 - Character.getNumericValue(fromSquare.charAt(1));
                int fromDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - fromRank : fromRank;
                int fromDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - fromFile : fromFile;
                
                // Check bounds before accessing array
                if (fromDisplayRow >= 0 && fromDisplayRow < BOARD_SIZE && 
                    fromDisplayCol >= 0 && fromDisplayCol < BOARD_SIZE) {
                    squares[fromDisplayRow][fromDisplayCol].setShowBorder(true);
                }
            } catch (Exception e) {
                System.err.println("Error highlighting from square: " + fromSquare + " - " + e.getMessage());
            }
        }
        
        if (toSquare != null) {
            try {
                int toFile = toSquare.charAt(0) - 'A';
                int toRank = 8 - Character.getNumericValue(toSquare.charAt(1));
                int toDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - toRank : toRank;
                int toDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - toFile : toFile;
                
                // Check bounds before accessing array
                if (toDisplayRow >= 0 && toDisplayRow < BOARD_SIZE && 
                    toDisplayCol >= 0 && toDisplayCol < BOARD_SIZE) {
                    squares[toDisplayRow][toDisplayCol].setShowBorder(true);
                }
            } catch (Exception e) {
                System.err.println("Error highlighting to square: " + toSquare + " - " + e.getMessage());
            }
        }
    }
    
    private void updateLastMoveHighlighting() {
        // Re-highlight the last move with current board orientation
        if (lastMoveFrom != null && lastMoveTo != null) {
            clearLastMoveHighlight();
            
            // Highlight the squares with yellow borders using current board orientation
            if (lastMoveFrom != null) {
                try {
                    int fromFile = lastMoveFrom.charAt(0) - 'A';
                    int fromRank = 8 - Character.getNumericValue(lastMoveFrom.charAt(1));
                    int fromDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - fromRank : fromRank;
                    int fromDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - fromFile : fromFile;
                    
                    // Check bounds before accessing array
                    if (fromDisplayRow >= 0 && fromDisplayRow < BOARD_SIZE && 
                        fromDisplayCol >= 0 && fromDisplayCol < BOARD_SIZE) {
                        squares[fromDisplayRow][fromDisplayCol].setShowBorder(true);
                    }
                } catch (Exception e) {
                    System.err.println("Error highlighting from square: " + lastMoveFrom + " - " + e.getMessage());
                }
            }
            
            if (lastMoveTo != null) {
                try {
                    int toFile = lastMoveTo.charAt(0) - 'A';
                    int toRank = 8 - Character.getNumericValue(lastMoveTo.charAt(1));
                    int toDisplayRow = isBoardFlipped ? BOARD_SIZE - 1 - toRank : toRank;
                    int toDisplayCol = isBoardFlipped ? BOARD_SIZE - 1 - toFile : toFile;
                    
                    // Check bounds before accessing array
                    if (toDisplayRow >= 0 && toDisplayRow < BOARD_SIZE && 
                        toDisplayCol >= 0 && toDisplayCol < BOARD_SIZE) {
                        squares[toDisplayRow][toDisplayCol].setShowBorder(true);
                    }
                } catch (Exception e) {
                    System.err.println("Error highlighting to square: " + lastMoveTo + " - " + e.getMessage());
                }
            }
        }
    }
    
    private void clearLastMoveHighlight() {
        // Clear borders from all squares
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                squares[r][c].setShowBorder(false);
            }
        }
    }
    
    private void highlightKingInCheck() {
        // Only highlight if the king is actually in check
        if (kingInCheckHighlight != null) {
            kingInCheckHighlight.setShowBorder(false);
            kingInCheckHighlight = null;
        }
        if (!game.isKingAttacked()) return;
        Side sideInCheck = game.getSideToMove();
        Piece kingPiece = (sideInCheck == Side.WHITE) ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String sq = ("" + (char)('a' + c) + (8 - r)).toUpperCase();
                Square square = Square.fromValue(sq);
                Piece piece = game.getPiece(square);
                if (piece == kingPiece) {
                    int displayR = isBoardFlipped ? BOARD_SIZE - 1 - r : r;
                    int displayC = isBoardFlipped ? BOARD_SIZE - 1 - c : c;
                    kingInCheckHighlight = squares[displayR][displayC];
                    kingInCheckHighlight.setShowBorder(true);
                    kingInCheckHighlight.setBorderColor(checkHighlightColor);
                    return;
                }
            }
        }
    }
    
    private void clearCheckHighlight() {
        if (kingInCheckHighlight != null) {
            kingInCheckHighlight.setShowBorder(false);
            kingInCheckHighlight = null;
        }
    }
    


    private void resetButtonColor(ChessSquareButton btn) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (squares[r][c] == btn) {
                    squares[r][c].setBackground((r + c) % 2 == 0
                        ? new Color(240, 217, 181)
                        : new Color(181, 136, 99));
                    squares[r][c].setShowBorder(false); // Clear any borders
                    return;
                }
            }
        }
    }
    // --- END HIGHLIGHTING FEATURE ---

    private void showLegalMoves() {
        if (selectedSquare != null) {
            highlightLegalMoves(selectedSquare);
        } else {
            JOptionPane.showMessageDialog(this, "Please select a piece first!");
        }
    }

    private void applyMove(String moveString) {
        try {
            Move move = new Move(moveString.toUpperCase(), game.getSideToMove());
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
            boolean isLegal = false;
            for (Move legal : legalMoves) {
                if (legal.equals(move)) {
                    isLegal = true;
                    break;
                }
            }
            if (isLegal) {
                // Store board state before move for capture detection
                Piece[][] beforeBoard = getBoardMatrix(game);
                
                game.doMove(move);
                
                // Update captures after move
                Piece[][] afterBoard = getBoardMatrix(game);
                updateCaptures(beforeBoard, afterBoard);
                
                updateBoard();
                updateGameInfo();
                highlightLastMove(moveString.substring(0, 2), moveString.substring(2, 4));
            } else {
                System.err.println("Received illegal move from server: " + moveString);
            }
        } catch (Exception e) {
            System.err.println("Error applying move " + moveString + ": " + e.getMessage());
        }
    }

    private void checkGameState() {
        if (game.isKingAttacked()) {
            if (game.isMated()) {
                gameEnded = true;
                Side winner = game.getSideToMove() == Side.WHITE ? Side.BLACK : Side.WHITE;
                String winnerName = isAIMode ?
                        (winner == Side.WHITE ? "You" : "AI") :
                        winner.toString();
                statusLabel.setText("CHECKMATE! " + winnerName + " wins!");
                JOptionPane.showMessageDialog(this, "Checkmate! " + winnerName + " wins the game!");
                clearCheckHighlight(); // Clear check highlight on checkmate
            } else {
                statusLabel.setText(statusLabel.getText() + " - " + game.getSideToMove() + " is in CHECK!");
                highlightKingInCheck(); // Highlight the king in check
            }
        } else {
            clearCheckHighlight(); // Clear check highlight when not in check
            if (game.isStaleMate()) {
                gameEnded = true;
                statusLabel.setText("STALEMATE! Game is a draw!");
                JOptionPane.showMessageDialog(this, "Stalemate! The game is a draw.");
            } else if (game.isDraw()) {
                gameEnded = true;
                String drawReason = getDrawReason();
                statusLabel.setText("DRAW! " + drawReason);
                JOptionPane.showMessageDialog(this, "Draw! " + drawReason);
            }
        }
    }

    private String getDrawReason() {
        if (game.isRepetition()) {
            return "Three-fold repetition";
        } else if (game.isInsufficientMaterial()) {
            return "Insufficient material";
        } else if (game.getHalfMoveCounter() >= 100) {
            return "50-move rule";
        } else {
            return "Draw by other rules";
        }
    }

    private void updateGameInfo() {
        String fen = game.getFen();
        String[] fenParts = fen.split(" ");
        int fullMoveNumber = Integer.parseInt(fenParts[5]);
        int halfMoveClock = game.getHalfMoveCounter();
        String currentPlayer = game.getSideToMove().toString();

        String info = String.format("Move: %d | Turn: %s | Half-moves: %d",
                fullMoveNumber, currentPlayer, halfMoveClock);

        if (game.isKingAttacked()) {
            info += " | " + currentPlayer + " IN CHECK!";
        }

        if (isAIMode) {
            info = "Single-player vs AI | " + info;
        } else if (isLocalMode) {
            info = "Local Two Player | " + info;
        } else {
            info = "Two-player online | " + info;
        }
        gameInfoLabel.setText(info);
    }

    private String getPieceTypeKey(Piece piece) {
        if (piece == Piece.NONE) return "";
        String colorPrefix = (piece.getPieceSide() == Side.WHITE) ? "w" : "b";
        String pieceTypeKey;
        switch (piece.getPieceType()) {
            case KING: pieceTypeKey = "K"; break;
            case QUEEN: pieceTypeKey = "Q"; break;
            case ROOK: pieceTypeKey = "R"; break;
            case BISHOP: pieceTypeKey = "B"; break;
            case KNIGHT: pieceTypeKey = "N"; break;
            case PAWN: pieceTypeKey = "P"; break;
            default: pieceTypeKey = ""; break;
        }
        return colorPrefix + pieceTypeKey;
    }

    private void updateBoard() {
        clearHighlights(); // Clear highlights before drawing
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int displayR = isBoardFlipped ? BOARD_SIZE - 1 - r : r;
                int displayC = isBoardFlipped ? BOARD_SIZE - 1 - c : c;
                String sq = ("" + (char)('a' + displayC) + (8 - displayR)).toUpperCase();
                Square square = Square.fromValue(sq);
                Piece piece = game.getPiece(square);
                ChessSquareButton btn = squares[r][c];
                btn.setText("");
                resetButtonColor(btn); // Reset the color to default
                if (piece == Piece.NONE) {
                    btn.setIcon(null);
                } else {
                    String iconKey = getPieceTypeKey(piece);
                    ImageIcon icon = icons.get(iconKey);
                    if (icon != null) {
                        btn.setIcon(icon);
                    } else {
                        btn.setFont(new Font("Serif", Font.BOLD, 48));
                        btn.setText(getUnicodeForPiece(piece));
                    }
                }
            }
        }
        updateLastMoveHighlighting(); // Update last move highlighting with current orientation
        updateCapturedDisplay(); // Update captured pieces display with current orientation
        highlightKingInCheck(); // Recalculate king-in-check highlight after board update
        repaint();
    }

    private String getUnicodeForPiece(Piece piece) {
        String[] unicodePieces = {
                "♔", "♕", "♖", "♗", "♘", "♙",
                "♚", "♛", "♜", "♝", "♞", "♟"
        };
        int index = -1;
        if (piece.getPieceSide() == Side.WHITE) {
            switch (piece.getPieceType()) {
                case KING: index = 0; break;
                case QUEEN: index = 1; break;
                case ROOK: index = 2; break;
                case BISHOP: index = 3; break;
                case KNIGHT: index = 4; break;
                case PAWN: index = 5; break;
            }
        } else {
            switch (piece.getPieceType()) {
                case KING: index = 6; break;
                case QUEEN: index = 7; break;
                case ROOK: index = 8; break;
                case BISHOP: index = 9; break;
                case KNIGHT: index = 10; break;
                case PAWN: index = 11; break;
            }
        }
        return (index >= 0) ? unicodePieces[index] : "";
    }

    private void saveGame() {
        if (!isLocalMode && !isAIMode) {
            JOptionPane.showMessageDialog(this, "Save/Load is only available in Local or AI mode.");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Game");
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(fileToSave)) {
                writer.println(game.getFen());
                JOptionPane.showMessageDialog(this, "Game saved successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving game: " + ex.getMessage());
            }
        }
    }

    private void loadGame() {
        if (!isLocalMode && !isAIMode) {
            JOptionPane.showMessageDialog(this, "Save/Load is only available in Local or AI mode.");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Game");
        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new FileReader(fileToOpen))) {
                String fen = reader.readLine();
                game.loadFromFen(fen);
                updateBoard();
                updateGameInfo();
                JOptionPane.showMessageDialog(this, "Game loaded successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading game: " + ex.getMessage());
            }
        }
    }

    private void showAITutorSuggestion() {
        if (stockfish == null) {
            JOptionPane.showMessageDialog(this, "Stockfish engine not available.", "AI Unavailable", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String fen = game.getFen();
        try {
            String bestMove = stockfish.getBestMove(fen, aiThinkTime);
            // Use aiThinkTime/1000 as depth, but minimum 5
            int depth = Math.max(5, aiThinkTime / 1000);
            int eval = stockfish.getEvaluation(fen, depth);
            String evalString;
            if (eval > 0) {
                evalString = String.format("+%.2f", eval / 100.0);
            } else if (eval < 0) {
                evalString = String.format("%.2f", eval / 100.0);
            } else {
                evalString = "0.00";
            }

            // Show hint overlay directly without dialog
            if (bestMove != null && bestMove.length() >= 4) {
                aiHintFromSquare = bestMove.substring(0, 2).toUpperCase();
                aiHintToSquare = bestMove.substring(2, 4).toUpperCase();
                aiHintLabel = evalString;
                aiHintOverlayPanel.repaint();
                if (aiHintTimer != null) aiHintTimer.stop();
                aiHintTimer = new javax.swing.Timer(5000, e -> clearAiHintOverlay());
                aiHintTimer.setRepeats(false);
                aiHintTimer.start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error getting suggestion: " + e.getMessage(), "AI Tutor Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getPieceNameAtSquare(String squareStr) {
        Square square = Square.fromValue(squareStr.toUpperCase());
        Piece piece = game.getPiece(square);
        if (piece == Piece.NONE) return "Empty";
        switch (piece.getPieceType()) {
            case KING: return "King";
            case QUEEN: return "Queen";
            case ROOK: return "Rook";
            case BISHOP: return "Bishop";
            case KNIGHT: return "Knight";
            case PAWN: return "Pawn";
            default: return "Unknown";
        }
    }

    private void handleDragAndDropMove(String from, String to) {
        if (gameEnded) {
            statusLabel.setText("Game has ended!");
            return;
        }
        if (from.equals(to)) return; // No move

        Side movingSide = isMyTurn ? Side.valueOf(playerColor.toUpperCase()) : (playerColor.equalsIgnoreCase("white") ? Side.BLACK : Side.WHITE);
        if (isAIMode && movingSide != Side.valueOf(playerColor.toUpperCase())) return; // Only allow user to move their color in AI mode
        if (isAIMode && !isMyTurn) return; // Only allow on user's turn

        String moveStr = from + to;
        if (isPawnPromotion(from, to)) {
            String promo = promptForPromotion();
            if (promo != null) moveStr += promo;
        }
        Move move = new Move(moveStr, movingSide);
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(game);
        boolean isLegal = false;
        for (Move legal : legalMoves) {
            if (legal.equals(move)) {
                isLegal = true;
                break;
            }
        }
        if (isLegal) {
            // Store board state before move for capture detection
            Piece[][] beforeBoard = getBoardMatrix(game);
            
            game.doMove(move);
            
            // Update captures after move
            Piece[][] afterBoard = getBoardMatrix(game);
            updateCaptures(beforeBoard, afterBoard);
            
            updateBoard();
            updateGameInfo();
            clearHighlights();
            highlightLastMove(from, to);
            selectedSquare = null;
            dragFromSquare = null;
            dragFromButton = null;
            
            if (isLocalMode) {
                isMyTurn = !isMyTurn;
                statusLabel.setText((isMyTurn ? "White's" : "Black's") + " turn (Local)");
                checkGameState();
                // Flip board after a delay
                Timer flipTimer = new Timer(500, e -> {
                    isBoardFlipped = !isBoardFlipped;
                    updateBoard();
                    ((Timer)e.getSource()).stop();
                });
                flipTimer.setRepeats(false);
                flipTimer.start();
            } else if (isAIMode) {
                isMyTurn = !isMyTurn;
                if (!isMyTurn) {
                    statusLabel.setText("AI is thinking...");
                    SwingUtilities.invokeLater(this::makeAIMove);
                }
                checkGameState();
            } else {
                // Online mode: after making a move, just wait for the server
                isMyTurn = false;
                statusLabel.setText("Waiting for opponent...");
                checkGameState();
                // Ensure check highlight is visible for the player who just moved
                if (game.isKingAttacked()) {
                    highlightKingInCheck();
                }
                // Send move to server
                if (out != null) {
                    try {
                        out.println("MOVE:" + from + to);
                    } catch (Exception e) {
                        statusLabel.setText("Error sending move to server: " + e.getMessage());
                        isMyTurn = true; // Allow retry
                    }
                }
            }
        } else {
            statusLabel.setText("Illegal move! Try again.");
            clearHighlights();
            selectedSquare = null;
        }
    }



    private void updateCapturedDisplay() {
        // Check if panels are initialized
        if (topCapturedPanel == null || bottomCapturedPanel == null) {
            return; // Panels not yet created
        }
        
        // Clear panels
        topCapturedPanel.removeAll();
        bottomCapturedPanel.removeAll();

        // Decide which color is at bottom based on board orientation
        boolean whiteAtBottom = !isBoardFlipped;

        java.util.List<Piece> topList = whiteAtBottom ? whiteCaptured : blackCaptured;
        java.util.List<Piece> bottomList = whiteAtBottom ? blackCaptured : whiteCaptured;

        // Add captured pieces to panels
        for (Piece p : topList) {
            String iconKey = getPieceTypeKey(p);
            ImageIcon icon = icons.get(iconKey);
            if (icon != null) {
                Image img = icon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                topCapturedPanel.add(new JLabel(new ImageIcon(img)));
            } else {
                JLabel label = new JLabel(getUnicodeForPiece(p));
                label.setFont(new Font("Serif", Font.PLAIN, 20));
                topCapturedPanel.add(label);
            }
        }
        
        for (Piece p : bottomList) {
            String iconKey = getPieceTypeKey(p);
            ImageIcon icon = icons.get(iconKey);
            if (icon != null) {
                Image img = icon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                bottomCapturedPanel.add(new JLabel(new ImageIcon(img)));
            } else {
                JLabel label = new JLabel(getUnicodeForPiece(p));
                label.setFont(new Font("Serif", Font.PLAIN, 20));
                bottomCapturedPanel.add(label);
            }
        }
        
        topCapturedPanel.revalidate();
        topCapturedPanel.repaint();
        bottomCapturedPanel.revalidate();
        bottomCapturedPanel.repaint();
    }

    private int getPieceValue(Piece piece) {
        switch (piece.getPieceType()) {
            case PAWN: return 1;
            case KNIGHT: return 3;
            case BISHOP: return 3;
            case ROOK: return 5;
            case QUEEN: return 9;
            default: return 0;
        }
    }

    private Piece[][] getBoardMatrix(Board board) {
        Piece[][] matrix = new Piece[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String sq = ("" + (char)('a' + c) + (8 - r)).toUpperCase();
                matrix[r][c] = board.getPiece(Square.fromValue(sq));
            }
        }
        return matrix;
    }

    private void updateCaptures(Piece[][] before, Piece[][] after) {
        // Count pieces by type and color before and after
        java.util.Map<Piece, Integer> beforeCount = new java.util.HashMap<>();
        java.util.Map<Piece, Integer> afterCount = new java.util.HashMap<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                Piece b = before[r][c];
                Piece a = after[r][c];
                if (b != Piece.NONE) beforeCount.put(b, beforeCount.getOrDefault(b, 0) + 1);
                if (a != Piece.NONE) afterCount.put(a, afterCount.getOrDefault(a, 0) + 1);
            }
        }
        // For each piece type/color, if count decreased, it was captured
        for (Piece p : beforeCount.keySet()) {
            int beforeNum = beforeCount.getOrDefault(p, 0);
            int afterNum = afterCount.getOrDefault(p, 0);
            int diff = beforeNum - afterNum;
            for (int i = 0; i < diff; i++) {
                if (p.getPieceSide() == Side.WHITE) {
                    whiteCaptured.add(p);
                    blackScore += getPieceValue(p);
                } else if (p.getPieceSide() == Side.BLACK) {
                    blackCaptured.add(p);
                    whiteScore += getPieceValue(p);
                }
            }
        }
        updateCapturedDisplay();
    }

    private void updateSquarePositions(int squareSize) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (squares[r][c] != null) {
                    squares[r][c].setBounds(c * squareSize, r * squareSize, squareSize, squareSize);
                }
            }
        }
    }

    private void highlightBestMove(String bestMove) {
        // Clear previous highlights
        if (bestMoveFromHighlight != null) {
            bestMoveFromHighlight.setShowBorder(false);
            resetButtonColor(bestMoveFromHighlight);
        }
        if (bestMoveToHighlight != null) {
            bestMoveToHighlight.setShowBorder(false);
            resetButtonColor(bestMoveToHighlight);
        }
        bestMoveFromHighlight = null;
        bestMoveToHighlight = null;
        
        if (bestMove != null && bestMove.length() >= 4) {
            String fromSq = bestMove.substring(0, 2).toUpperCase();
            String toSq = bestMove.substring(2, 4).toUpperCase();
            
            // Convert chess notation to board coordinates (always from White's perspective)
            int fromFile = fromSq.charAt(0) - 'A';
            int fromRank = 8 - Character.getNumericValue(fromSq.charAt(1));
            int toFile = toSq.charAt(0) - 'A';
            int toRank = 8 - Character.getNumericValue(toSq.charAt(1));
            
            // Convert to display coordinates based on board orientation
            int fromDisplayRow, fromDisplayCol, toDisplayRow, toDisplayCol;
            if (isBoardFlipped) {
                fromDisplayRow = BOARD_SIZE - 1 - fromRank;
                fromDisplayCol = BOARD_SIZE - 1 - fromFile;
                toDisplayRow = BOARD_SIZE - 1 - toRank;
                toDisplayCol = BOARD_SIZE - 1 - toFile;
            } else {
                fromDisplayRow = fromRank;
                fromDisplayCol = fromFile;
                toDisplayRow = toRank;
                toDisplayCol = toFile;
            }
            
            // Highlight the squares with borders
            bestMoveFromHighlight = squares[fromDisplayRow][fromDisplayCol];
            bestMoveToHighlight = squares[toDisplayRow][toDisplayCol];
            bestMoveFromHighlight.setShowBorder(true);
            bestMoveFromHighlight.setBorderColor(bestMoveFromColor);
            bestMoveToHighlight.setShowBorder(true);
            bestMoveToHighlight.setBorderColor(bestMoveToColor);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set system look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(ChessClient::new);
    }

    private JLabel createStyledClockLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 20));
        label.setOpaque(true);
        label.setBackground(new Color(245, 245, 245));
        label.setForeground(Color.BLACK);
        label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(new Color(180,180,180), 2, true),
            javax.swing.BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        label.setFocusable(false);
        label.setVisible(true);
        return label;
    }

    private void drawHintArrow(Graphics2D g2d, String from, String to, String label) {
        // Convert board squares to pixel positions
        int[] fromXY = getSquareCenter(from);
        int[] toXY = getSquareCenter(to);
        if (fromXY == null || toXY == null) return;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Determine color based on evaluation value
        boolean isNegative = label != null && label.startsWith("-");
        boolean isZero = label != null && label.equals("0.00");
        Color arrowColor, circleColor, borderColor;
        
        if (isNegative) {
            // Crimson colors for negative evaluation
            arrowColor = new Color(220, 20, 60, 180); // Crimson, semi-transparent
            circleColor = new Color(220, 20, 60, 220); // Crimson
            borderColor = new Color(178, 34, 34); // Darker crimson for border
        } else if (isZero) {
            // Teal blue colors for zero evaluation
            arrowColor = new Color(0, 128, 128, 180); // Teal, semi-transparent
            circleColor = new Color(0, 128, 128, 220); // Teal
            borderColor = new Color(0, 105, 105); // Darker teal for border
        } else {
            // Emerald green colors for positive evaluation
            arrowColor = new Color(46, 204, 113, 180); // Emerald green, semi-transparent
            circleColor = new Color(46, 204, 113, 220); // Emerald green
            borderColor = new Color(39, 174, 96); // Darker emerald green for border
        }
        
        // Draw arrow line
        g2d.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(arrowColor);
        g2d.drawLine(fromXY[0], fromXY[1], toXY[0], toXY[1]);
        
        // Draw arrowhead circle
        int r = 32; // Bigger circle
        g2d.setColor(circleColor);
        g2d.fillOval(toXY[0] - r/2, toXY[1] - r/2, r, r);
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(toXY[0] - r/2, toXY[1] - r/2, r, r);
        
        // Draw label (score/percentage)
        if (label != null) {
            g2d.setFont(new Font("Arial", Font.BOLD, 12)); // Smaller font
            FontMetrics fm = g2d.getFontMetrics();
            int w = fm.stringWidth(label);
            int h = fm.getAscent();
            g2d.setColor(Color.WHITE); // White text for better contrast
            g2d.drawString(label, toXY[0] - w/2, toXY[1] + h/2 - 2);
        }
    }

    private int[] getSquareCenter(String square) {
        try {
            int file = square.charAt(0) - 'A';
            int rank = 8 - Character.getNumericValue(square.charAt(1));
            int displayRow = isBoardFlipped ? BOARD_SIZE - 1 - rank : rank;
            int displayCol = isBoardFlipped ? BOARD_SIZE - 1 - file : file;
            int squareSize = squares[0][0].getWidth();
            int x = displayCol * squareSize + squareSize/2;
            int y = displayRow * squareSize + squareSize/2;
            return new int[]{x, y};
        } catch (Exception e) { return null; }
    }

    private void clearAiHintOverlay() {
        aiHintFromSquare = null;
        aiHintToSquare = null;
        aiHintLabel = null;
        if (aiHintTimer != null) aiHintTimer.stop();
        aiHintOverlayPanel.repaint();
    }

    // Show best move hint using Stockfish and overlay arrow
    private void showBestMoveHint() {
        if (stockfish == null) {
            JOptionPane.showMessageDialog(this, "Stockfish engine not available.", "AI Unavailable", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String fen = game.getFen();
        try {
            String bestMove = stockfish.getBestMove(fen, aiThinkTime);
            if (bestMove != null && bestMove.length() >= 4) {
                aiHintFromSquare = bestMove.substring(0, 2).toUpperCase();
                aiHintToSquare = bestMove.substring(2, 4).toUpperCase();
                aiHintLabel = "Best move";
                aiHintOverlayPanel.repaint();
                if (aiHintTimer != null) aiHintTimer.stop();
                aiHintTimer = new javax.swing.Timer(4000, e -> clearAiHintOverlay());
                aiHintTimer.setRepeats(false);
                aiHintTimer.start();
            } else {
                JOptionPane.showMessageDialog(this, "No best move found.", "Hint", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error getting best move: " + e.getMessage(), "Hint Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
