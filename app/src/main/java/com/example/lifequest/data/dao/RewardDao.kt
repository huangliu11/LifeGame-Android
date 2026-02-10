package com.example.lifequest.data.dao

import androidx.room.*
import com.example.lifequest.data.entity.RewardItem
import kotlinx.coroutines.flow.Flow

/**
 * 奖励数据访问对象
 */
@Dao
interface RewardDao {

    /**
     * 获取所有奖励
     */
    @Query("SELECT * FROM rewards ORDER BY coinCost ASC")
    fun getAllRewards(): Flow<List<RewardItem>>

    /**
     * 获取未购买的奖励
     */
    @Query("SELECT * FROM rewards WHERE isPurchased = 0 ORDER BY coinCost ASC")
    fun getAvailableRewards(): Flow<List<RewardItem>>

    /**
     * 获取已购买的奖励
     */
    @Query("SELECT * FROM rewards WHERE isPurchased = 1 ORDER BY lastPurchaseTime DESC")
    fun getPurchasedRewards(): Flow<List<RewardItem>>

    /**
     * 根据分类获取奖励
     */
    @Query("SELECT * FROM rewards WHERE category = :category ORDER BY coinCost ASC")
    fun getRewardsByCategory(category: String): Flow<List<RewardItem>>

    /**
     * 根据 ID 获取奖励
     */
    @Query("SELECT * FROM rewards WHERE id = :rewardId")
    suspend fun getRewardById(rewardId: String): RewardItem?

    /**
     * 插入奖励
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: RewardItem)

    /**
     * 插入多个奖励
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewards(rewards: List<RewardItem>)

    /**
     * 更新奖励
     */
    @Update
    suspend fun updateReward(reward: RewardItem)

    /**
     * 删除奖励
     */
    @Delete
    suspend fun deleteReward(reward: RewardItem)

    /**
     * 根据 ID 删除奖励
     */
    @Query("DELETE FROM rewards WHERE id = :rewardId")
    suspend fun deleteRewardById(rewardId: String)

    /**
     * 删除所有奖励
     */
    @Query("DELETE FROM rewards")
    suspend fun deleteAllRewards()

    /**
     * 获取奖励总数
     */
    @Query("SELECT COUNT(*) FROM rewards")
    suspend fun getRewardCount(): Int
}
