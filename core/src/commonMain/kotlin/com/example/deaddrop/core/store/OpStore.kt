package com.example.deaddrop.core.store

import com.example.deaddrop.core.model.Op
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface OpStore {
    fun addOp(op: Op): Boolean
    fun addOps(ops: Collection<Op>): List<Op>
    fun getOp(id: String): Op?
    fun getAllOps(): List<Op>
    fun getOpsForDevice(dev: String): List<Op>
    val opFlow: StateFlow<List<Op>>
}

class InMemoryOpStore : OpStore {
    private val opsMap = LinkedHashMap<String, Op>()
    private val _opFlow = MutableStateFlow<List<Op>>(emptyList())
    override val opFlow: StateFlow<List<Op>> = _opFlow.asStateFlow()

    @Synchronized
    override fun addOp(op: Op): Boolean {
        if (opsMap.containsKey(op.id)) return false
        opsMap[op.id] = op
        _opFlow.value = opsMap.values.toList()
        return true
    }

    @Synchronized
    override fun addOps(ops: Collection<Op>): List<Op> {
        val added = mutableListOf<Op>()
        for (op in ops) {
            if (!opsMap.containsKey(op.id)) {
                opsMap[op.id] = op
                added.add(op)
            }
        }
        if (added.isNotEmpty()) {
            _opFlow.value = opsMap.values.toList()
        }
        return added
    }

    @Synchronized
    override fun getOp(id: String): Op? = opsMap[id]

    @Synchronized
    override fun getAllOps(): List<Op> = opsMap.values.toList()

    @Synchronized
    override fun getOpsForDevice(dev: String): List<Op> {
        return opsMap.values.filter { it.dev == dev }.sortedBy { it.seq }
    }
}
