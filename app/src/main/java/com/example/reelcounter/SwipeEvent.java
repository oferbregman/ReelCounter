package com.example.reelcounter;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "swipes")
public class SwipeEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;

    // Timestamp of the scroll event
    public long timestamp;

    // Delta Y value (positive = scrolling down in Reels)
    public int dy;

    // Scroll session ID to group continuous scrolls
    public long scrollSessionId;

    public SwipeEvent(long timestamp, int dy, long scrollSessionId) {
        this.timestamp = timestamp;
        this.dy = dy;
        this.scrollSessionId = scrollSessionId;
    }

    // Empty constructor for Room
    public SwipeEvent() {
    }
}