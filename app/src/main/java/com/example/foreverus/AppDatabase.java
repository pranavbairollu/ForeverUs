package com.example.foreverus;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = { SpecialDate.class, Story.class, Memory.class, Letter.class, Song.class,
        BucketItem.class, SpinnerCategory.class, SpinnerItem.class,
        Milestone.class, PolaroidEntity.class }, version = 15, exportSchema = false)
@TypeConverters({ Converters.class })
public abstract class AppDatabase extends RoomDatabase {

    public abstract SpecialDateDao specialDateDao();

    public abstract StoryDao storyDao();

    public abstract MemoryDao memoryDao();

    public abstract LetterDao letterDao();

    public abstract SongDao songDao();

    public abstract BucketDao bucketDao();

    public abstract SpinnerDao spinnerDao();

    public abstract MilestoneDao milestoneDao();

    public abstract PolaroidDao polaroidDao();

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "forever_us_database";

    static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_11_12, MIGRATION_12_13,
                                    MIGRATION_13_14, MIGRATION_14_15)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE letters ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE songs ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add mediaUrls column for multi-image support. Stored as JSON string (TEXT).
            database.execSQL("ALTER TABLE memories ADD COLUMN mediaUrls TEXT");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add audioUrl column for Voice Notes.
            database.execSQL("ALTER TABLE memories ADD COLUMN audioUrl TEXT");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE letters ADD COLUMN audioUrl TEXT");
            database.execSQL("ALTER TABLE letters ADD COLUMN mediaUrl TEXT");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `bucket_items` (" +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT, " +
                    "`description` TEXT, " +
                    "`type` TEXT, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`imageUrl` TEXT, " +
                    "`relationshipId` TEXT, " +
                    "`timestamp` INTEGER, " +
                    "`completedDate` INTEGER, " +
                    "`syncStatus` TEXT, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE letters ADD COLUMN reaction TEXT");
            database.execSQL("ALTER TABLE letters ADD COLUMN replyContent TEXT");
            database.execSQL("ALTER TABLE letters ADD COLUMN replyTimestamp INTEGER");
            database.execSQL("ALTER TABLE letters ADD COLUMN readTimestamp INTEGER");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `spinner_categories` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT, " +
                    "`isDefault` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS `spinner_items` (" +
                    "`id` TEXT NOT NULL, " +
                    "`categoryId` TEXT, " +
                    "`text` TEXT, " +
                    "`emoji` TEXT, " +
                    "`orderIndex` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE memories ADD COLUMN spinnerCategoryId TEXT");
            database.execSQL("ALTER TABLE memories ADD COLUMN spinnerItemId TEXT");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `milestones` (" +
                    "`id` TEXT NOT NULL, " +
                    "`relationshipId` TEXT, " +
                    "`title` TEXT, " +
                    "`category` TEXT, " +
                    "`completedDate` INTEGER, " +
                    "`cost` REAL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `polaroids` (" +
                    "`id` TEXT NOT NULL, " +
                    "`imagePath` TEXT, " +
                    "`caption` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`linkedMemoryId` TEXT, " +
                    "`linkedAdventureId` TEXT, " +
                    "PRIMARY KEY(`id`))");
        }
    };
}
