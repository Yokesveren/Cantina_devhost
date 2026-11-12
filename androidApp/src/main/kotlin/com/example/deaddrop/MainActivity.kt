package com.example.deaddrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deaddrop.core.state.Conflict
import com.example.deaddrop.core.state.DerivedState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeadDropExactTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

// Exact Color Palette from attached UI reference
val DarkHeaderBg = Color(0xFF092232)
val MintPillBg = Color(0xFFA0E8D5)
val DarkNavyText = Color(0xFF08202E)
val LightSubtext = Color(0xFF5F6D7A)
val HeroBannerBg = Color(0xFF08202E)
val MintTagText = Color(0xFF53E0BC)
val TileCardBg = Color(0xFFF0F4F8)
val DarkTealIcon = Color(0xFF0B4F4A)
val EmergencyRed = Color(0xFFC62828)
val CardBorderGray = Color(0xFFE2E8F0)

@Composable
fun DeadDropExactTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DarkNavyText,
            secondary = MintPillBg,
            error = EmergencyRed,
            background = DarkHeaderBg,
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = DarkNavyText
        ),
        content = content
    )
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val simResults by viewModel.simScenarioResults.collectAsState()

    var showAddNeedDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showAddShelterDialog by remember { mutableStateOf(false) }
    var showStockDialog by remember { mutableStateOf(false) }
    var showConflictsScreen by remember { mutableStateOf(false) }
    var showSimulatorScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    if (!isUnlocked) {
        UnlockScreen(onUnlock = { pin -> viewModel.unlock(pin) })
        return
    }

    if (showConflictsScreen) {
        ConflictsScreen(
            conflicts = uiState?.conflicts ?: emptyList(),
            onResolve = { entityId, field, chosenVal, replaces ->
                viewModel.resolveConflict(entityId, field, chosenVal, replaces)
            },
            onBack = { showConflictsScreen = false }
        )
        return
    }

    if (showSimulatorScreen) {
        SimulatorScreen(
            simNodes = viewModel.simNodes,
            simResults = simResults,
            onRunScenarios = { viewModel.runSimAllScenarios() },
            onBack = { showSimulatorScreen = false }
        )
        return
    }

    if (showSettingsScreen) {
        SettingsScreen(
            onPanicWipe = { viewModel.panicWipe() },
            onBack = { showSettingsScreen = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            // Emergency Red Full Width Bar
            Surface(
                modifier = Modifier.fillMaxWidth().height(60.dp).clickable { showSimulatorScreen = true },
                color = EmergencyRed
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("◆ ", color = Color.White, fontSize = 18.sp)
                    Text("EMERGENCY / आपातकालीन", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().background(DarkHeaderBg)
        ) {
            // 1. Top Bar Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF00695C)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⌂", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("DEADDROP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.sp)
                }

                Surface(
                    modifier = Modifier.clickable { showSettingsScreen = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MintPillBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔊 ", fontSize = 14.sp)
                        Text("LISTEN", color = DarkNavyText, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }

            // 2. White Sheet Body
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    item {
                        // Title
                        Text("NAMASTE", fontSize = 38.sp, fontWeight = FontWeight.Black, color = DarkNavyText, letterSpacing = 1.sp)
                        Text("Offline Disaster Coordination Portal", fontSize = 15.sp, color = LightSubtext, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(20.dp))

                        // 3. Dark Featured Hero Banner Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = HeroBannerBg
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("OFFLINE · 2 PEERS NEARBY", color = MintTagText, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sync & coordinate on this phone.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "A few taps per record. Works fully without internet or server. Data merges automatically.",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.clickable { showAddNeedDialog = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("REGISTER NEED", color = MintTagText, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.5.sp)
                                    Text("  →", color = MintTagText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 4. 2x2 Light Gray Action Tiles Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "👤+",
                                label = "NEED",
                                onClick = { showAddNeedDialog = true }
                            )
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "🎫",
                                label = "SUPPLY",
                                onClick = { showStockDialog = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "▲",
                                label = "SHELTER",
                                onClick = { showAddShelterDialog = true }
                            )
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "📋",
                                label = "PERSON FOUND",
                                onClick = { showAddPersonDialog = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. Bottom Info Card
                        val conflictCount = uiState?.conflicts?.size ?: 0
                        val entityCount = uiState?.entities?.size ?: 0

                        Surface(
                            modifier = Modifier.fillMaxWidth().border(1.5.dp, CardBorderGray, RoundedCornerShape(18.dp)).clickable { showConflictsScreen = true },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF00695C)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("✚", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Open Records & Conflicts", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkNavyText)
                                        Text("$entityCount active records · $conflictCount conflicts", fontSize = 13.sp, color = LightSubtext)
                                    }
                                }
                                Text("→", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LightSubtext)
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    if (showAddNeedDialog) {
        AddNeedDialog(
            onDismiss = { showAddNeedDialog = false },
            onConfirm = { cat, count, urg, note ->
                viewModel.addNeed(cat, count, urg, note)
                showAddNeedDialog = false
            }
        )
    }

    if (showAddPersonDialog) {
        AddPersonDialog(
            onDismiss = { showAddPersonDialog = false },
            onConfirm = { name, age, cond, stat ->
                viewModel.addPerson(name, age, cond, stat)
                showAddPersonDialog = false
            }
        )
    }

    if (showAddShelterDialog) {
        AddShelterDialog(
            onDismiss = { showAddShelterDialog = false },
            onConfirm = { name, cap, occ ->
                viewModel.addShelter(name, cap, occ)
                showAddShelterDialog = false
            }
        )
    }

    if (showStockDialog) {
        StockDialog(
            uiState = uiState,
            onDismiss = { showStockDialog = false },
            onGiveStock = { item, target, amt -> viewModel.giveStock(item, target, amt) }
        )
    }
}

@Composable
fun GridTile(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = TileCardBg
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 28.sp, color = DarkTealIcon, fontWeight = FontWeight.Bold)
            Text(label, fontWeight = FontWeight.Black, fontSize = 15.sp, color = DarkNavyText, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun UnlockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkHeaderBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            color = HeroBannerBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🔒", fontSize = 42.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("DEAD DROP", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
        Text("Offline Disaster Coordination Portal", fontSize = 14.sp, color = Color(0xFF90A4AE))
        Spacer(modifier = Modifier.height(36.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Enter Passcode") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                if (!onUnlock(pin)) {
                    errorMsg = "Invalid Passcode. Try 1234."
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MintPillBg),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("UNLOCK", fontSize = 18.sp, fontWeight = FontWeight.Black, color = DarkNavyText)
        }
    }
}

@Composable
fun StockDialog(
    uiState: DerivedState?,
    onDismiss: () -> Unit,
    onGiveStock: (item: String, target: String, amount: Long) -> Boolean
) {
    var selectedItem by remember { mutableStateOf("blanket") }
    var giveAmount by remember { mutableStateOf("1") }
    val myShare = uiState?.balances?.get("dev:PhoneA1") ?: emptyMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supply & Escrow Shares", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("My Personal Share:", fontWeight = FontWeight.Bold, color = DarkNavyText)
                Spacer(modifier = Modifier.height(8.dp))
                for (item in listOf("blanket", "water", "medkit", "boat")) {
                    val count = myShare[item] ?: 0L
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.replaceFirstChar { it.uppercase() }, color = DarkNavyText)
                        Text("$count left", fontWeight = FontWeight.Bold, color = if (count > 0) Color(0xFF2E7D32) else Color.Red)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transfer Item:", fontSize = 13.sp, color = LightSubtext)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = selectedItem == "blanket", onClick = { selectedItem = "blanket" }, label = { Text("Blanket") })
                    FilterChip(selected = selectedItem == "water", onClick = { selectedItem = "water" }, label = { Text("Water") })
                    FilterChip(selected = selectedItem == "boat", onClick = { selectedItem = "boat" }, label = { Text("Boat") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = giveAmount,
                    onValueChange = { giveAmount = it },
                    label = { Text("Amount to Give") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = giveAmount.toLongOrNull() ?: 1L
                onGiveStock(selectedItem, "given:need1", amt)
                onDismiss()
            }) {
                Text("Confirm Transfer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ConflictsScreen(
    conflicts: List<Conflict>,
    onResolve: (entityId: String, field: String, chosenValue: String, replaces: List<String>) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(DarkHeaderBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("← ", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { onBack() })
                Text("Active Field Conflicts", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White).padding(16.dp)) {
            if (conflicts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TileCardBg)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All nodes in convergence!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("No conflicting field edits present across peers.", color = LightSubtext, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(conflicts) { conflict ->
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, EmergencyRed, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Conflict: ${conflict.entityId} -> ${conflict.field}", fontWeight = FontWeight.Bold, color = EmergencyRed, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                for (fieldVal in conflict.liveValues) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = TileCardBg),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(fieldVal.value, fontWeight = FontWeight.Bold, color = DarkNavyText, fontSize = 16.sp)
                                                Text("From ${fieldVal.dev} (op ${fieldVal.opId})", fontSize = 11.sp, color = LightSubtext)
                                            }
                                            Button(
                                                onClick = {
                                                    val allOpIds = conflict.liveValues.map { it.opId }
                                                    onResolve(conflict.entityId, conflict.field, fieldVal.value, allOpIds)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DarkHeaderBg),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Use This Value", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatorScreen(
    simNodes: List<com.example.deaddrop.core.node.Node>,
    simResults: List<SimScenarioResult>,
    onRunScenarios: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(DarkHeaderBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("← ", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { onBack() })
                Text("Network Simulator & Scenarios", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White).padding(16.dp)) {
            Button(
                onClick = onRunScenarios,
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("EXECUTE ALL 13 TEST SCENARIOS", fontWeight = FontWeight.Black, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Virtual Peer Nodes:", fontSize = 13.sp, color = LightSubtext, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (node in simNodes) {
                    Card(
                        modifier = Modifier.weight(1f).border(1.dp, CardBorderGray, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = TileCardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(node.deviceId, fontWeight = FontWeight.Bold, color = DarkNavyText, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val hashPrefix = node.stateHash().take(8)
                            Text("Hash: $hashPrefix", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("Ops: ${node.opStore.getAllOps().size}", fontSize = 11.sp, color = LightSubtext)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Test Suite Scenarios Output:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavyText)
            Spacer(modifier = Modifier.height(8.dp))

            if (simResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Tap button above to execute headless partition & split scenarios.", color = LightSubtext, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(simResults) { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (res.passed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (res.passed) "✅ " else "❌ ", fontSize = 20.sp)
                                Column {
                                    Text(res.name, fontWeight = FontWeight.Bold, color = if (res.passed) Color(0xFF2E7D32) else EmergencyRed, fontSize = 15.sp)
                                    Text(res.details, fontSize = 12.sp, color = DarkNavyText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onPanicWipe: () -> Unit, onBack: () -> Unit) {
    var showPanicConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(DarkHeaderBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("← ", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { onBack() })
                Text("Settings & Security", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorderGray, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = TileCardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Security Protocol", fontWeight = FontWeight.Bold, color = DarkNavyText, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ed25519 digital signatures active. AES-GCM region key encryption enforced at rest.", fontSize = 13.sp, color = LightSubtext)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { showPanicConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("🚨 PANIC WIPE ALL DATA", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }

    if (showPanicConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicConfirm = false },
            title = { Text("⚠️ Confirm Panic Wipe?") },
            text = { Text("This will immediately destroy all local cryptographic keys and delete all offline records from this phone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onPanicWipe()
                        showPanicConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("YES, WIPE EVERYTHING", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AddNeedDialog(onDismiss: () -> Unit, onConfirm: (category: String, count: Int, urgency: String, note: String) -> Unit) {
    var category by remember { mutableStateOf("Food & Water") }
    var countText by remember { mutableStateOf("4") }
    var urgency by remember { mutableStateOf("High") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Need", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, shape = RoundedCornerShape(10.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = countText, onValueChange = { countText = it }, label = { Text("People Count") }, shape = RoundedCornerShape(10.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = urgency, onValueChange = { urgency = it }, label = { Text("Urgency (Low/Normal/High)") }, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(category, countText.toIntOrNull() ?: 1, urgency, "")
            }) { Text("Confirm & Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPersonDialog(onDismiss: () -> Unit, onConfirm: (name: String, age: String, condition: String, status: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Injured") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Person Found/Missing", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, shape = RoundedCornerShape(10.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("Condition (Ok/Injured/Critical)") }, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, "Adult", condition, "Found")
            }) { Text("Confirm & Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddShelterDialog(onDismiss: () -> Unit, onConfirm: (name: String, cap: Int, occ: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var capText by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Shelter", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shelter Name") }, shape = RoundedCornerShape(10.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = capText, onValueChange = { capText = it }, label = { Text("Capacity") }, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, capText.toIntOrNull() ?: 50, 10)
            }) { Text("Confirm & Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
