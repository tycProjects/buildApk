package com.ryan.vietsubai.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceUri: String,
    val outputUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ready"
)

@Entity(tableName = "translation_memory", indices = [Index(value = ["cacheKey"], unique = true)])
data class TranslationMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cacheKey: String,
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val provider: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "render_jobs")
data class RenderJobEntity(
    @PrimaryKey val id: String,
    val projectName: String,
    val progress: Int = 0,
    val stage: String = "queued",
    val status: String = "queued",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_jobs")
data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val progress: Int = 0,
    val status: String = "queued",
    val downloadId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Insert suspend fun insert(project: ProjectEntity): Long
    @Update suspend fun update(project: ProjectEntity)
    @Delete suspend fun delete(project: ProjectEntity)
}

@Dao
interface TranslationMemoryDao {
    @Query("SELECT * FROM translation_memory WHERE cacheKey = :key LIMIT 1") suspend fun get(key: String): TranslationMemoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: TranslationMemoryEntity)
    @Query("DELETE FROM translation_memory") suspend fun clear()
}

@Dao
interface RenderJobDao {
    @Query("SELECT * FROM render_jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<RenderJobEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(job: RenderJobEntity)
    @Query("UPDATE render_jobs SET status='cancelled', stage='cancelled' WHERE status IN ('queued','running')") suspend fun markQueuedCancelled()
}

@Dao
interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadJobEntity>>
    @Query("SELECT * FROM download_jobs WHERE id = :id LIMIT 1") suspend fun get(id: String): DownloadJobEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(job: DownloadJobEntity)
}

@Database(entities = [ProjectEntity::class, TranslationMemoryEntity::class, RenderJobEntity::class, DownloadJobEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun translationMemoryDao(): TranslationMemoryDao
    abstract fun renderJobDao(): RenderJobDao
    abstract fun downloadJobDao(): DownloadJobDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vietsub_ai.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
