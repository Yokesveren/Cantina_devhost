package com.example.deaddrop.core.sync

import com.example.deaddrop.core.model.Op
import kotlinx.serialization.Serializable

@Serializable
data class SequenceRange(val start: Long, val end: Long) {
    fun contains(seq: Long): Boolean = seq in start..end
}

@Serializable
data class RangeSetSummary(
    val ranges: Map<String, List<SequenceRange>> = emptyMap()
) {
    fun contains(dev: String, seq: Long): Boolean {
        val devRanges = ranges[dev] ?: return false
        return devRanges.any { it.contains(seq) }
    }

    fun missingOps(ops: Collection<Op>): List<Op> {
        return ops.filter { !contains(it.dev, it.seq) }
    }

    companion object {
        fun fromOps(ops: Collection<Op>): RangeSetSummary {
            val map = mutableMapOf<String, MutableList<SequenceRange>>()
            val grouped = ops.groupBy { it.dev }
            for ((dev, devOps) in grouped) {
                val seqs = devOps.map { it.seq }.distinct().sorted()
                if (seqs.isEmpty()) continue
                val devRanges = mutableListOf<SequenceRange>()
                var start = seqs.first()
                var prev = start
                for (i in 1 until seqs.size) {
                    val current = seqs[i]
                    if (current == prev + 1) {
                        prev = current
                    } else {
                        devRanges.add(SequenceRange(start, prev))
                        start = current
                        prev = current
                    }
                }
                devRanges.add(SequenceRange(start, prev))
                map[dev] = devRanges
            }
            return RangeSetSummary(map)
        }
    }
}
