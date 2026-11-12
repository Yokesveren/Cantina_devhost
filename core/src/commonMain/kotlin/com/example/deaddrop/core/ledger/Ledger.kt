package com.example.deaddrop.core.ledger

import com.example.deaddrop.core.model.BodySerializer
import com.example.deaddrop.core.model.Kind
import com.example.deaddrop.core.model.Op

data class LedgerResult(
    val balances: Map<String, Map<String, Long>>, // accountId -> (item -> balance)
    val alerts: List<String>,
    val equivocatedDevices: Set<String>
)

object Ledger {

    fun build(
        ops: Collection<Op>,
        decryptor: ((region: String, body: ByteArray) -> ByteArray)? = null
    ): LedgerResult {
        val alerts = mutableListOf<String>()
        val equivocatedDevices = mutableSetOf<String>()

        // Equivocation check: dev:seq must uniquely map to body & fields
        val opIdMap = mutableMapOf<String, Op>()
        for (op in ops) {
            val existing = opIdMap[op.id]
            if (existing != null) {
                if (!existing.body.contentEquals(op.body) || existing.kind != op.kind) {
                    equivocatedDevices.add(op.dev)
                    alerts.add("Equivocation detected on device ${op.dev} at seq ${op.seq}! Device quarantined.")
                }
            } else {
                opIdMap[op.id] = op
            }
        }

        // Account balances: account -> (item -> amount)
        val inflows = mutableMapOf<String, MutableMap<String, Long>>()
        val outflows = mutableMapOf<String, MutableMap<String, Long>>()

        fun addInflow(account: String, item: String, n: Long) {
            val map = inflows.getOrPut(account) { mutableMapOf() }
            map[item] = (map[item] ?: 0L) + n
        }

        fun addOutflow(account: String, item: String, n: Long) {
            val map = outflows.getOrPut(account) { mutableMapOf() }
            map[item] = (map[item] ?: 0L) + n
        }

        for (op in ops) {
            if (op.dev in equivocatedDevices) continue // Skip quarantined ops

            val plainBytes = try {
                decryptor?.invoke(op.region, op.body) ?: op.body
            } catch (e: Exception) {
                op.body
            }

            when (op.kind) {
                Kind.MINT -> {
                    val mint = try {
                        BodySerializer.decodeMintBody(plainBytes)
                    } catch (e: Exception) {
                        continue
                    }
                    addInflow(mint.to, mint.item, mint.n)
                }

                Kind.MOVE -> {
                    val move = try {
                        BodySerializer.decodeMoveBody(plainBytes)
                    } catch (e: Exception) {
                        continue
                    }
                    addOutflow(move.from, move.item, move.n)
                    addInflow(move.to, move.item, move.n)
                }

                else -> {}
            }
        }

        val balances = mutableMapOf<String, MutableMap<String, Long>>()
        val allAccounts = inflows.keys + outflows.keys

        for (account in allAccounts) {
            val accountIn = inflows[account] ?: emptyMap()
            val accountOut = outflows[account] ?: emptyMap()
            val allItems = accountIn.keys + accountOut.keys

            val itemBalances = mutableMapOf<String, Long>()
            for (item in allItems) {
                val totalIn = accountIn[item] ?: 0L
                val totalOut = accountOut[item] ?: 0L
                if (totalOut > totalIn) {
                    alerts.add("Integrity alert: Account $account overspent $item (in=$totalIn, out=$totalOut)")
                }
                itemBalances[item] = totalIn - totalOut
            }
            balances[account] = itemBalances
        }

        return LedgerResult(
            balances = balances,
            alerts = alerts,
            equivocatedDevices = equivocatedDevices
        )
    }
}
