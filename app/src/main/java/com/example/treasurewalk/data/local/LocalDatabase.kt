package com.example.treasurewalk.data.local


import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TreasureRarity {
    COMMON, RARE, LEGENDARY
}

// 1. Definizione dell'Entità (La tabella nel database)
@Entity(tableName = "treasures")
data class TreasureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: TreasureRarity,          // COMMON, RARE, LEGENDARY
    val lat: Double,           // Latitudine del ritrovamento
    val lng: Double,           // Longitudine del ritrovamento
    val xpAwarded: Int,        // Punti XP guadagnati
    val timestamp: Long = System.currentTimeMillis(),
    val itemName: String? = null // Nome dell'oggetto avatar sbloccato (se presente)
)

// 2. Il DAO (L'interfaccia per interrogare il DB)
@Dao
interface TreasureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreasure(treasure: TreasureEntity)

    @Query("SELECT * FROM treasures ORDER BY timestamp DESC")
    fun getAllCollectedTreasures(): Flow<List<TreasureEntity>>

    @Query("SELECT SUM(xpAwarded) FROM treasures")
    fun getTotalXp(): Flow<Int?>
}

// 3. La Classe Database
@Database(entities = [TreasureEntity::class], version = 1, exportSchema = false)
abstract class WalkDatabase : RoomDatabase() {
    abstract fun treasureDao(): TreasureDao

    companion object {
        @Volatile
        private var INSTANCE: WalkDatabase? = null

        fun getDatabase(context: android.content.Context): WalkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalkDatabase::class.java,
                    "walk_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}