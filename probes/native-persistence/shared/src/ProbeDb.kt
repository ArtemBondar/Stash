package org.example.stashprobe

import androidx.room3.*

@Entity
data class ProbeItem(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(defaultValue = "") val note: String = "",
)

@Entity
data class ProbeOutbox(@PrimaryKey val id: String, val itemId: String)

@Dao
abstract class ProbeDao {
    @Insert abstract suspend fun insert(item: ProbeItem)
    @Insert abstract suspend fun insertOutbox(outbox: ProbeOutbox)

    @Query("SELECT * FROM ProbeItem WHERE instr(text, :query) > 0 ORDER BY id")
    abstract suspend fun search(query: String): List<ProbeItem>

    @Query("SELECT * FROM ProbeItem WHERE id = :id")
    abstract suspend fun find(id: String): ProbeItem?

    @Query("SELECT * FROM ProbeOutbox WHERE id = :id")
    abstract suspend fun findOutbox(id: String): ProbeOutbox?

    @Transaction
    open suspend fun capture(id: String, text: String, fail: Boolean = false) {
        insert(ProbeItem(id, text))
        if (fail) error("injected")
        insertOutbox(ProbeOutbox(id, id))
    }
}

@Database(entities = [ProbeItem::class, ProbeOutbox::class], version = 2)
@ConstructedBy(ProbeDbConstructor::class)
abstract class ProbeDb : RoomDatabase() {
    abstract fun dao(): ProbeDao
}

@Suppress("KotlinNoActualForExpect")
expect object ProbeDbConstructor : RoomDatabaseConstructor<ProbeDb> {
    override fun initialize(): ProbeDb
}
