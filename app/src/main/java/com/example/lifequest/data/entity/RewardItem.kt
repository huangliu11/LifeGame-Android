package com.example.lifequest.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 奖励物品实体类
 */
@Entity(tableName = "rewards")
data class RewardItem(
    @PrimaryKey  // ✅ 移除 autoGenerate
    val id: String,  // ✅ String 类型
    val name: String,
    val description: String,
    val coinCost: Int,
    val isPurchased: Boolean = false,
    val category: String = "娱乐",
    val icon: String = "🎁",
    val purchaseCount: Int = 0,
    val lastPurchaseTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
