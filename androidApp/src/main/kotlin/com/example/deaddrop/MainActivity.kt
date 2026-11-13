package com.example.deaddrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deaddrop.BuildConfig
import com.example.deaddrop.core.model.Op
import com.example.deaddrop.core.state.Conflict
import com.example.deaddrop.core.state.DerivedState
import com.example.deaddrop.core.state.EntityState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNearbyPermissions()

        setContent {
            DeadDropExactTheme {
                MainAppScreen(viewModel)
            }
        }
    }

    private fun requestNearbyPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_SCAN)
            perms.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            perms.add("android.permission.NEARBY_WIFI_DEVICES")
        }
        requestPermissionsLauncher.launch(perms.toTypedArray())
    }
}

// Color Palette
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
val StateGreen = Color(0xFF2E7D32)
val StateAmber = Color(0xFFF57C00)

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
    val connectedPeers by viewModel.nearbyTransport.peers.collectAsState()
    val isRadioOn by viewModel.nearbyTransport.isRadioOn.collectAsState()
    val bytesSent by viewModel.nearbyTransport.totalBytesSent.collectAsState()
    val bytesReceived by viewModel.nearbyTransport.totalBytesReceived.collectAsState()

    var showAddNeedDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showAddShelterDialog by remember { mutableStateOf(false) }
    var showStockDialog by remember { mutableStateOf(false) }
    var showConflictsScreen by remember { mutableStateOf(false) }
    var showSimulatorScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<EntityState?>(null) }

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
            ops = viewModel.persistentOpStore.getAllOps(),
            onResetToGenesis = { viewModel.resetDemoToGenesis() },
            onPanicWipe = { viewModel.panicWipe() },
            onBack = { showSettingsScreen = false }
        )
        return
    }

    val conflictCount = uiState?.conflicts?.size ?: 0
    val totalOpsCount = viewModel.persistentOpStore.getAllOps().size
    val localOpsCount = viewModel.persistentOpStore.getOpsForDevice(BuildConfig.NODE_DEVICE_ID).size
    val isStateAmber = connectedPeers.isEmpty() && localOpsCount > 0 && totalOpsCount > viewModel.demoPlan.genesisOps.size
    val hashColor = if (isStateAmber) StateAmber else StateGreen

    Scaffold(
        bottomBar = {
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
            // 1. Top Status Bar
            Column(
                modifier = Modifier.fillMaxWidth().background(DarkHeaderBg).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DEADDROP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MintPillBg
                            ) {
                                Text(
                                    "${BuildConfig.NODE_DEVICE_ID} · ${BuildConfig.NODE_USER_NAME}",
                                    color = DarkNavyText,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            BuildConfig.NODE_ROLE,
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp).clickable { showSettingsScreen = true },
                            shape = CircleShape,
                            color = Color(0xFF08202E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚙️", fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hashPrefix = (uiState?.stateHash ?: "INITIAL").take(6)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = hashColor
                        ) {
                            Text(
                                "Hash: $hashPrefix",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val totalBytes = bytesSent + bytesReceived
                        val bytesFormatted = if (totalBytes > 1024) "${totalBytes / 1024} KB" else "$totalBytes B"
                        Text(
                            "${connectedPeers.size} nearby · $bytesFormatted",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isRadioOn) "P2P: ON " else "P2P: OFF ", color = if (isRadioOn) MintTagText else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isRadioOn,
                            onCheckedChange = { viewModel.toggleRadio(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MintPillBg,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                if (conflictCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showConflictsScreen = true },
                        shape = RoundedCornerShape(8.dp),
                        color = EmergencyRed
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("[!] $conflictCount CONFLICT DETECTED IN FIELD EDITS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text("RESOLVE NOW →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // 2. Body
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)
                ) {
                    item {
                        Text("NAMASTE, ${BuildConfig.NODE_USER_NAME.uppercase()}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = DarkNavyText, letterSpacing = 1.sp)
                        Text("Offline Mesh Node (${BuildConfig.NODE_DEVICE_ID}) · Zero Cloud Dependency", fontSize = 13.sp, color = LightSubtext, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = HeroBannerBg
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text("REGION A · ESCROW WALLET ACTIVE", color = MintTagText, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Field Disaster Coordination", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Tap any record below to edit offline with dropdown choices or custom text. Encrypted ops sync automatically over Bluetooth & Wi-Fi Direct on contact.",
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.clickable { showAddNeedDialog = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("REGISTER NEED", color = MintTagText, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                                    Text("  →", color = MintTagText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "👤+",
                                label = "NEED",
                                onClick = { showAddNeedDialog = true }
                            )
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "🎫",
                                label = "SUPPLY & STOCK",
                                onClick = { showStockDialog = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GridTile(
                                modifier = Modifier.weight(1f),
                                icon = "▲",
                                label = "ADD SHELTER",
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
                        Text("Active Field Records:", fontWeight = FontWeight.Black, fontSize = 18.sp, color = DarkNavyText)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    val entities = uiState?.entities ?: emptyMap()
                    val personEntities = entities.values.filter { it.type == "person" }
                    val shelterEntities = entities.values.filter { it.type == "shelter" }

                    if (personEntities.isEmpty() && shelterEntities.isEmpty()) {
                        item {
                            Text("No field entities present.", color = LightSubtext, fontSize = 13.sp)
                        }
                    } else {
                        items(personEntities.toList()) { person ->
                            PersonRecordCard(
                                entity = person,
                                onEdit = { editingEntity = person }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        items(shelterEntities.toList()) { shelter ->
                            ShelterRecordCard(entity = shelter)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth().border(1.5.dp, CardBorderGray, RoundedCornerShape(16.dp)).clickable { showConflictsScreen = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
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
                                            Text("✚", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Open Records & Conflicts", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkNavyText)
                                        Text("${entities.size} active records · $conflictCount conflicts", fontSize = 12.sp, color = LightSubtext)
                                    }
                                }
                                Text("→", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LightSubtext)
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    if (editingEntity != null) {
        val targetEntity = editingEntity!!
        EditPersonDialog(
            entity = targetEntity,
            onDismiss = { editingEntity = null },
            onSaveFields = { updates ->
                viewModel.editEntityFields(targetEntity.id, updates)
                editingEntity = null
            }
        )
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
            onConfirm = { name, age, cond, stat, where, note ->
                viewModel.addPerson(name, age, cond, stat, where, note)
                showAddPersonDialog = false
            }
        )
    }

    if (showAddShelterDialog) {
        AddShelterDialog(
            onDismiss = { showAddShelterDialog = false },
            onConfirm = { name, cap, occ, loc ->
                viewModel.addShelter(name, cap, occ, loc)
                showAddShelterDialog = false
            }
        )
    }

    if (showStockDialog) {
        StockDialog(
            uiState = uiState,
            localDeviceId = BuildConfig.NODE_DEVICE_ID,
            onDismiss = { showStockDialog = false },
            onGiveStock = { item, target, amt -> viewModel.giveStock(item, target, amt) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownChoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontWeight = FontWeight.Medium) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PersonRecordCard(entity: EntityState, onEdit: () -> Unit) {
    val name = entity.fields["displayName"] ?: "Unknown"
    val status = entity.fields["status"] ?: "Missing"
    val condition = entity.fields["condition"] ?: "Unknown"
    val whereNow = entity.fields["whereNow"] ?: "Unknown"
    val note = entity.fields["note"] ?: ""
    val hasConflict = entity.rawLiveFields.values.any { it.size > 1 }

    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, if (hasConflict) EmergencyRed else CardBorderGray, RoundedCornerShape(16.dp)).clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        color = TileCardBg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋 ", fontSize = 16.sp)
                    Text(name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = DarkNavyText)
                }

                if (hasConflict) {
                    Surface(shape = RoundedCornerShape(6.dp), color = EmergencyRed) {
                        Text("[!] CONFLICT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(shape = RoundedCornerShape(6.dp), color = MintPillBg) {
                        Text(status.uppercase(), color = DarkNavyText, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Condition: $condition", fontSize = 12.sp, color = DarkNavyText, fontWeight = FontWeight.Medium)
                Text("Location: $whereNow", fontSize = 12.sp, color = LightSubtext)
            }
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Note: $note", fontSize = 11.sp, color = LightSubtext)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Tap to edit record →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTealIcon)
            }
        }
    }
}

@Composable
fun ShelterRecordCard(entity: EntityState) {
    val name = entity.fields["name"] ?: "Shelter"
    val cap = entity.fields["capacity"] ?: "0"
    val occ = entity.fields["occupancy"] ?: "0"
    val loc = entity.fields["locationText"] ?: ""

    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, CardBorderGray, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = TileCardBg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▲ ", fontSize = 16.sp, color = DarkTealIcon)
                    Text(name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = DarkNavyText)
                }
                Text("Cap: $occ / $cap", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkTealIcon)
            }
            if (loc.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Location: $loc", fontSize = 12.sp, color = LightSubtext)
            }
        }
    }
}

@Composable
fun EditPersonDialog(
    entity: EntityState,
    onDismiss: () -> Unit,
    onSaveFields: (updates: List<FieldUpdate>) -> Unit
) {
    val name = entity.fields["displayName"] ?: ""
    var status by remember { mutableStateOf(entity.fields["status"] ?: "Missing") }
    var condition by remember { mutableStateOf(entity.fields["condition"] ?: "Unknown") }
    var whereNow by remember { mutableStateOf(entity.fields["whereNow"] ?: "") }
    var note by remember { mutableStateOf(entity.fields["note"] ?: "") }

    val statusOptions = listOf("Missing", "Found", "Safe", "Evacuated", "Hospitalized")
    val conditionOptions = listOf("Unknown", "Safe", "Injured", "Critical", "Deceased")
    val locationOptions = listOf("School Rooftop", "Old Bridge North", "Community Hall", "Main Shelter", "Base Camp")
    val noteOptions = listOf("Last seen near river embankment", "Needs immediate first aid", "Reunited with family", "Requires food & water", "No medical issues")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Record: $name", fontWeight = FontWeight.Black) },
        text = {
            Column {
                DropdownChoiceTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = "Status",
                    options = statusOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = "Condition",
                    options = conditionOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = whereNow,
                    onValueChange = { whereNow = it },
                    label = "Location (whereNow)",
                    options = locationOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Note",
                    options = noteOptions
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val updates = mutableListOf<FieldUpdate>()

                val currentStatusOpIds = entity.rawLiveFields["status"]?.map { it.opId } ?: emptyList()
                val currentCondOpIds = entity.rawLiveFields["condition"]?.map { it.opId } ?: emptyList()
                val currentWhereOpIds = entity.rawLiveFields["whereNow"]?.map { it.opId } ?: emptyList()
                val currentNoteOpIds = entity.rawLiveFields["note"]?.map { it.opId } ?: emptyList()

                if (status != entity.fields["status"]) updates.add(FieldUpdate("status", status, currentStatusOpIds))
                if (condition != entity.fields["condition"]) updates.add(FieldUpdate("condition", condition, currentCondOpIds))
                if (whereNow != entity.fields["whereNow"]) updates.add(FieldUpdate("whereNow", whereNow, currentWhereOpIds))
                if (note != entity.fields["note"]) updates.add(FieldUpdate("note", note, currentNoteOpIds))

                if (updates.isNotEmpty()) {
                    onSaveFields(updates)
                } else {
                    onDismiss()
                }
            }) {
                Text("Save All Updates", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun GridTile(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(100.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = TileCardBg
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 26.sp, color = DarkTealIcon, fontWeight = FontWeight.Bold)
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = DarkNavyText, letterSpacing = 0.5.sp)
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
        Text("Offline Field Persona (${BuildConfig.NODE_DEVICE_ID})", fontSize = 14.sp, color = Color(0xFF90A4AE))
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
    localDeviceId: String,
    onDismiss: () -> Unit,
    onGiveStock: (item: String, target: String, amount: Long) -> Boolean
) {
    var selectedItem by remember { mutableStateOf("blanket") }
    var giveAmount by remember { mutableStateOf("1") }

    val myAccount = "dev:$localDeviceId"
    val myShare = uiState?.balances?.get(myAccount) ?: emptyMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supply & Escrow Shares ($localDeviceId)", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("My Personal Share ($localDeviceId):", fontWeight = FontWeight.Bold, color = DarkNavyText)
                Spacer(modifier = Modifier.height(6.dp))

                val blanketCount = myShare["blanket"] ?: 0L
                val waterCount = myShare["water"] ?: 0L
                val boatCount = myShare["boat"] ?: 0L

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Blanket", color = DarkNavyText)
                    Text("$blanketCount / 5 left", fontWeight = FontWeight.Bold, color = if (blanketCount > 0) StateGreen else Color.Red)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Water (L)", color = DarkNavyText)
                    Text("$waterCount left", fontWeight = FontWeight.Bold, color = if (waterCount > 0) StateGreen else Color.Red)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rescue Boat #1", color = DarkNavyText)
                    val boatLabel = if (boatCount > 0) "1 (Held by Charan · C1)" else "0 (Held by Charan · C1)"
                    Text(boatLabel, fontWeight = FontWeight.Bold, color = if (boatCount > 0) StateGreen else Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (localDeviceId == "C1" && boatCount > 0) {
                    Button(
                        onClick = {
                            onGiveStock("boat", "given:rescue", 1)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkHeaderBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ASSIGN RESCUE BOAT #1", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (localDeviceId != "C1") {
                    Text("Rescue Boat #1: Locked (Held by Charan · C1)", fontSize = 11.sp, color = LightSubtext, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text("Transfer Stock Item:", fontSize = 12.sp, color = LightSubtext)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = selectedItem == "blanket", onClick = { selectedItem = "blanket" }, label = { Text("Blanket") })
                    FilterChip(selected = selectedItem == "water", onClick = { selectedItem = "water" }, label = { Text("Water") })
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = giveAmount,
                    onValueChange = { giveAmount = it },
                    label = { Text("Amount to Give") }
                )
            }
        },
        confirmButton = {
            val mySelectedCount = myShare[selectedItem] ?: 0L
            val canGive = mySelectedCount > 0
            Button(
                onClick = {
                    val amt = giveAmount.toLongOrNull() ?: 1L
                    if (onGiveStock(selectedItem, "given:need1", amt)) {
                        onDismiss()
                    }
                },
                enabled = canGive
            ) {
                Text(if (canGive) "Give 1 ${selectedItem.uppercase()}" else "No share left on $localDeviceId", fontWeight = FontWeight.Bold)
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
                        Text("All nodes in convergence!", color = StateGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                                Text("\"${fieldVal.value}\"", fontWeight = FontWeight.Bold, color = DarkNavyText, fontSize = 16.sp)
                                                Text("Edited by device ${fieldVal.dev} (op ${fieldVal.opId})", fontSize = 11.sp, color = LightSubtext)
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
                Text("EXECUTE ALL TEST SCENARIOS", fontWeight = FontWeight.Black, color = Color.White)
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
                            Text("Hash: $hashPrefix", fontSize = 11.sp, color = StateGreen, fontWeight = FontWeight.Bold)
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
                                    Text(res.name, fontWeight = FontWeight.Bold, color = if (res.passed) StateGreen else EmergencyRed, fontSize = 15.sp)
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
fun SettingsScreen(
    ops: List<Op>,
    onResetToGenesis: () -> Unit,
    onPanicWipe: () -> Unit,
    onBack: () -> Unit
) {
    var showPanicConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(DarkHeaderBg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("← ", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { onBack() })
                Text("Settings & Security Inspector", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorderGray, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = TileCardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cryptographic Key Protocol", fontWeight = FontWeight.Bold, color = DarkNavyText, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ed25519 signatures active per op. Region A AES-GCM 256-bit payload encryption enforced.", fontSize = 12.sp, color = LightSubtext)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Encrypted Database Inspector (${ops.size} Ops):", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkNavyText)
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, CardBorderGray, RoundedCornerShape(12.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ops) { op ->
                    val bodyHex = op.body.joinToString("") { "%02x".format(it) }.take(24) + "..."
                    val sigHex = op.sig.joinToString("") { "%02x".format(it) }.take(16) + "..."
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TileCardBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ID: ${op.id} (${op.kind})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavyText)
                                Text("Prio: ${op.priority} | Reg: ${op.region}", fontSize = 11.sp, color = LightSubtext)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Encrypted Body: $bodyHex", fontSize = 10.sp, color = EmergencyRed, fontWeight = FontWeight.SemiBold)
                            Text("Signature: $sigHex", fontSize = 10.sp, color = LightSubtext)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkHeaderBg),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RESET TO GENESIS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }

                Button(
                    onClick = { showPanicConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🚨 PANIC WIPE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Demo to Genesis Stage 1?") },
            text = { Text("This will clear any local edits on this phone and restore the green genesis state hash.") },
            confirmButton = {
                Button(onClick = {
                    onResetToGenesis()
                    showResetConfirm = false
                    onBack()
                }) {
                    Text("CONFIRM RESET", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
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
            dismissButton = { TextButton(onClick = { showPanicConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AddNeedDialog(onDismiss: () -> Unit, onConfirm: (category: String, count: Int, urgency: String, note: String) -> Unit) {
    var category by remember { mutableStateOf("Food & Water") }
    var countText by remember { mutableStateOf("4") }
    var urgency by remember { mutableStateOf("High") }

    val categoryOptions = listOf("Food & Water", "Medical Supplies", "Blankets & Clothing", "Rescue Boat", "Shelter Space")
    val urgencyOptions = listOf("Low", "Normal", "High", "Critical")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Need", fontWeight = FontWeight.Black) },
        text = {
            Column {
                DropdownChoiceTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = "Category",
                    options = categoryOptions
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = countText, onValueChange = { countText = it }, label = { Text("People Count") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                DropdownChoiceTextField(
                    value = urgency,
                    onValueChange = { urgency = it },
                    label = "Urgency",
                    options = urgencyOptions
                )
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
fun AddPersonDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, age: String, condition: String, status: String, whereNow: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Injured") }
    var whereNow by remember { mutableStateOf("School Rooftop") }
    var note by remember { mutableStateOf("") }

    val conditionOptions = listOf("Unknown", "Safe", "Injured", "Critical", "Deceased")
    val locationOptions = listOf("School Rooftop", "Old Bridge North", "Community Hall", "Main Shelter", "Base Camp")
    val noteOptions = listOf("Last seen near river embankment", "Needs immediate first aid", "Reunited with family", "Requires food & water", "No medical issues")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Person Found/Missing", fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = "Condition",
                    options = conditionOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = whereNow,
                    onValueChange = { whereNow = it },
                    label = "Location (whereNow)",
                    options = locationOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Note",
                    options = noteOptions
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, "Adult", condition, "Found", whereNow, note)
            }) { Text("Confirm & Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddShelterDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, cap: Int, occ: Int, loc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var capText by remember { mutableStateOf("100") }
    var occText by remember { mutableStateOf("25") }
    var locText by remember { mutableStateOf("Main Road") }

    val nameOptions = listOf("School Shelter", "Community Hall", "Sports Complex", "Base Camp Hall")
    val locationOptions = listOf("Main Road", "North Street", "South Street", "High Ground Area")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Log Shelter", fontWeight = FontWeight.Black) },
        text = {
            Column {
                DropdownChoiceTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Shelter Name",
                    options = nameOptions
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = capText, onValueChange = { capText = it }, label = { Text("Capacity") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = occText, onValueChange = { occText = it }, label = { Text("Current Occupancy") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                DropdownChoiceTextField(
                    value = locText,
                    onValueChange = { locText = it },
                    label = "Location Text",
                    options = locationOptions
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(name, capText.toIntOrNull() ?: 100, occText.toIntOrNull() ?: 0, locText)
            }) { Text("Confirm & Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
