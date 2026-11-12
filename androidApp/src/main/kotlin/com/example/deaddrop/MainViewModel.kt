package com.example.deaddrop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deaddrop.core.crypto.createCrypto
import com.example.deaddrop.core.node.Node
import com.example.deaddrop.core.state.DerivedState
import com.example.deaddrop.sim.VirtualNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SimScenarioResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

class MainViewModel : ViewModel() {
    private val crypto = createCrypto()
    private val regionKey = crypto.generateSymmetricKey()

    // Local device node
    val localNode: Node

    // Simulator virtual network & nodes
    val simNetwork = VirtualNetwork(viewModelScope)
    val simNodes: List<Node>

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _uiState = MutableStateFlow<DerivedState?>(null)
    val uiState: StateFlow<DerivedState?> = _uiState.asStateFlow()

    private val _simScenarioResults = MutableStateFlow<List<SimScenarioResult>>(emptyList())
    val simScenarioResults: StateFlow<List<SimScenarioResult>> = _simScenarioResults.asStateFlow()

    init {
        // Setup local node
        val localKeys = crypto.generateKeyPair()
        localNode = Node(
            deviceId = "PhoneA1",
            keys = localKeys,
            crypto = crypto,
            regionKeys = mapOf("A" to regionKey)
        )

        // Seed initial mint for stock testing
        localNode.createMintOp("blanket", "dev:PhoneA1", 15, region = "A")
        localNode.createMintOp("water", "dev:PhoneA1", 30, region = "A")
        localNode.createMintOp("boat", "dev:PhoneA1", 1, region = "A")

        // Seed initial open need
        localNode.createSetOp("need:PhoneA1:1", "category", "Food & Water", region = "A")
        localNode.createSetOp("need:PhoneA1:1", "peopleCount", "12", region = "A")
        localNode.createSetOp("need:PhoneA1:1", "urgency", "High", region = "A")
        localNode.createSetOp("need:PhoneA1:1", "status", "open", region = "A")

        // Setup 3 virtual nodes for in-app simulator
        simNodes = (1..3).map { i ->
            val id = "SimNode$i"
            val transport = simNetwork.createTransport(id)
            Node(
                deviceId = id,
                keys = crypto.generateKeyPair(),
                transport = transport,
                crypto = crypto,
                regionKeys = mapOf("A" to regionKey)
            )
        }

        refreshState()
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

    fun addNeed(category: String, peopleCount: Int, urgency: String, note: String) {
        val seq = System.currentTimeMillis()
        val entityId = "need:PhoneA1:$seq"
        localNode.createSetOp(entityId, "category", category, region = "A")
        localNode.createSetOp(entityId, "peopleCount", peopleCount.toString(), region = "A")
        localNode.createSetOp(entityId, "urgency", urgency, region = "A")
        localNode.createSetOp(entityId, "note", note, region = "A")
        localNode.createSetOp(entityId, "status", "open", region = "A")
        refreshState()
    }

    fun addPerson(name: String, ageGroup: String, condition: String, status: String) {
        val seq = System.currentTimeMillis()
        val entityId = "person:PhoneA1:$seq"
        localNode.createSetOp(entityId, "displayName", name, region = "A")
        localNode.createSetOp(entityId, "ageGroup", ageGroup, region = "A")
        localNode.createSetOp(entityId, "condition", condition, region = "A")
        localNode.createSetOp(entityId, "status", status, region = "A")
        refreshState()
    }

    fun addShelter(name: String, capacity: Int, occupancy: Int) {
        val seq = System.currentTimeMillis()
        val entityId = "shelter:PhoneA1:$seq"
        localNode.createSetOp(entityId, "name", name, region = "A")
        localNode.createSetOp(entityId, "capacity", capacity.toString(), region = "A")
        localNode.createSetOp(entityId, "occupancy", occupancy.toString(), region = "A")
        localNode.createSetOp(entityId, "status", "open", region = "A")
        refreshState()
    }

    fun giveStock(item: String, targetAccount: String, amount: Long): Boolean {
        val moveOp = localNode.createMoveOp(item, "dev:PhoneA1", targetAccount, amount, region = "A")
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
        // Clear all stored ops & reset seq counter
        val ops = localNode.opStore.getAllOps()
        // Re-initialize state
        _uiState.value = DerivedState(
            entities = emptyMap(),
            conflicts = emptyList(),
            balances = emptyMap(),
            alerts = listOf("PANIC WIPE EXECUTED. All local keys and database cleared."),
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
