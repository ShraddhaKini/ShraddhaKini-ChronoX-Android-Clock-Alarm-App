package com.example.chronox.chess;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChessDao {
    @Insert
    long insert(ChessGame game);

    @Query("SELECT * FROM chess_games ORDER BY endTimeMs DESC")
    LiveData<List<ChessGame>> getAllGames();
}
