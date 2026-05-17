package com.example.reelcounter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SwipeDao {

    @Insert
    void insert(SwipeEvent event);

    // Count distinct scroll sessions since a timestamp (existing)
    @Query("SELECT COUNT(DISTINCT scrollSessionId) FROM swipes WHERE timestamp >= :since AND dy >= 0")
    int countSessionsSince(long since);

    // Count distinct scroll sessions within an exact window [from, to)
    @Query("SELECT COUNT(DISTINCT scrollSessionId) FROM swipes WHERE timestamp >= :from AND timestamp < :to AND dy >= 0")
    int countSessionsBetween(long from, long to);

    // Get daily counts for weekly view (last 7 days)
    @Query("SELECT " +
            "DATE(timestamp / 1000, 'unixepoch') as date, " +
            "COUNT(DISTINCT scrollSessionId) as count " +
            "FROM swipes " +
            "WHERE dy >= 0 AND timestamp >= :since " +
            "GROUP BY DATE(timestamp / 1000, 'unixepoch') " +
            "ORDER BY date DESC")
    List<DailyCount> getDailyCountsSince(long since);

    // Get all swipe events for a specific day
    @Query("SELECT * FROM swipes WHERE DATE(timestamp / 1000, 'unixepoch') = :date ORDER BY timestamp DESC")
    List<SwipeEvent> getEventsByDate(String date);

    // Count total distinct scroll sessions ever (lifetime)
    @Query("SELECT COUNT(DISTINCT scrollSessionId) FROM swipes WHERE dy >= 0")
    int countAllSessions();

    // Get total Instagram usage time in seconds
    @Query("SELECT (MAX(timestamp) - MIN(timestamp)) / 1000 FROM swipes")
    long getTotalUsageSeconds();

    // Delete old records
    @Query("DELETE FROM swipes WHERE timestamp < :beforeTimestamp")
    void deleteOlderThan(long beforeTimestamp);

    // Get usage time for a period since a timestamp (existing)
    @Query("SELECT (MAX(timestamp) - MIN(timestamp)) FROM swipes WHERE timestamp >= :since AND dy >= 0")
    Long getUsageMillisSince(long since);

    // Get usage time within an exact window [from, to)
    @Query("SELECT (MAX(timestamp) - MIN(timestamp)) FROM swipes WHERE timestamp >= :from AND timestamp < :to AND dy >= 0")
    Long getUsageMillisBetween(long from, long to);

    // Delete everything
    @Query("DELETE FROM swipes")
    void deleteAll();

    class DailyCount {
        public String date;
        public int count;
    }
}