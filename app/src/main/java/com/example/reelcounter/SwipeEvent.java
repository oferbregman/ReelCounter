package com.example.reelcounter;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "swipes")
public class SwipeEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    public SwipeEvent(long timestamp) {
        this.timestamp = timestamp;
    }
}
