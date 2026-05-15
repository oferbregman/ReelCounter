package com.example.reelcounter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface SwipeDao {
    @Insert
    void insert(SwipeEvent event);

    @Query("SELECT COUNT(*) FROM swipes WHERE timestamp >= :since")
    int countSince(long since);
}
