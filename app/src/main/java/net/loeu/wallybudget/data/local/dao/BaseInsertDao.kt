package net.loeu.wallybudget.data.local.dao

import androidx.room.Insert

interface BaseInsertDao<T> {
    @Insert
    suspend fun insert(entity: T): Long
}
