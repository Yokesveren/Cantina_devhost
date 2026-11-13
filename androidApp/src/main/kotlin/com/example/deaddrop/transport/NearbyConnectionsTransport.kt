package com.example.deaddrop.transport

import android.content.Context
import android.util.Log
import com.example.deaddrop.core.transport.PeerId
import com.example.deaddrop.core.transport.Transport
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NearbyConnectionsTransport(
    private val context: Context,
    val localDeviceId: String,
    private val scope: CoroutineScope
) : Transport {

    companion object {
        private const val TAG = "NearbyTransport"
        const val SERVICE_ID = "com.example.deaddrop.offline.mesh"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context.applicationContext)

    private val _peers = MutableStateFlow<Set<PeerId>>(emptySet())
    override val peers: StateFlow<Set<PeerId>> = _peers.asStateFlow()

    private val _incoming = MutableSharedFlow<Pair<PeerId, ByteArray>>(extraBufferCapacity = 1000)
    override val incoming: SharedFlow<Pair<PeerId, ByteArray>> = _incoming

    private val _isRadioOn = MutableStateFlow(true)
    val isRadioOn: StateFlow<Boolean> = _isRadioOn.asStateFlow()

    val totalBytesSent = MutableStateFlow(0L)
    val totalBytesReceived = MutableStateFlow(0L)

    private val endpointToPeerMap = ConcurrentHashMap<String, PeerId>()
    private val peerToEndpointMap = ConcurrentHashMap<PeerId, String>()

    var onPeerConnected: ((PeerId) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                val peerId = endpointToPeerMap[endpointId]
                if (bytes != null && peerId != null) {
                    totalBytesReceived.value += bytes.size
                    Log.d(TAG, "Received ${bytes.size} bytes from $peerId ($endpointId)")
                    scope.launch {
                        _incoming.emit(Pair(peerId, bytes))
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val remoteName = info.endpointName
            Log.d(TAG, "Connection initiated from endpoint $endpointId ($remoteName)")
            endpointToPeerMap[endpointId] = remoteName
            peerToEndpointMap[remoteName] = endpointId
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val remotePeerId = endpointToPeerMap[endpointId] ?: endpointId
                Log.d(TAG, "Connection SUCCESS with $remotePeerId ($endpointId)")
                endpointToPeerMap[endpointId] = remotePeerId
                peerToEndpointMap[remotePeerId] = endpointId
                updatePeersState()
                onPeerConnected?.invoke(remotePeerId)
            } else {
                Log.e(TAG, "Connection FAILED with endpoint $endpointId, status: ${result.status.statusCode}")
                val remotePeerId = endpointToPeerMap.remove(endpointId)
                if (remotePeerId != null) peerToEndpointMap.remove(remotePeerId)
                updatePeersState()
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from endpoint $endpointId")
            val remotePeerId = endpointToPeerMap.remove(endpointId)
            if (remotePeerId != null) peerToEndpointMap.remove(remotePeerId)
            updatePeersState()
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint FOUND: $endpointId (${info.endpointName}) serviceId=${info.serviceId}")
            if (info.serviceId == SERVICE_ID) {
                connectionsClient.requestConnection(localDeviceId, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener { Log.d(TAG, "Connection request sent to $endpointId") }
                    .addOnFailureListener { e -> Log.e(TAG, "Connection request failed: ${e.message}") }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint LOST: $endpointId")
            val remotePeerId = endpointToPeerMap.remove(endpointId)
            if (remotePeerId != null) peerToEndpointMap.remove(remotePeerId)
            updatePeersState()
        }
    }

    fun startAdvertisingAndDiscovery() {
        if (!_isRadioOn.value) return

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(localDeviceId, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener { Log.d(TAG, "Advertising STARTED as $localDeviceId") }
            .addOnFailureListener { e -> Log.e(TAG, "Advertising FAILED for $localDeviceId: ${e.message}", e) }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.d(TAG, "Discovery STARTED as $localDeviceId") }
            .addOnFailureListener { e -> Log.e(TAG, "Discovery FAILED for $localDeviceId: ${e.message}", e) }
    }

    fun stopAdvertisingAndDiscovery() {
        Log.d(TAG, "Stopping advertising and discovery for $localDeviceId")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        endpointToPeerMap.clear()
        peerToEndpointMap.clear()
        updatePeersState()
    }

    fun setRadioState(enabled: Boolean) {
        _isRadioOn.value = enabled
        if (enabled) {
            startAdvertisingAndDiscovery()
        } else {
            stopAdvertisingAndDiscovery()
        }
    }

    private fun updatePeersState() {
        _peers.value = peerToEndpointMap.keys.toSet()
    }

    override suspend fun send(peer: PeerId, bytes: ByteArray) {
        if (!_isRadioOn.value) return
        val endpointId = peerToEndpointMap[peer] ?: return
        totalBytesSent.value += bytes.size
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }
}
