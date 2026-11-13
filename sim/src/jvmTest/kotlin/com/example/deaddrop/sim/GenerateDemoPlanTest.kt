package com.example.deaddrop.sim

import com.example.deaddrop.core.crypto.KeyPair
import com.example.deaddrop.core.crypto.createCrypto
import com.example.deaddrop.core.model.*
import com.example.deaddrop.core.node.Node
import com.example.deaddrop.core.state.StateBuilder
import com.example.deaddrop.core.store.InMemoryOpStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
@Serializable
data class DemoPlanJson(
    val authorityPublicKey: String,
    val authorityPrivateKey: String,
    val regionKeyA: String,
    val devices: List<DemoDeviceJson>,
    val genesisOps: List<Op>
)

@OptIn(ExperimentalEncodingApi::class)
@Serializable
data class DemoDeviceJson(
    val id: String,
    val userName: String,
    val role: String,
    val region: String,
    val publicKey: String,
    val privateKey: String
)

class GenerateDemoPlanTest {

    private val crypto = createCrypto()

    private fun createKeyFromSeed(seedStr: String): KeyPair {
        val seedBytes = seedStr.padStart(32, '0').take(32).encodeToByteArray()
        val privParams = Ed25519PrivateKeyParameters(seedBytes, 0)
        val pubParams = privParams.generatePublicKey()
        return KeyPair(publicKey = pubParams.encoded, privateKey = privParams.encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun generateGenesisPlan() {
        val authorityKeys = createKeyFromSeed("DeadDropAuthoritySeed_32Bytes!!")
        val devA1Keys = createKeyFromSeed("DeadDropDeviceA1Seed_32Bytes!!!")
        val devB1Keys = createKeyFromSeed("DeadDropDeviceB1Seed_32Bytes!!!")
        val devC1Keys = createKeyFromSeed("DeadDropDeviceC1Seed_32Bytes!!!")
        val regionAKey = "DeadDropRegionAKey_32BytesLong!!".encodeToByteArray()

        val genesisOps = mutableListOf<Op>()
        var seq = 1L

        fun addOp(kind: Kind, bodyBytes: ByteArray, priority: Int = 1) {
            val encryptedBody = crypto.encrypt(regionAKey, bodyBytes)
            val unsigned = Op(
                dev = "Authority",
                seq = seq++,
                kind = kind,
                region = "A",
                priority = priority,
                hlc = 0L,
                body = encryptedBody,
                sig = ByteArray(0)
            )
            val sig = crypto.sign(authorityKeys.privateKey, unsigned.signaturePayload())
            genesisOps.add(unsigned.copy(sig = sig))
        }

        // 1. Authority Mints
        addOp(Kind.MINT, BodySerializer.encodeMintBody(MintBody("blanket", "pool:A", 20)), priority = 0)
        addOp(Kind.MINT, BodySerializer.encodeMintBody(MintBody("water", "pool:A", 60)), priority = 0)
        addOp(Kind.MINT, BodySerializer.encodeMintBody(MintBody("boat", "pool:A", 1)), priority = 0)

        // 2. Initial Escrow Allocations
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("blanket", "pool:A", "dev:A1", 5)), priority = 1)
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("blanket", "pool:A", "dev:B1", 5)), priority = 1)
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("water", "pool:A", "dev:A1", 15)), priority = 1)
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("water", "pool:A", "dev:B1", 15)), priority = 1)
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("water", "pool:A", "dev:C1", 10)), priority = 1)
        addOp(Kind.MOVE, BodySerializer.encodeMoveBody(MoveBody("boat", "pool:A", "dev:C1", 1)), priority = 1)

        // 3. Initial Field Entities
        // Missing Person: person:genesis:1
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "displayName", "Ravi")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "ageGroup", "Adult")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "status", "Missing")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "condition", "Unknown")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "whereNow", "Old Bridge North")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("person:genesis:1", "note", "Last seen near river embankment")), priority = 1)

        // Active Shelter: shelter:genesis:1
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("shelter:genesis:1", "name", "Community Hall")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("shelter:genesis:1", "locationText", "Main Road")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("shelter:genesis:1", "capacity", "80")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("shelter:genesis:1", "occupancy", "12")), priority = 1)
        addOp(Kind.SET, BodySerializer.encodeSetBody(SetBody("shelter:genesis:1", "status", "Open")), priority = 1)

        // Verify signatures and derived state
        val store = InMemoryOpStore()
        for (op in genesisOps) {
            val valid = crypto.verify(authorityKeys.publicKey, op.signaturePayload(), op.sig)
            assertTrue(valid, "Op signature must be valid")
            store.addOp(op)
        }

        val state = StateBuilder.build(store.getAllOps()) { region, ciphertext ->
            crypto.decrypt(regionAKey, ciphertext)
        }

        assertEquals(5L, state.balances["dev:A1"]?.get("blanket"))
        assertEquals(15L, state.balances["dev:A1"]?.get("water"))
        assertEquals(5L, state.balances["dev:B1"]?.get("blanket"))
        assertEquals(15L, state.balances["dev:B1"]?.get("water"))
        assertEquals(10L, state.balances["dev:C1"]?.get("water"))
        assertEquals(1L, state.balances["dev:C1"]?.get("boat"))
        assertEquals("Ravi", state.entities["person:genesis:1"]?.fields?.get("displayName"))
        assertEquals("Community Hall", state.entities["shelter:genesis:1"]?.fields?.get("name"))

        val demoPlan = DemoPlanJson(
            authorityPublicKey = Base64.encode(authorityKeys.publicKey),
            authorityPrivateKey = Base64.encode(authorityKeys.privateKey),
            regionKeyA = Base64.encode(regionAKey),
            devices = listOf(
                DemoDeviceJson("A1", "Arjun", "Field Rescuer · North Street", "A", Base64.encode(devA1Keys.publicKey), Base64.encode(devA1Keys.privateKey)),
                DemoDeviceJson("B1", "Bhavna", "Field Rescuer · South Street", "A", Base64.encode(devB1Keys.publicKey), Base64.encode(devB1Keys.privateKey)),
                DemoDeviceJson("C1", "Charan", "Group Lead · Base Camp", "A", Base64.encode(devC1Keys.publicKey), Base64.encode(devC1Keys.privateKey))
            ),
            genesisOps = genesisOps
        )

        val jsonString = Json { prettyPrint = true }.encodeToString(demoPlan)
        val targetFile = java.io.File("../androidApp/src/main/assets/demo_plan.json")
        targetFile.parentFile.mkdirs()
        targetFile.writeText(jsonString)
        println("Generated demo_plan.json successfully at ${targetFile.absolutePath}, initial stateHash=${state.stateHash}")
    }
}
