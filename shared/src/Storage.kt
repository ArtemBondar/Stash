package org.example.stash

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity
data class CaptureRow(@PrimaryKey val id: String, val text: String, val kind: CaptureKind, val createdAt: Long, val profile: String) {
    fun toCapture() = Capture(id, text, kind, createdAt, profile)
}
@Entity
data class OutboxEntry(@PrimaryKey val id: String, val itemId: String, val profile: String)

@Dao
abstract class CaptureDao {
    @Query("SELECT * FROM CaptureRow WHERE profile = :profile ORDER BY createdAt DESC, id DESC")
    abstract suspend fun rows(profile: String = LOCAL_PROFILE): List<CaptureRow>
    @Query("SELECT * FROM CaptureRow WHERE id = :id")
    abstract suspend fun find(id: String): CaptureRow?
    @Query("SELECT COUNT(*) FROM OutboxEntry")
    abstract suspend fun outboxCount(): Int
    @Insert abstract suspend fun insert(item: CaptureRow)
    @Insert abstract suspend fun insertOutbox(entry: OutboxEntry)

    @Transaction
    open suspend fun capture(item: CaptureRow) {
        val existing = find(item.id)
        if (existing != null) {
            if (existing != item) throw CaptureConflict()
            return
        }
        insert(item)
        insertOutbox(OutboxEntry(item.id, item.id, item.profile))
    }
}

class RoomCaptureStore(private val database: StashDatabase) : CaptureStore {
    override suspend fun items(): List<Capture> = withContext(Dispatchers.Default) { database.dao().rows().map { it.toCapture() } }
    override suspend fun capture(item: Capture) = withContext(Dispatchers.Default) {
        database.dao().capture(CaptureRow(item.id, item.text, item.kind, item.createdAt, item.profile))
    }
}

@Database(entities = [CaptureRow::class, OutboxEntry::class], version = 1)
@ConstructedBy(StashDatabaseConstructor::class)
abstract class StashDatabase : RoomDatabase() { abstract fun dao(): CaptureDao }

@Suppress("KotlinNoActualForExpect")
expect object StashDatabaseConstructor : RoomDatabaseConstructor<StashDatabase> { override fun initialize(): StashDatabase }
