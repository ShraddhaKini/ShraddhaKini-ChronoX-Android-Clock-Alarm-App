package com.example.chronox.chess;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chess_games")
public class ChessGame {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public long startTimeMs;
    public long endTimeMs;

    public int p1Moves;
    public int p2Moves;

    public long timeControlMs;
    public long incrementMs;

    public String p1Name;
    public String p2Name;

    public String result; // "Player 1 Wins", "Player 2 Wins", "Draw"
    
    // Store simple serialized history: "P1: 15:42:03, P2: 15:42:10" etc.
    public String historyJson;
}
