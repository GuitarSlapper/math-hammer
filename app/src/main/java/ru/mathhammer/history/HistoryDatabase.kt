package ru.mathhammer.history

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.mathhammer.DamageInput

const val CALCULATION_VERSION = 1

@Entity(tableName = "saved_calculations", indices = [Index("updatedAtEpochMs")])
data class SavedCalculation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val attacks: Int,
    val hitTarget: Int,
    val strength: Int,
    val armorPenetration: Int,
    val damage: Int,
    val toughness: Int,
    val saveTarget: Int,
    val invulnerableSaveTarget: Int?,
    val averageDamagePerAttack: Double,
    val averageTotalDamage: Double,
    val calculationVersion: Int = CALCULATION_VERSION,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun input() = DamageInput(attacks, hitTarget, strength, armorPenetration, damage,
        toughness, saveTarget, invulnerableSaveTarget)
}

@Dao
interface SavedCalculationDao {
    @Query("SELECT * FROM saved_calculations ORDER BY updatedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<SavedCalculation>>

    @Query("SELECT * FROM saved_calculations WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SavedCalculation?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: SavedCalculation): Long

    @Update
    suspend fun update(value: SavedCalculation): Int

    @Query("DELETE FROM saved_calculations WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Database(entities = [SavedCalculation::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun calculations(): SavedCalculationDao

    companion object {
        fun open(context: Context, name: String = "mathhammer-history.db") =
            Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, name).build()
        // Схема v1 — первая БД приложения. Будущие версии добавляют явные Migration;
        // destructive migration намеренно отсутствует: история не должна исчезать.
    }
}

interface HistoryStore {
    fun observeAll(): Flow<List<SavedCalculation>>
    suspend fun findById(id: Long): SavedCalculation?
    suspend fun insert(value: SavedCalculation): Long
    suspend fun update(value: SavedCalculation): Int
    suspend fun delete(id: Long): Int
    suspend fun <T> transaction(block: suspend () -> T): T
}

class RoomHistoryStore(private val database: HistoryDatabase) : HistoryStore {
    private val dao get() = database.calculations()
    override fun observeAll() = dao.observeAll()
    override suspend fun findById(id: Long) = dao.findById(id)
    override suspend fun insert(value: SavedCalculation) = dao.insert(value)
    override suspend fun update(value: SavedCalculation) = dao.update(value)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
}
