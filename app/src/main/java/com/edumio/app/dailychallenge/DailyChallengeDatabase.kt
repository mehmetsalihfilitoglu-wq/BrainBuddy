package com.edumio.app.dailychallenge

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Isolated per-user Daily Challenge state store. Deliberately a SEPARATE database from edumio.db so
 * the content DB's schema/version is never touched (no migration risk). Content questions still live in
 * edumio.db and are read via QuestionDao; only user progress lives here.
 */
@Database(
    entities = [
        DailyChallengeEntity::class,
        ChallengeAnswerEntity::class,
        UserQuestionStateEntity::class,
        SectionDeficitEntity::class,
        StreakEntity::class,
    ],
    // v2: daily_challenge re-keyed from (userId, examType, localDate) → (userId, localDate); added
    //     challengeId / examProfile / currentIndex / createdAt. Pre-launch app → destructive rebuild
    //     (no production users to migrate; consistent with the app-wide fresh-install decision).
    version = 2,
    // Schema exported to app/schemas/ and committed (frozen v2 baseline). Pre-launch this DB still uses
    // destructive fallback (no production users); once real users exist, add additive 2→3 migrations.
    exportSchema = true,
)
abstract class DailyChallengeDatabase : RoomDatabase() {
    abstract fun dailyChallengeDao(): DailyChallengeDao

    companion object {
        @Volatile
        private var instance: DailyChallengeDatabase? = null

        fun get(context: Context): DailyChallengeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailyChallengeDatabase::class.java,
                    "daily_challenge.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
