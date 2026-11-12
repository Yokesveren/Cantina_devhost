package com.example.deaddrop.core.transport

import kotlinx.coroutines.flow.Flow

typealias PeerId = String

interface Transport {
    val peers: Flow<Set<PeerId>>
    suspend fun send(peer: PeerId, bytes: ByteArray)
    val incoming: Flow<Pair<PeerId, ByteArray>>
}
