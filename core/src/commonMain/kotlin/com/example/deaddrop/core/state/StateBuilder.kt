package com.example.deaddrop.core.state

import com.example.deaddrop.core.crypto.sha256Hex
import com.example.deaddrop.core.ledger.Ledger
import com.example.deaddrop.core.model.BodySerializer
import com.example.deaddrop.core.model.Kind
import com.example.deaddrop.core.model.Op
import com.example.deaddrop.core.model.SetBody

data class FieldValue(
    val opId: String,
    val value: String,
    val dev: String,
    val hlc: Long
)

data class Conflict(
    val entityId: String,
    val field: String,
    val liveValues: List<FieldValue>
)

data class EntityState(
    val id: String,
    val type: String, // e.g. "need", "shelter", "person"
    val fields: Map<String, String>,
    val rawLiveFields: Map<String, List<FieldValue>>
)

data class DerivedState(
    val entities: Map<String, EntityState>,
    val conflicts: List<Conflict>,
    val balances: Map<String, Map<String, Long>>,
    val alerts: List<String>,
    val stateHash: String
)

object StateBuilder {

    fun build(
        ops: Collection<Op>,
        decryptor: ((region: String, body: ByteArray) -> ByteArray)? = null
    ): DerivedState {
        // Sort ops deterministically by dev, seq, and id so order of arrival never affects calculation
        val sortedOps = ops.sortedWith(compareBy({ it.dev }, { it.seq }, { it.id }))

        // 1. Process Ledger (MINT & MOVE ops)
        val ledgerResult = Ledger.build(sortedOps, decryptor)

        // 2. Process SET ops
        // Map: entityId -> field -> List<Pair<Op, SetBody>>
        val setOpsMap = mutableMapOf<String, MutableMap<String, MutableList<Pair<Op, SetBody>>>>()

        for (op in sortedOps) {
            if (op.kind != Kind.SET) continue
            val plainBodyBytes = try {
                decryptor?.invoke(op.region, op.body) ?: op.body
            } catch (e: Exception) {
                op.body
            }

            val setBody = try {
                BodySerializer.decodeSetBody(plainBodyBytes)
            } catch (e: Exception) {
                continue
            }

            setOpsMap.getOrPut(setBody.entity) { mutableMapOf() }
                .getOrPut(setBody.field) { mutableListOf() }
                .add(Pair(op, setBody))
        }

        val entitiesMap = mutableMapOf<String, EntityState>()
        val conflictsList = mutableListOf<Conflict>()

        for ((entityId, fieldsMap) in setOpsMap) {
            val liveFields = mutableMapOf<String, List<FieldValue>>()
            val effectiveFields = mutableMapOf<String, String>()

            for ((field, opsList) in fieldsMap) {
                val replacedSet = opsList.flatMap { it.second.replaces }.toSet()
                val liveOps = opsList.filter { it.first.id !in replacedSet }

                val fieldVals = liveOps.map { (op, body) ->
                    FieldValue(
                        opId = op.id,
                        value = body.value,
                        dev = op.dev,
                        hlc = op.hlc
                    )
                }.sortedWith(compareBy({ it.dev }, { it.opId }))

                liveFields[field] = fieldVals

                if (fieldVals.size > 1) {
                    conflictsList.add(Conflict(entityId, field, fieldVals))
                    // Pick deterministic live value (by hlc descending, then by opId)
                    val chosen = fieldVals.sortedWith(compareByDescending<FieldValue> { it.hlc }.thenBy { it.opId }).first()
                    effectiveFields[field] = chosen.value
                } else if (fieldVals.isNotEmpty()) {
                    effectiveFields[field] = fieldVals.first().value
                }
            }

            val type = entityId.substringBefore(":")
            entitiesMap[entityId] = EntityState(
                id = entityId,
                type = type,
                fields = effectiveFields,
                rawLiveFields = liveFields
            )
        }

        // 3. Compute deterministic State Hash
        val sortedOpIds = sortedOps.map { it.id }.joinToString(",")

        val sortedEntitiesString = entitiesMap.keys.sorted().joinToString(";") { eId ->
            val e = entitiesMap[eId]!!
            val fieldsStr = e.fields.keys.sorted().joinToString(",") { f -> "$f=${e.fields[f]}" }
            "$eId:[$fieldsStr]"
        }

        val sortedBalancesString = ledgerResult.balances.keys.sorted().joinToString(";") { acc ->
            val accBal = ledgerResult.balances[acc]!!
            val itemsStr = accBal.keys.sorted().joinToString(",") { item -> "$item=${accBal[item]}" }
            "$acc:[$itemsStr]"
        }

        val canonicalData = "$sortedOpIds|$sortedEntitiesString|$sortedBalancesString"
        val stateHash = sha256Hex(canonicalData.encodeToByteArray())

        return DerivedState(
            entities = entitiesMap,
            conflicts = conflictsList.sortedWith(compareBy({ it.entityId }, { it.field })),
            balances = ledgerResult.balances,
            alerts = ledgerResult.alerts,
            stateHash = stateHash
        )
    }
}
