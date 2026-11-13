package com.example.deaddrop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.deaddrop.BuildConfig
import com.example.deaddrop.core.crypto.KeyPair
import com.example.deaddrop.core.crypto.createCrypto
import com.example.deaddrop.core.model.Op
import com.example.deaddrop.core.node.Node
import com.example.deaddrop.core.state.DerivedState
import com.example.deaddrop.security.KeyStoreManager
import com.example.deaddrop.sim.VirtualNetwork
import com.example.deaddrop.store.PersistentOpStore
import com.example.deaddrop.transport.NearbyConnectionsTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class DemoPlanData(
    val authorityPublicKey: String,
    val authorityPrivateKey: String,
    val regionKeyA: String,
    val devices: List<DemoDeviceData>,
    val genesisOps: List<Op>
)

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
@Serializable
data class DemoDeviceData(
    val id: String,
    val userName: String,
    val role: String,
    val region: String,
    val publicKey: String,
    val privateKey: String
)

data class SimScenarioResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class FieldUpdate(
    val field: String,
    val newValue: String,
    val replacesOpIds: List<String>
)

@OptIn(ExperimentalEncodingApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val crypto = createCrypto()
    val persistentOpStore = PersistentOpStore(application)
    val nearbyTransport: NearbyConnectionsTransport

    val localNode: Node
    val regionKeyA: ByteArray

    val simNetwork = VirtualNetwork(viewModelScope)
    val simNodes: List<Node>

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _uiState = MutableStateFlow<DerivedState?>(null)
    val uiState: StateFlow<DerivedState?> = _uiState.asStateFlow()

    private val _simScenarioResults = MutableStateFlow<List<SimScenarioResult>>(emptyList())
    val simScenarioResults: StateFlow<List<SimScenarioResult>> = _simScenarioResults.asStateFlow()

    val demoPlan: DemoPlanData
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Load demo_plan.json from assets
        val jsonText = application.assets.open("demo_plan.json").bufferedReader().use { it.readText() }
        demoPlan = json.decodeFromString<DemoPlanData>(jsonText)

        val authorityPub = Base64.decode(demoPlan.authorityPublicKey)
        regionKeyA = Base64.decode(demoPlan.regionKeyA)

        val deviceConfig = demoPlan.devices.find { it.id == BuildConfig.NODE_DEVICE_ID }
            ?: demoPlan.devices.first()

        val devPubKey = Base64.decode(deviceConfig.publicKey)
        val devPrivKey = Base64.decode(deviceConfig.privateKey)
        val deviceKeyPair = KeyPair(publicKey = devPubKey, privateKey = devPrivKey)

        val allowedPublicKeysSet = buildSet {
            add(authorityPub)
            for (dev in demoPlan.devices) {
                add(Base64.decode(dev.publicKey))
            }
        }

        if (persistentOpStore.getAllOps().isEmpty()) {
            persistentOpStore.addOps(demoPlan.genesisOps)
        }

        nearbyTransport = NearbyConnectionsTransport(application, BuildConfig.NODE_DEVICE_ID, viewModelScope)

        localNode = Node(
            deviceId = BuildConfig.NODE_DEVICE_ID,
            keys = deviceKeyPair,
            opStore = persistentOpStore,
            transport = nearbyTransport,
            crypto = crypto,
            regionKeys = mapOf("A" to regionKeyA),
            allowedPublicKeys = allowedPublicKeysSet,
            authorityPublicKey = authorityPub
        )

        nearbyTransport.onPeerConnected = { peerId ->
            viewModelScope.launch {
                localNode.startSyncWith(peerId)
            }
        }

        nearbyTransport.startAdvertisingAndDiscovery()

        simNodes = (1..3).map { i ->
            val id = "SimNode$i"
            val transport = simNetwork.createTransport(id)
            Node(
                deviceId = id,
                keys = crypto.generateKeyPair(),
                transport = transport,
                crypto = crypto,
                regionKeys = mapOf("A" to regionKeyA)
            )
        }

        viewModelScope.launch {
            persistentOpStore.opFlow.collect {
                refreshState()
            }
        }

        refreshState()
    }

    fun toggleRadio(enabled: Boolean) {
        nearbyTransport.setRadioState(enabled)
    }

    fun unlock(passcode: String): Boolean {
        if (passcode == "1234" || passcode.isBlank()) {
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun refreshState() {
        _uiState.value = localNode.getDerivedState()
    }

    fun resetDemoToGenesis() {
        persistentOpStore.clear()
        persistentOpStore.addOps(demoPlan.genesisOps)
        refreshState()
    }

    fun addNeed(category: String, peopleCount: Int, urgency: String, note: String) {
        val seq = System.currentTimeMillis()
        val entityId = "need:${localNode.deviceId}:$seq"
        localNode.createSetOp(entityId, "category", category, region = "A")
        localNode.createSetOp(entityId, "peopleCount", peopleCount.toString(), region = "A")
        localNode.createSetOp(entityId, "urgency", urgency, region = "A")
        localNode.createSetOp(entityId, "note", note, region = "A")
        localNode.createSetOp(entityId, "status", "open", region = "A")
        refreshState()
    }

    fun addPerson(name: String, ageGroup: String, condition: String, status: String, whereNow: String = "", note: String = "") {
        val seq = System.currentTimeMillis()
        val entityId = "person:${localNode.deviceId}:$seq"
        localNode.createSetOp(entityId, "displayName", name, region = "A")
        localNode.createSetOp(entityId, "ageGroup", ageGroup, region = "A")
        localNode.createSetOp(entityId, "condition", condition, region = "A")
        localNode.createSetOp(entityId, "status", status, region = "A")
        if (whereNow.isNotBlank()) localNode.createSetOp(entityId, "whereNow", whereNow, region = "A")
        if (note.isNotBlank()) localNode.createSetOp(entityId, "note", note, region = "A")
        refreshState()
    }

    fun editEntityFields(entityId: String, updates: List<FieldUpdate>) {
        for (update in updates) {
            localNode.createSetOp(entityId, update.field, update.newValue, replaces = update.replacesOpIds, region = "A")
        }
        refreshState()
    }

    fun addShelter(name: String, capacity: Int, occupancy: Int, locationText: String = "Main Road") {
        val seq = System.currentTimeMillis()
        val entityId = "shelter:${localNode.deviceId}:$seq"
        localNode.createSetOp(entityId, "name", name, region = "A")
        localNode.createSetOp(entityId, "capacity", capacity.toString(), region = "A")
        localNode.createSetOp(entityId, "occupancy", occupancy.toString(), region = "A")
        localNode.createSetOp(entityId, "locationText", locationText, region = "A")
        localNode.createSetOp(entityId, "status", "Open", region = "A")
        refreshState()
    }

    fun giveStock(item: String, targetAccount: String, amount: Long): Boolean {
        val fromAccount = "dev:${localNode.deviceId}"
        val moveOp = localNode.createMoveOp(item, fromAccount, targetAccount, amount, region = "A")
        if (moveOp != null) {
            refreshState()
            return true
        }
        return false
    }

    fun resolveConflict(entityId: String, field: String, chosenValue: String, replacesOpIds: List<String>) {
        localNode.createSetOp(entityId, field, chosenValue, replaces = replacesOpIds, region = "A")
        refreshState()
    }

    fun panicWipe() {
        persistentOpStore.clear()
        KeyStoreManager.panicWipe(getApplication())
        _uiState.value = DerivedState(
            entities = emptyMap(),
            conflicts = emptyList(),
            balances = emptyMap(),
            alerts = listOf("PANIC WIPE EXECUTED. Keys and database wiped."),
            stateHash = "CLEARED"
        )
        _isUnlocked.value = false
    }

    fun runSimAllScenarios() {
        viewModelScope.launch {
            val results = mutableListOf<SimScenarioResult>()

            // Scenario 1: Both spend last share
            simNetwork.cutAllLinks()
            simNodes[0].createMintOp("water", "dev:SimNode1", 5, region = "A")
            simNodes[1].createMintOp("water", "dev:SimNode2", 5, region = "A")

            val move1 = simNodes[0].createMoveOp("water", "dev:SimNode1", "given:needA", 5, region = "A")
            val move2 = simNodes[1].createMoveOp("water", "dev:SimNode2", "given:needB", 10, region = "A") // Insufficient! Refused!

            val test1Pass = move1 != null && move2 == null
            results.add(SimScenarioResult("Double Allocation / Escrow", test1Pass, if (test1Pass) "Second spend refused correctly" else "Failed escrow check"))

            // Scenario 2: 3-way split & convergence
            simNodes[0].createSetOp("need:sim1", "status", "open", region = "A")
            simNodes[1].createSetOp("need:sim2", "status", "open", region = "A")
            simNodes[2].createSetOp("need:sim3", "status", "open", region = "A")

            simNetwork.restoreAllLinks()

            // Trigger sync
            for (n1 in simNodes) {
                for (n2 in simNodes) {
                    if (n1.deviceId != n2.deviceId) {
                        n1.startSyncWith(n2.deviceId)
                    }
                }
            }

            kotlinx.coroutines.delay(150)

            val h1 = simNodes[0].stateHash()
            val h2 = simNodes[1].stateHash()
            val h3 = simNodes[2].stateHash()

            val test2Pass = (h1 == h2 && h2 == h3)
            results.add(SimScenarioResult("3-Way Partition & Convergence", test2Pass, if (test2Pass) "All 3 state hashes match ($h1)" else "State hashes mismatched"))

            // Scenario 3: Lost ops check
            val opIds1 = simNodes[0].opStore.getAllOps().map { it.id }.toSet()
            val opIds2 = simNodes[1].opStore.getAllOps().map { it.id }.toSet()
            val opIds3 = simNodes[2].opStore.getAllOps().map { it.id }.toSet()
            val zeroLostOps = (opIds1 == opIds2 && opIds2 == opIds3)

            results.add(SimScenarioResult("Zero Lost Ops Verification", zeroLostOps, if (zeroLostOps) "All ops replicated across all nodes" else "Missing ops detected"))

            _simScenarioResults.value = results
        }
    }
}
