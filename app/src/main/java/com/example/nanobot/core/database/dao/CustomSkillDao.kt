package com.example.nanobot.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nanobot.core.database.entity.CustomSkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSkillDao {
    @Query("SELECT * FROM custom_skills ORDER BY title COLLATE NOCASE")
    fun observeSkills(): Flow<List<CustomSkillEntity>>

    @Query("SELECT * FROM custom_skills ORDER BY title COLLATE NOCASE")
    suspend fun getSkills(): List<CustomSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skills: List<CustomSkillEntity>)

    @Query("DELETE FROM custom_skills WHERE id = :skillId")
    suspend fun deleteById(skillId: String)

    @Query("DELETE FROM custom_skills WHERE sourceTreeUri = :treeUri")
    suspend fun deleteByTreeUri(treeUri: String)
}
