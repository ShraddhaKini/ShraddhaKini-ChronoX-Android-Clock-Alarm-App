package com.example.chronox.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.chronox.alarm.Alarm;
import com.example.chronox.alarm.AlarmDao;
import com.example.chronox.chess.ChessDao;
import com.example.chronox.chess.ChessGame;
import com.example.chronox.common.Constants;
import com.example.chronox.worldclock.WorldClock;
import com.example.chronox.worldclock.WorldClockDao;

@Database(
        entities = {Alarm.class, WorldClock.class, ChessGame.class},
        version = Constants.DATABASE_VERSION,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract AlarmDao alarmDao();
    public abstract WorldClockDao worldClockDao();
    public abstract ChessDao chessDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS chess_games (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startTimeMs INTEGER NOT NULL, endTimeMs INTEGER NOT NULL, p1Moves INTEGER NOT NULL, p2Moves INTEGER NOT NULL, timeControlMs INTEGER NOT NULL, incrementMs INTEGER NOT NULL, p1Name TEXT, p2Name TEXT, result TEXT, historyJson TEXT)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN scheduledDateMillis INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    Constants.DATABASE_NAME
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
