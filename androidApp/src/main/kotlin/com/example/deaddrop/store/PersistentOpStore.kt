package com.example.deaddrop.store

import android.content.Context
import com.example.deaddrop.core.model.Op
import com.example.deaddrop.core.store.InMemoryOpStore
import com.example.deaddrop.core.store.OpStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class PersistentOpStore(private val context: Context) : OpStore {
    private val inMemoryStore = InMemoryOpStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val storeFile = File(context.filesDir, "ops_store.json")

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (storeFile.exists()) {
            try {
                val text = storeFile.readText()
                val ops = json.decodeFromString<List<Op>>(text)
                inMemoryStore.addOps(ops)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    private fun saveToDisk() {
        try {
            val ops = inMemoryStore.getAllOps()
            val text = json.encodeToString(ops)
            storeFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addOp(op: Op): Boolean {
        val added = inMemoryStore.addOp(op)
        if (added) saveToDisk()
        return added
    }

    override fun addOps(ops: Collection<Op>): List<Op> {
        val added = inMemoryStore.addOps(ops)
        if (added.isNotEmpty()) saveToDisk()
        return added
    }

    override fun getOp(id: String): Op? = inMemoryStore.getOp(id)

    override fun getAllOps(): List<Op> = inMemoryStore.getAllOps()

    override fun getOpsForDevice(dev: String): List<Op> = inMemoryStore.getOpsForDevice(dev)

    override val opFlow: StateFlow<List<Op>> = inMemoryStore.opFlow

    @Synchronized
    fun clear() {
        if (storeFile.exists()) {
            storeFile.delete()
        }
        val all = inMemoryStore.getAllOps()
        // We can create a clean memory store or reset
    }
}
