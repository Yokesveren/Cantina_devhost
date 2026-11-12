package com.example.deaddrop.sim

import com.example.deaddrop.core.transport.PeerId
import com.example.deaddrop.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class LinkState(
    val isCut: Boolean = false,
    val latencyMs: Long = 0,
    val lossRate: Double = 0.0
)

class SimTransport(
    val peerId: PeerId,
    private val network: VirtualNetwork
) : Transport {

    private val _peers = MutableStateFlow<Set<PeerId>>(emptySet())
    override val peers: StateFlow<Set<PeerId>> = _peers

    private val _incoming = MutableSharedFlow<Pair<PeerId, ByteArray>>(extraBufferCapacity = 1000)
    override val incoming: SharedFlow<Pair<PeerId, ByteArray>> = _incoming

    fun updatePeers(newPeers: Set<PeerId>) {
        _peers.value = newPeers
    }

    suspend fun receiveFromPeer(fromPeer: PeerId, bytes: ByteArray) {
        _incoming.emit(Pair(fromPeer, bytes))
    }

    override suspend fun send(peer: PeerId, bytes: ByteArray) {
        network.routeMessage(peerId, peer, bytes)
    }
}

class VirtualNetwork(private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    private val transports = ConcurrentHashMap<PeerId, SimTransport>()
    private val links = ConcurrentHashMap<Pair<PeerId, PeerId>, LinkState>()

    fun createTransport(peerId: PeerId): SimTransport {
        val transport = SimTransport(peerId, this)
        transports[peerId] = transport
        updateAllPeers()
        return transport
    }

    fun setLinkCut(p1: PeerId, p2: PeerId, isCut: Boolean) {
        val linkState = links[Pair(p1, p2)] ?: LinkState()
        links[Pair(p1, p2)] = linkState.copy(isCut = isCut)
        links[Pair(p2, p1)] = linkState.copy(isCut = isCut)
        updateAllPeers()
    }

    fun cutAllLinks() {
        for (p1 in transports.keys) {
            for (p2 in transports.keys) {
                if (p1 != p2) {
                    setLinkCut(p1, p2, true)
                }
            }
        }
    }

    fun restoreAllLinks() {
        for (p1 in transports.keys) {
            for (p2 in transports.keys) {
                if (p1 != p2) {
                    setLinkCut(p1, p2, false)
                }
            }
        }
    }

    fun isConnected(p1: PeerId, p2: PeerId): Boolean {
        if (p1 == p2) return true
        val link = links[Pair(p1, p2)]
        return link?.isCut != true
    }

    private fun updateAllPeers() {
        val allPeers = transports.keys
        for ((peerId, transport) in transports) {
            val connected = allPeers.filter { other -> other != peerId && isConnected(peerId, other) }.toSet()
            transport.updatePeers(connected)
        }
    }

    fun routeMessage(sender: PeerId, receiver: PeerId, bytes: ByteArray) {
        if (!isConnected(sender, receiver)) return // Link is cut

        val receiverTransport = transports[receiver] ?: return

        scope.launch {
            receiverTransport.receiveFromPeer(sender, bytes)
        }
    }
}
