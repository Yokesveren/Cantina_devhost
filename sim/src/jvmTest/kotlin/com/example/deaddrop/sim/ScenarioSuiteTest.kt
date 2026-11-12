package com.example.deaddrop.sim

import com.example.deaddrop.core.crypto.createCrypto
import com.example.deaddrop.core.model.Kind
import com.example.deaddrop.core.model.Op
import com.example.deaddrop.core.node.Node
import com.example.deaddrop.core.state.StateBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioSuiteTest {

    private val crypto = createCrypto()
    private val regionKey = crypto.generateSymmetricKey()

    private fun setupTestNodes(count: Int): Pair<VirtualNetwork, List<Node>> {
        val network = VirtualNetwork()
        val nodes = (1..count).map { i ->
            val id = "Node$i"
            val keys = crypto.generateKeyPair()
            val transport = network.createTransport(id)
            Node(
                deviceId = id,
                keys = keys,
                transport = transport,
                crypto = crypto,
                regionKeys = mapOf("A" to regionKey)
            )
        }
        return Pair(network, nodes)
    }

    private suspend fun syncAll(nodes: List<Node>) {
        for (i in nodes.indices) {
            for (j in nodes.indices) {
                if (i != j) {
                    nodes[i].startSyncWith(nodes[j].deviceId)
                }
            }
        }
        delay(100) // allow async coroutines to process messages
    }

    @Test
    fun test01_ConcurrentSameFieldEdit() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (nodeA, nodeB) = nodes

        // 1. Split A and B
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, true)

        // 2. Both edit person:1.status differently
        nodeA.createSetOp("person:1", "status", "injured", region = "A")
        nodeB.createSetOp("person:1", "status", "critical", region = "A")

        // 3. Rejoin
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, false)
        syncAll(nodes)

        // 4. Assertions: Hashes match, conflict present with both values
        assertEquals(nodeA.stateHash(), nodeB.stateHash())
        val stateA = nodeA.getDerivedState()
        assertEquals(1, stateA.conflicts.size)
        assertEquals("person:1", stateA.conflicts.first().entityId)
        assertEquals("status", stateA.conflicts.first().field)
        val liveVals = stateA.conflicts.first().liveValues.map { it.value }.toSet()
        assertEquals(setOf("injured", "critical"), liveVals)
    }

    @Test
    fun test02_DoubleAllocation() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (nodeA, nodeB) = nodes

        // Mint 10 blankets to dev:Node1
        nodeA.createMintOp("blanket", "dev:${nodeA.deviceId}", 10, region = "A")
        syncAll(nodes)

        // Split A and B
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, true)

        // Node A moves 10 blankets to given:need1
        val move1 = nodeA.createMoveOp("blanket", "dev:${nodeA.deviceId}", "given:need1", 10, region = "A")
        assertNotNull(move1)

        // Node B tries to spend Node B's share (dev:Node2) which has 0 balance
        val move2 = nodeB.createMoveOp("blanket", "dev:${nodeB.deviceId}", "given:need2", 10, region = "A")
        assertNull(move2) // Refused!

        // Rejoin and sync
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, false)
        syncAll(nodes)

        assertEquals(nodeA.stateHash(), nodeB.stateHash())
        val state = nodeA.getDerivedState()
        assertEquals(0L, state.balances["dev:Node1"]?.get("blanket") ?: 0L)
        assertEquals(10L, state.balances["given:need1"]?.get("blanket") ?: 0L)
        assertTrue(state.alerts.isEmpty(), "No integrity alerts for valid escrow")
    }

    @Test
    fun test03_BoatHandoff() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (nodeA, nodeB) = nodes

        // Mint 1 unique boat to dev:Node1
        nodeA.createMintOp("boat", "dev:${nodeA.deviceId}", 1, region = "A")
        syncAll(nodes)

        // Node B tries to move boat from dev:Node1 (Node B doesn't own dev:Node1)
        val moveByB = nodeB.createMoveOp("boat", "dev:${nodeA.deviceId}", "given:need1", 1, region = "A")
        assertNull(moveByB) // Refused!

        // Node A transfers boat to dev:Node2
        val moveByA = nodeA.createMoveOp("boat", "dev:${nodeA.deviceId}", "dev:${nodeB.deviceId}", 1, region = "A")
        assertNotNull(moveByA)
        syncAll(nodes)

        // Now Node B owns boat in dev:Node2 and moves it to given:need1
        val moveByB2 = nodeB.createMoveOp("boat", "dev:${nodeB.deviceId}", "given:need1", 1, region = "A")
        assertNotNull(moveByB2)
        syncAll(nodes)

        assertEquals(nodeA.stateHash(), nodeB.stateHash())
        val state = nodeA.getDerivedState()
        assertEquals(1L, state.balances["given:need1"]?.get("boat") ?: 0L)
    }

    @Test
    fun test04_DeleteVsEdit() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (nodeA, nodeB) = nodes

        // Create initial need op
        val initialOp = nodeA.createSetOp("need:1", "status", "open", region = "A")
        syncAll(nodes)

        // Split A and B
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, true)

        // A closes need (replaces initialOp.id)
        nodeA.createSetOp("need:1", "status", "closed", replaces = listOf(initialOp.id), region = "A")
        // B edits need status (replaces initialOp.id)
        nodeB.createSetOp("need:1", "status", "assigned", replaces = listOf(initialOp.id), region = "A")

        // Rejoin
        network.setLinkCut(nodeA.deviceId, nodeB.deviceId, false)
        syncAll(nodes)

        assertEquals(nodeA.stateHash(), nodeB.stateHash())
        val state = nodeA.getDerivedState()
        assertEquals(1, state.conflicts.size)
        val vals = state.conflicts.first().liveValues.map { it.value }.toSet()
        assertEquals(setOf("closed", "assigned"), vals)
    }

    @Test
    fun test05_ThreeWaySplitAndRejoin() = runBlocking {
        val (network, nodes) = setupTestNodes(3)
        val (n1, n2, n3) = nodes

        // 3-way split
        network.cutAllLinks()

        n1.createSetOp("shelter:1", "name", "Shelter Alpha", region = "A")
        n2.createSetOp("shelter:2", "name", "Shelter Beta", region = "A")
        n3.createSetOp("shelter:3", "name", "Shelter Gamma", region = "A")

        // Merge 1 & 2 first
        network.setLinkCut(n1.deviceId, n2.deviceId, false)
        syncAll(listOf(n1, n2))

        // Then merge 3
        network.restoreAllLinks()
        syncAll(nodes)

        assertEquals(n1.stateHash(), n2.stateHash())
        assertEquals(n2.stateHash(), n3.stateHash())
        assertEquals(3, n1.getDerivedState().entities.size)
    }

    @Test
    fun test06_LongIsolation() = runBlocking {
        val (network, nodes) = setupTestNodes(3)
        val (n1, n2, n3) = nodes

        // Isolate n3
        network.setLinkCut(n3.deviceId, n1.deviceId, true)
        network.setLinkCut(n3.deviceId, n2.deviceId, true)

        // n1 & n2 perform 50 ops
        for (i in 1..25) {
            n1.createSetOp("need:$i", "status", "open", region = "A")
            n2.createSetOp("person:$i", "status", "found", region = "A")
        }
        syncAll(listOf(n1, n2))

        // Rejoin n3 after long isolation
        network.restoreAllLinks()
        syncAll(nodes)

        assertEquals(n1.stateHash(), n3.stateHash())
        val allOpIdsCreated = (n1.opStore.getAllOps() + n2.opStore.getAllOps() + n3.opStore.getAllOps()).map { it.id }.toSet()
        val n3OpIds = n3.opStore.getAllOps().map { it.id }.toSet()
        assertEquals(allOpIdsCreated, n3OpIds, "Zero lost ops after long isolation")
    }

    @Test
    fun test07_FlappingLinks() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (n1, n2) = nodes

        n1.createSetOp("need:flapping", "note", "urgent help", region = "A")

        // Connect and disconnect repeatedly
        for (i in 1..5) {
            network.setLinkCut(n1.deviceId, n2.deviceId, false)
            syncAll(nodes)
            network.setLinkCut(n1.deviceId, n2.deviceId, true)
        }

        network.setLinkCut(n1.deviceId, n2.deviceId, false)
        syncAll(nodes)

        assertEquals(n1.stateHash(), n2.stateHash())
        assertEquals(1, n2.opStore.getAllOps().size, "No duplicate ops stored")
    }

    @Test
    fun test08_DuplicateAndReorder() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (n1, n2) = nodes

        val op1 = n1.createSetOp("need:seq", "step", "1", region = "A")
        val op2 = n1.createSetOp("need:seq", "step", "2", region = "A")

        // Add op2 first, then op1, then duplicate op2 to n2 manually
        n2.verifyAndAddOp(op2)
        n2.verifyAndAddOp(op1)
        n2.verifyAndAddOp(op2) // duplicate

        assertEquals(n1.stateHash(), n2.stateHash())
    }

    @Test
    fun test09_ForgedOp() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (n1, n2) = nodes

        val forgedKeys = crypto.generateKeyPair()
        val forgedOp = Op(
            dev = "ForgedDev",
            seq = 1,
            kind = Kind.SET,
            region = "A",
            priority = 1,
            hlc = 100,
            body = "forged".encodeToByteArray(),
            sig = crypto.sign(forgedKeys.privateKey, "invalid".encodeToByteArray())
        )

        val n2Restricted = Node(
            deviceId = "Node2Restricted",
            keys = n2.keys,
            crypto = crypto,
            allowedPublicKeys = setOf(n1.keys.publicKey)
        )

        val accepted = n2Restricted.verifyAndAddOp(forgedOp)
        assertFalse(accepted, "Forged op rejected")
    }

    @Test
    fun test10_Equivocation() = runBlocking {
        val ops = listOf(
            Op("dev1", 1, Kind.SET, "A", 1, 10, "body1".encodeToByteArray(), ByteArray(0)),
            Op("dev1", 1, Kind.SET, "A", 1, 10, "body2_different".encodeToByteArray(), ByteArray(0)) // Equivocation!
        )

        val derivedState = StateBuilder.build(ops)
        assertTrue(derivedState.alerts.any { it.contains("Equivocation detected on device dev1") })
    }

    @Test
    fun test11_CrossRegionRequest() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (regionLeadA, regionLeadB) = nodes

        // Mint 50 water to region pool pool:A
        regionLeadA.createMintOp("water", "pool:A", 50, region = "A")
        syncAll(nodes)

        // Lead A sends 20 water to region pool pool:B
        val moveOp = regionLeadA.createMoveOp("water", "pool:A", "pool:B", 20, region = "A")
        assertNotNull(moveOp)

        // Link cuts mid-flight
        network.setLinkCut(regionLeadA.deviceId, regionLeadB.deviceId, true)

        // Lead A's balance for pool:A is reduced to 30
        val stateA = regionLeadA.getDerivedState()
        assertEquals(30L, stateA.balances["pool:A"]?.get("water"))

        // Rejoin link and sync
        network.setLinkCut(regionLeadA.deviceId, regionLeadB.deviceId, false)
        syncAll(nodes)

        val stateB = regionLeadB.getDerivedState()
        assertEquals(20L, stateB.balances["pool:B"]?.get("water"))
        assertEquals(regionLeadA.stateHash(), regionLeadB.stateHash())
    }

    @Test
    fun test12_PayloadSize() = runBlocking {
        val (network, nodes) = setupTestNodes(2)
        val (n1, n2) = nodes

        for (i in 1..10) {
            n1.createSetOp("need:$i", "status", "open", region = "A")
        }

        syncAll(nodes)
        val initialBytesSent = n1.bytesSent.value

        n1.createSetOp("need:11", "status", "open", region = "A")
        syncAll(nodes)
        val deltaBytesSent = n1.bytesSent.value - initialBytesSent

        assertTrue(deltaBytesSent < initialBytesSent, "Syncing 1 op sends far fewer bytes than initial 10 ops sync")
    }

    @Test
    fun test13_GlobalAssertionsSuite() = runBlocking {
        val (network, nodes) = setupTestNodes(3)

        nodes[0].createMintOp("medkit", "dev:Node1", 20, region = "A")
        syncAll(nodes)

        nodes[0].createMoveOp("medkit", "dev:Node1", "dev:Node2", 5, region = "A")
        nodes[1].createSetOp("person:10", "condition", "injured", region = "A")
        nodes[2].createSetOp("shelter:5", "capacity", "100", region = "A")

        syncAll(nodes)

        val hash1 = nodes[0].stateHash()
        val hash2 = nodes[1].stateHash()
        val hash3 = nodes[2].stateHash()
        assertEquals(hash1, hash2)
        assertEquals(hash2, hash3)

        val n1Ops = nodes[0].opStore.getAllOps().map { it.id }.toSet()
        val n2Ops = nodes[1].opStore.getAllOps().map { it.id }.toSet()
        val n3Ops = nodes[2].opStore.getAllOps().map { it.id }.toSet()
        assertEquals(n1Ops, n2Ops)
        assertEquals(n2Ops, n3Ops)

        val state = nodes[0].getDerivedState()
        var totalInAccounts = 0L
        for ((_, items) in state.balances) {
            val count = items["medkit"] ?: 0L
            totalInAccounts += count
        }
        assertEquals(20L, totalInAccounts, "Total medkit stock conserved")
        assertTrue(state.alerts.isEmpty(), "No unexpected integrity alerts")
    }
}
