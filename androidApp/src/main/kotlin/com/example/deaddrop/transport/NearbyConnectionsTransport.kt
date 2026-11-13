package com.example.deaddrop.transport

import android.content.Context
import android.util.Log
import com.example.deaddrop.core.transport.PeerId
import com.example.deaddrop.core.transport.Transport
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

enum class EndpointState {
    DISCOVERED,
    REQUESTED,
    CONNECTING,
    CONNECTED
}

class NearbyConnectionsTransport(
    private val context: Context,
    val localDeviceId: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Transport {

    companion object {
        private const val TAG = "P2P"
        const val SERVICE_ID = "com.example.deaddrop.offline.mesh"
        private const val PULSE_INTERVAL_MS = 8000L
    }

    private val connectionsClient = Nearby.getConnectionsClient(context.applicationContext)

    private val _peers = MutableStateFlow<Set<PeerId>>(emptySet())
    override val peers: StateFlow<Set<PeerId>> = _peers.asStateFlow()

    private val _incoming = MutableSharedFlow<Pair<PeerId, ByteArray>>(extraBufferCapacity = 1000)
    override val incoming: SharedFlow<Pair<PeerId, ByteArray>> = _incoming

    val totalBytesSent = MutableStateFlow(0L)
    val totalBytesReceived = MutableStateFlow(0L)

    private val endpointStates = ConcurrentHashMap<String, EndpointState>()
    private val endpointDeviceMap = ConcurrentHashMap<String, String>()
    private val deviceEndpointMap = ConcurrentHashMap<String, String>()

    private var isAdvertising = false
    private var isDiscovering = false

    var onPeerConnected: ((PeerId) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                val peerId = endpointDeviceMap[endpointId]
                if (bytes != null && peerId != null) {
                    totalBytesReceived.value += bytes.size
                    Log.d(TAG, "[P2P] Received ${bytes.size} bytes from $peerId ($endpointId)")
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
            Log.d(TAG, "[P2P] Connection initiated from endpoint $endpointId ($remoteName)")
            endpointStates[endpointId] = EndpointState.CONNECTING
            endpointDeviceMap[endpointId] = remoteName
            deviceEndpointMap[remoteName] = endpointId

            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "[P2P] Connection accepted for endpoint $endpointId ($remoteName)")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "[P2P] Connection accept failed for endpoint $endpointId: ${e.message}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val remotePeerId = endpointDeviceMap[endpointId] ?: endpointId
            val statusCode = result.status.statusCode
            val isSuccess = result.status.isSuccess || statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT

            Log.d(TAG, "[P2P] Connection result for $endpointId ($remotePeerId): status=$statusCode (success=$isSuccess)")

            if (isSuccess) {
                endpointStates[endpointId] = EndpointState.CONNECTED
                updatePeersState()
                onPeerConnected?.invoke(remotePeerId)
            } else {
                Log.e(TAG, "[P2P] Connection failed with endpoint $endpointId ($remotePeerId), status: $statusCode")
                cleanupEndpoint(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val remotePeerId = endpointDeviceMap[endpointId] ?: endpointId
            Log.d(TAG, "[P2P] Disconnected from endpoint $endpointId ($remotePeerId)")
            cleanupEndpoint(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val remoteDeviceId = info.endpointName
            Log.d(TAG, "[P2P] Endpoint found: $endpointId ($remoteDeviceId) serviceId=${info.serviceId}")

            if (info.serviceId != SERVICE_ID) return

            val currentState = endpointStates[endpointId]
            val existingEndpointForDevice = deviceEndpointMap[remoteDeviceId]
            val existingDeviceState = existingEndpointForDevice?.let { endpointStates[it] }

            if (currentState == EndpointState.REQUESTED || currentState == EndpointState.CONNECTING || currentState == EndpointState.CONNECTED ||
                existingDeviceState == EndpointState.REQUESTED || existingDeviceState == EndpointState.CONNECTING || existingDeviceState == EndpointState.CONNECTED) {
                Log.d(TAG, "[P2P] Ignoring discovered endpoint $endpointId ($remoteDeviceId) - device already in state ${currentState ?: existingDeviceState}")
                return
            }

            endpointStates[endpointId] = EndpointState.DISCOVERED
            endpointDeviceMap[endpointId] = remoteDeviceId

            val shouldInitiateRequest = localDeviceId < remoteDeviceId
            Log.d(TAG, "[P2P] Lexicographical check: local ($localDeviceId), remote ($remoteDeviceId). Initiating request = $shouldInitiateRequest")

            if (shouldInitiateRequest) {
                requestConnectionToEndpoint(endpointId, remoteDeviceId)
            } else {
                Log.d(TAG, "[P2P] Waiting for lower ID device ($remoteDeviceId) to request connection.")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val remoteDeviceId = endpointDeviceMap[endpointId] ?: endpointId
            Log.d(TAG, "[P2P] Endpoint lost: $endpointId ($remoteDeviceId)")
            cleanupEndpoint(endpointId)
        }
    }

    private fun requestConnectionToEndpoint(endpointId: String, remoteDeviceId: String) {
        endpointStates[endpointId] = EndpointState.REQUESTED
        Log.d(TAG, "[P2P] Connection requested to $endpointId ($remoteDeviceId)")

        connectionsClient.requestConnection(localDeviceId, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                Log.d(TAG, "[P2P] Connection request sent successfully to $endpointId ($remoteDeviceId)")
            }
            .addOnFailureListener { e ->
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    Log.d(TAG, "[P2P] Already connected to endpoint $endpointId ($remoteDeviceId)")
                    endpointStates[endpointId] = EndpointState.CONNECTED
                } else {
                    Log.e(TAG, "[P2P] Connection request failed for $endpointId ($remoteDeviceId): ${e.message} (code=$statusCode)")
                    endpointStates[endpointId] = EndpointState.DISCOVERED
                }
            }
    }

    init {
        startAdvertisingAndDiscovery()
        startAutoPulseLoop()
    }

    @Synchronized
    fun startAdvertisingAndDiscovery() {
        if (_peers.value.isNotEmpty()) return

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(localDeviceId, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener {
                isAdvertising = true
                Log.d(TAG, "[P2P] Advertising started as $localDeviceId")
            }
            .addOnFailureListener { e ->
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    isAdvertising = true
                } else {
                    isAdvertising = false
                    Log.e(TAG, "[P2P] Advertising failed for $localDeviceId: ${e.message} (code=$statusCode)")
                }
            }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                isDiscovering = true
                Log.d(TAG, "[P2P] Discovery started as $localDeviceId")
            }
            .addOnFailureListener { e ->
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    isDiscovering = true
                } else {
                    isDiscovering = false
                    Log.e(TAG, "[P2P] Discovery failed for $localDeviceId: ${e.message} (code=$statusCode)")
                }
            }
    }

    private fun stopScanningOnly() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "[P2P] Error stopping scanning: ${e.message}")
        }
        isAdvertising = false
        isDiscovering = false
    }

    fun stopAdvertisingAndDiscovery() {
        Log.d(TAG, "[P2P] Stopping all advertising, discovery, and endpoints for $localDeviceId")
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e(TAG, "[P2P] Error stopping connections: ${e.message}")
        }
        isAdvertising = false
        isDiscovering = false
        endpointStates.clear()
        endpointDeviceMap.clear()
        deviceEndpointMap.clear()
        updatePeersState()
    }

    private fun startAutoPulseLoop() {
        scope.launch {
            while (isActive) {
                delay(PULSE_INTERVAL_MS)
                if (_peers.value.isEmpty()) {
                    Log.d(TAG, "[P2P Pulse] Search pulse: Resetting scanner to clear BLE deadlocks...")
                    stopScanningOnly()
                    delay(1000)
                    if (_peers.value.isEmpty()) {
                        startAdvertisingAndDiscovery()
                    }
                } else {
                    Log.d(TAG, "[P2P Pulse] Connected to ${_peers.value.size} peer(s). Pulse holding steady.")
                }
            }
        }
    }

    private fun cleanupEndpoint(endpointId: String) {
        endpointStates.remove(endpointId)
        val remotePeerId = endpointDeviceMap.remove(endpointId)
        if (remotePeerId != null) {
            deviceEndpointMap.remove(remotePeerId)
        }
        updatePeersState()
    }

    private fun updatePeersState() {
        _peers.value = deviceEndpointMap.keys.toSet()
    }

    override suspend fun send(peer: PeerId, bytes: ByteArray) {
        val endpointId = deviceEndpointMap[peer] ?: return
        totalBytesSent.value += bytes.size
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }
}
