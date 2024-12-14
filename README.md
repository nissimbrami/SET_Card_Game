# Set Card Game

A Java implementation of the Set card game with concurrent programming and synchronization mechanisms. This project implements a modified version of the classic Set card game, featuring both human and AI players, real-time gameplay, and a graphical user interface.

## Game Overview

Set is a real-time card game where players compete to identify valid "sets" of three cards from a 3x4 grid of cards displayed on the table. Each card has four features (color, number, shape, shading), and a valid set consists of three cards where each feature is either all the same or all different across the cards.

### Features

- Multi-threaded implementation with Thread-Per-Client architecture
- Support for both human and AI players
- Real-time gameplay with concurrent player actions
- GUI display of game state and player scores
- Configurable game parameters
- Synchronized player actions and game state management

## Technical Implementation

### Core Components

- **Table**: Manages the game board state and token placement
- **Dealer**: Controls game flow, card distribution, and set validation
- **Player**: Handles player actions and token management
- **User Interface**: Provides graphical display of game state

### Concurrency Features

- Thread-safe game state management
- Synchronized access to shared resources
- Fair dealer service using FIFO ordering
- Configurable timing mechanisms for player actions

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Maven
- Git

### Installation

1. Clone the repository:
```bash
git clone https://github.com/nissimbrami/Set-Card-Game.git
cd Set-Card-Game
```

2. Build the project:
```bash
mvn clean install
```

### Running the Game

1. Start the game:
```bash
mvn exec:java
```

### Default Player Controls

The game supports keyboard input for human players:

Player 1 Keys:
```
Q W E R
A S D F
Z X C V
```

Player 2 Keys:
```
U I O P
J K L ;
M , . /
```

## Configuration

Game settings can be modified through the `config.properties` file, including:

- Number of human/computer players
- Turn timeout duration
- Penalty and point freeze durations
- Table update delay

## Technical Details

### Threading Model

- One thread per player
- Additional AI simulation threads for computer players
- Dealer thread for game management
- Synchronized token placement and set checking

### Synchronization Mechanisms

- Thread-safe data structures for game state
- Synchronized methods for critical sections
- Wait/notify mechanisms for player coordination
- Fair dealer service implementation

## Project Structure

```
src/
├── main/
│   └── java/
│       └── bguspl/
│           └── set/
│               ├── ex/
│               │   ├── Dealer.java
│               │   ├── Player.java
│               │   └── Table.java
│               └── ...
└── test/
    └── java/
        └── bguspl/
            └── set/
                └── ex/
                    └── ...
```

## Author

Nissim Brami

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
