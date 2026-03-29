package net.loeu.wallybudget.data.local.db

import android.content.Context
import androidx.room.Room

const val BUDGET_DATABASE_NAME = "budget_database"

object BudgetDatabaseFactory {
    fun create(context: Context, installId: String): BudgetDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            BudgetDatabase::class.java,
            BUDGET_DATABASE_NAME
        )
            .addMigrations(*BudgetDatabaseMigrations.all(installId))
            .build()
    }
}
