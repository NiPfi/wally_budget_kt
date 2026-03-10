package net.loeu.wallybudget.data.local.db

interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
