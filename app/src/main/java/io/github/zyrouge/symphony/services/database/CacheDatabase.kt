package io.github.zyrouge.symphony.services.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.store.SongCacheStore
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.RoomConvertors

@Database(
    entities = [Song::class],
    version = 4,
    autoMigrations = [AutoMigration(1, 2, CacheDatabase.Migration1To2::class)]
)
@TypeConverters(RoomConvertors::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun songs(): SongCacheStore

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGain REAL")
            }
        }

        // Adds per-song lyrics sync offset. NOT NULL DEFAULT 0 so SQLite backfills
        // existing rows automatically — no data loss, no explicit UPDATE required.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN lyricsOffset INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun create(symphony: Symphony) = Room
            .databaseBuilder(
                symphony.applicationContext,
                CacheDatabase::class.java,
                "cache"
            )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @DeleteColumn("songs", "minBitrate")
    @DeleteColumn("songs", "maxBitrate")
    @DeleteColumn("songs", "bitsPerSample")
    @DeleteColumn("songs", "samples")
    @DeleteColumn("songs", "codec")
    class Migration1To2 : AutoMigrationSpec
}