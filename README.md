Chess Game Application
A comprehensive Java-based chess application built with Swing GUI framework featuring multiple gameplay modes and advanced chess analysis capabilities.

🧠 AI Integration & Analysis

*Stockfish Engine Integration
*Professional Chess Engine: Full integration with Stockfish for advanced AI gameplay
*Winner Predictor: Real-time position evaluation with numerical scoring (+/- values)
*AI Tutor System: Intelligent move suggestions with visual arrow overlays and position analysis
*4 Difficulty Levels: Easy (500ms), Medium (1000ms), Hard (2000ms), Expert (3000ms) thinking time

Advanced Analysis Features

*Position Evaluation: Real-time scoring showing advantage/disadvantage
*Best Move Detection: AI identifies optimal moves with visual feedback
*Tactical Analysis: AI tutor provides strategic insights and move explanations
*Game State Prediction: Advanced algorithms predict game outcomes and winning chances


Game Modes
*Local Two Player: Face-to-face gameplay with automatic board flipping
*Online Multiplayer: Real-time TCP socket-based multiplayer with chat functionality
*AI vs Human: Single-player mode with Stockfish chess engine integration

*Real-time drag-and-drop piece movement with visual feedback
*Legal move highlighting with color-coded dots (brown for moves, red for captures)
*Last move tracking with border highlighting
*Dynamic board resizing and responsive layout
*Captured pieces display with score tracking
*Check detection with king highlighting

*Save/Load functionality using FEN notation
*Comprehensive chess rule validation
*Checkmate, stalemate, and draw detection
*Move history and game state tracking
*Networking
*Client-server architecture for online multiplayer
*Real-time chat functionality
*Game state synchronization
*Automatic connection handling

Backend: Java, Stockfish Chess Engine
Frontend: Java Swing, Custom UI Components
Libraries: ChessLib (chess logic), Apache Commons Lang
Networking: TCP Socket Programming
Architecture: MVC Pattern, Event-Driven Programming

Ensure Java 8+ is installed
Download Stockfish engine and place in project root
Compile and run ChessClient.java
Select game mode and start playing!


file structure 

├── src/
│   ├── client/ChessClient.java      # Main GUI application
│   ├── server/ChessServer.java      # Multiplayer server
│   ├── engine/StockfishEngine.java  # AI engine integration
│   └── com/github/bhlangonijr/chesslib/  # Chess logic library
├── images/                          # Chess piece graphics
├── lib/                             # External dependencies
└── stockfishfinal                   # Stockfish engine binary



Perfect for chess enthusiasts, Java developers learning GUI programming, or anyone interested in game development with AI integration!
