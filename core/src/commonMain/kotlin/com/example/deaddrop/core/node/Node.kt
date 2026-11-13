package com.example.deaddrop.core.node

import com.example.deaddrop.core.crypto.Crypto
import com.example.deaddrop.core.crypto.KeyPair
import com.example.deaddrop.core.crypto.createCrypto
import com.example.deaddrop.core.model.*
import com.example.deaddrop.core.state.DerivedState
import com.example.deaddrop.core.state.StateBuilder
import com.example.deaddrop.core.store.InMemoryOpStore
import com.example.deaddrop.core.store.OpStore
import com.example.deaddrop.core.sync.RangeSetSummary
import com.example.deaddrop.core.sync.SyncMessage
import com.example.deaddrop.core.sync.SyncSerializer
import com.example.deaddrop.core.transport.PeerId
import com.example.deaddrop.core.transport.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class Node(
    val deviceId: String,
    val keys: KeyPair,
    val opStore: OpStore = InMemoryOpStore(),
    val transport: Transport? = null,
    val crypto: Crypto = createCrypto(),
    val regionKeys: Map<String, ByteArray> = emptyMap(),
    val allowedPublicKeys: Set<ByteArray> = emptySet(),
    val authorityPublicKey: ByteArray? = null
) {
    private var seqCounter: Long = 0
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val peerSummaries = mutableMapOf<PeerId, RangeSetSummary>()
    private val activeConnectedPeers = mutableSetOf<PeerId>()

    val bytesSent = MutableStateFlow(0L)
    val bytesReceived = MutableStateFlow(0L)

    init {
        val existingMaxSeq = opStore.getOpsForDevice(deviceId).maxOfOrNull { it.seq } ?: 0L
        seqCounter = existingMaxSeq

        if (transport != null) {
            scope.launch {
                transport.peers.collect { peersSet ->
                    activeConnectedPeers.clear()
                    activeConnectedPeers.addAll(peersSet)
                }
            }
            scope.launch {
                transport.incoming.collect { (peer, bytes) ->
                    bytesReceived.value += bytes.size
                    handleIncomingBytes(peer, bytes)
                }
            }
        }
    }

    @Synchronized
    private fun nextSeq(): Long {
        seqCounter++
        return seqCounter
    }

    fun getDerivedState(): DerivedState {
        return StateBuilder.build(opStore.getAllOps()) { region, ciphertext ->
            val key = regionKeys[region] ?: error("No key for region $region")
            crypto.decrypt(key, ciphertext)
        }
    }

    fun stateHash(): String = getDerivedState().stateHash

    fun createSetOp(
        entity: String,
        field: String,
        value: String,
        replaces: List<String> = emptyList(),
        region: String = "A",
        priority: Int = 1,
        hlc: Long = 0
    ): Op {
        val setBody = SetBody(entity, field, value, replaces)
        val bodyBytes = BodySerializer.encodeSetBody(setBody)
        val regionKey = regionKeys[region]
        val encryptedBody = if (regionKey != null) crypto.encrypt(regionKey, bodyBytes) else bodyBytes

        val seq = nextSeq()
        val unsignedOp = Op(
            dev = deviceId,
            seq = seq,
            kind = Kind.SET,
            region = region,
            priority = priority,
            hlc = hlc,
            body = encryptedBody,
            sig = ByteArray(0)
        )

        val sig = crypto.sign(keys.privateKey, unsignedOp.signaturePayload())
        val signedOp = unsignedOp.copy(sig = sig)

        opStore.addOp(signedOp)
        broadcastOpToPeers(signedOp)
        return signedOp
    }

    fun createMintOp(
        item: String,
        to: String,
        n: Long,
        region: String = "A",
        priority: Int = 0,
        hlc: Long = 0
    ): Op {
        val mintBody = MintBody(item, to, n)
        val bodyBytes = BodySerializer.encodeMintBody(mintBody)
        val regionKey = regionKeys[region]
        val encryptedBody = if (regionKey != null) crypto.encrypt(regionKey, bodyBytes) else bodyBytes

        val seq = nextSeq()
        val unsignedOp = Op(
            dev = deviceId,
            seq = seq,
            kind = Kind.MINT,
            region = region,
            priority = priority,
            hlc = hlc,
            body = encryptedBody,
            sig = ByteArray(0)
        )

        val sig = crypto.sign(keys.privateKey, unsignedOp.signaturePayload())
        val signedOp = unsignedOp.copy(sig = sig)

        opStore.addOp(signedOp)
        broadcastOpToPeers(signedOp)
        return signedOp
    }

    fun createMoveOp(
        item: String,
        from: String,
        to: String,
        n: Long,
        region: String = "A",
        priority: Int = 1,
        hlc: Long = 0
    ): Op? {
        if (from.startsWith("dev:") && from != "dev:$deviceId") {
            return null // Refuse: cannot move from another device's personal account
        }

        val currentState = getDerivedState()
        val fromBalance = currentState.balances[from]?.get(item) ?: 0L

        if (fromBalance < n) {
            return null // Refuse spend - insufficient balance
        }

        val moveBody = MoveBody(item, from, to, n)
        val bodyBytes = BodySerializer.encodeMoveBody(moveBody)
        val regionKey = regionKeys[region]
        val encryptedBody = if (regionKey != null) crypto.encrypt(regionKey, bodyBytes) else bodyBytes

        val seq = nextSeq()
        val unsignedOp = Op(
            dev = deviceId,
            seq = seq,
            kind = Kind.MOVE,
            region = region,
            priority = priority,
            hlc = hlc,
            body = encryptedBody,
            sig = ByteArray(0)
        )

        val sig = crypto.sign(keys.privateKey, unsignedOp.signaturePayload())
        val signedOp = unsignedOp.copy(sig = sig)

        opStore.addOp(signedOp)
        broadcastOpToPeers(signedOp)
        return signedOp
    }

    fun verifyAndAddOp(op: Op): Boolean {
        if (allowedPublicKeys.isNotEmpty()) {
            val keyFound = allowedPublicKeys.any { pubKey ->
                crypto.verify(pubKey, op.signaturePayload(), op.sig)
            }
            if (!keyFound) return false
        }
        return opStore.addOp(op)
    }

    private fun broadcastOpToPeers(op: Op, exceptPeer: PeerId? = null) {
        if (transport == null || activeConnectedPeers.isEmpty()) return
        val currentPeers = activeConnectedPeers.toSet()
        scope.launch {
            val batchMsg = SyncMessage.Ops(listOf(op))
            for (peer in currentPeers) {
                if (peer != exceptPeer) {
                    sendMsg(peer, batchMsg)
                }
            }
        }
    }

    suspend fun startSyncWith(peer: PeerId) {
        if (transport == null) return
        val summary = RangeSetSummary.fromOps(opStore.getAllOps())
        val helloMsg = SyncMessage.Hello(deviceId, summary)
        sendMsg(peer, helloMsg)
    }

    private suspend fun sendMsg(peer: PeerId, msg: SyncMessage) {
        val bytes = SyncSerializer.encode(msg)
        bytesSent.value += bytes.size
        transport?.send(peer, bytes)
    }

    private suspend fun handleIncomingBytes(peer: PeerId, bytes: ByteArray) {
        val message = try {
            SyncSerializer.decode(bytes)
        } catch (e: Exception) {
            return
        }

        when (message) {
            is SyncMessage.Hello -> {
                peerSummaries[peer] = message.summary

                val myOps = opStore.getAllOps()
                val missingForPeer = message.summary.missingOps(myOps)
                    .sortedWith(compareBy({ it.priority }, { it.dev }, { it.seq }))

                if (missingForPeer.isNotEmpty()) {
                    sendMsg(peer, SyncMessage.Ops(missingForPeer))
                }

                sendMsg(peer, SyncMessage.Ack(deviceId, RangeSetSummary.fromOps(myOps)))
            }

            is SyncMessage.Ops -> {
                for (op in message.batch) {
                    val addedNew = verifyAndAddOp(op)
                    if (addedNew) {
                        broadcastOpToPeers(op, exceptPeer = peer)
                    }
                }
            }

            is SyncMessage.Ack -> {
                peerSummaries[peer] = message.summary
                val myOps = opStore.getAllOps()
                val missingForPeer = message.summary.missingOps(myOps)
                    .sortedWith(compareBy({ it.priority }, { it.dev }, { it.seq }))
                if (missingForPeer.isNotEmpty()) {
                    sendMsg(peer, SyncMessage.Ops(missingForPeer))
                }
            }
        }
    }
}
