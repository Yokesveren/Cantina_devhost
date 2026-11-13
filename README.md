# DeadDrop: Resilient Offline P2P Mesh Ledger & First-to-Sync Emergency Uplink Gateway

DeadDrop is an emergency disaster relief coordination platform designed for zero-connectivity environments (e.g. floods, earthquakes, cellular blackouts). Field rescuers log missing person reports, shelter capacities, and critical inventory movements using cryptographically signed operations. 

Devices automatically synchronize peer-to-peer (P2P) in the field over Bluetooth and Wi-Fi Direct. **Whichever mobile app regains connectivity first acts as the Emergency Uplink Gateway**, pushing all accumulated local and mesh operations to the central Command HQ Web Dashboard.

---

## 📱 Pre-Built APK Downloads (Direct Installation)

You can download and install the pre-built Android APKs directly for all 3 node roles:

* 📥 **[DeadDrop Device A1 - Rescuer Arjun](file:///C:/Users/Yokesveren/AndroidStudioProjects/DeadDrop/apks/DeadDrop-DeviceA-Arjun.apk)** (`./apks/DeadDrop-DeviceA-Arjun.apk`)
* 📥 **[DeadDrop Device B1 - Rescuer Bhavna](file:///C:/Users/Yokesveren/AndroidStudioProjects/DeadDrop/apks/DeadDrop-DeviceB-Bhavna.apk)** (`./apks/DeadDrop-DeviceB-Bhavna.apk`)
* 📥 **[DeadDrop Device C1 - Lead Charan](file:///C:/Users/Yokesveren/AndroidStudioProjects/DeadDrop/apks/DeadDrop-DeviceC-Charan.apk)** (`./apks/DeadDrop-DeviceC-Charan.apk`)

---

## 1. Executive Summary & Problem Statement

### Core Challenge
During disasters, infrastructure fails and mobile internet goes offline. Field rescuers (`dev:A1`, `dev:B1`, `dev:C1`) must collect high-priority information—such as missing persons, shelter occupancy, and scarce supplies (boats, medkits, blankets)—without relying on cloud servers.

### Solution & Key Requirement
1. **Offline Peer-to-Peer Mesh**: Operations (`SET`, `MINT`, `MOVE`) are cryptographically signed via Ed25519, encrypted per region (AES-GCM), and propagated device-to-device when field workers pass near each other using Google Nearby Connections.
2. **First-to-Sync Uplink Protocol**: As soon as **any single mobile app** establishes internet or local network connectivity (5G, LTE, Wi-Fi, or Satellite link), it acts as the gateway. It immediately pushes all local and mesh operation logs, node telemetry (battery, online status, total ops held), and consensus hashes to the central Web HQ Dashboard.

---

## 2. End-to-End Architecture & Data Flow

```mermaid
graph TD
    subgraph Phase 1: Zero-Cloud P2P Mesh Zone (Offline)
        A[Device A1 - Rescuer Arjun] <-->|Google Nearby Connections / P2P| B[Device B1 - Rescuer Bhavna]
        B <-->|Google Nearby Connections / P2P| C[Device C1 - Lead Charan]
    end

    subgraph Phase 2: First-to-Sync Network Gateway Trigger
        A -- "1st App to Gain Connectivity" --> Gateway[Local / Cloud Gateway REST & Firebase API]
    end

    subgraph Phase 3: Central Command HQ Web Dashboard
        Gateway <-->|Real-Time WebSockets / REST| WB[DeadDrop HQ Web Dashboard]
        WB --> Banner[🟢 LIVE UPLINK ESTABLISHED BANNER]
        WB --> Nodes[Rescuer Telemetry & Battery Grid]
        WB --> Ops[Live Missing Persons & Inventory Feed]
    end
```

---

## 3. End-to-End Workflow

```text
[ Rescuer A1 (Arjun) ]     [ Rescuer B1 (Bhavna) ]     [ Local / Cloud Gateway ]     [ HQ Web Dashboard ]
         |                            |                            |                            |
         |--- Log "Missing Person" -->|                            |                            |
         |    (Offline Ed25519 Op)    |                            |                            |
         |                            |                            |                            |
         |<======= P2P Sync =========>|                            |                            |
         |    (Bluetooth / Wi-Fi)     |                            |                            |
         |                            |                            |                            |
         |-- Connectivity Established |                            |                            |
         |   (Wi-Fi / 5G / Local LAN) |                            |                            |
         |                            |                            |                            |
         |=========================== pushLocalUplink() ==========>|                            |
         |                            (First-to-Sync Gateway)      |                            |
         |                                                         |====== Real-time Update ===>|
         |                                                         |                            |
                                                                                                |-- Flash Green Banner:
                                                                                                |   "Device A1 (Arjun)
                                                                                                |    First to Sync!"
                                                                                                |-- Render Live Ops Feed
```

### Detailed Workflow Steps:
1. **Offline Operation Creation**:
   - Field worker Arjun (`A1`) logs a missing person report (*"Saveeta, Missing, Condition: Safe"*).
   - An immutable `Op` is created: `opId` = `A1:1`, signed with Arjun's Ed25519 private key and encrypted for Region A (AES-GCM).

2. **Automated P2P Mesh Sync**:
   - When Arjun meets Bhavna (`B1`), their devices exchange range sets via Google Nearby Connections.
   - Bhavna's node receives `A1:1` and appends it to her local `PersistentOpStore`.

3. **First-to-Sync Gateway Activation**:
   - When Arjun or Bhavna enters a zone with network connection (Wi-Fi / 5G or local laptop LAN), their app detects network state.
   - The app invokes `pushLocalUplink()`, declaring itself as the active **Uplink Gateway**.
   - It posts all ops, battery levels, node status, and state hash to the Gateway endpoint (`/api/uplink` / `/ops`).

4. **Real-Time HQ Dashboard Display**:
   - The Web Command Center receives the payload.
   - A high-visibility alert banner flashes:
     > 🟢 **LOCAL NETWORK UPLINK ESTABLISHED** — First-to-Sync Gateway: **Device A1 (Arjun)** — Pushed 12 ops to HQ
   - Missing person lists, shelter occupancies, and supply balances update live on screen without page refresh.

---

## 4. Multi-Node App Flavors (`androidApp`)

The Android application is configured with 3 distinct product flavors representing emergency field roles:

| Product Flavor | Device ID | Rescuer Name | Role | Region |
| :--- | :--- | :--- | :--- | :--- |
| `deviceA` | `A1` | Arjun | Field Rescuer · North Street | Region A |
| `deviceB` | `B1` | Bhavna | Field Rescuer · South Street | Region A |
| `deviceC` | `C1` | Charan | Group Lead · Base Camp | Region A |

---

## 5. Technical Stack & Key Modules

* **`core` (Kotlin Multiplatform)**:
  * `Op`: Cryptographic operation data class (`SET`, `MINT`, `MOVE`) with `opId` (`dev:seq`), Ed25519 signatures, and AES-GCM payload encryption.
  * `StateBuilder` & `DerivedState`: Deterministic state reconciliation, conflict detection, and `stateHash` calculation.
  * `OpStore` & `InMemoryOpStore`: In-memory ledger store.
* **`androidApp` (Android / Jetpack Compose)**:
  * `PersistentOpStore`: Disk persistence using JSON storage (`ops_store.json`).
  * `NearbyConnectionsTransport`: Google Nearby Connections P2P transport.
  * `MainViewModel`: Manages node state, P2P sync, and uplink triggers (`pushLocalUplink()`).
  * `MainActivity`: Tactical emergency dark UI built with Jetpack Compose.
* **`sim` (JVM Simulator)**:
  * `VirtualNetwork` & `ScenarioSuiteTest`: Automated simulation suite testing double-spending prevention and multi-node P2P convergence.
* **Web Dashboard**:
  * React + TypeScript + Tailwind CSS web dashboard listening to real-time uplink endpoints.

---

## 6. Build, Install & Demo Instructions

### Prerequisites
* Android Studio (2026.1.3+) / JDK 11+
* Connected Android device (with USB Debugging enabled)
* Node.js (v18+) for local web dashboard server

---

### Step 1: Install the 3 Mobile Apps

Option A: Install using Gradle (from source):
```bash
./gradlew :androidApp:installDeviceADebug :androidApp:installDeviceBDebug :androidApp:installDeviceCDebug
```

Option B: Direct ADB install using pre-built APKs:
```bash
adb install apks/DeadDrop-DeviceA-Arjun.apk
adb install apks/DeadDrop-DeviceB-Bhavna.apk
adb install apks/DeadDrop-DeviceC-Charan.apk
```

Option C: Compile from source:
```bash
./gradlew :androidApp:assembleDebug
```

---

### Step 2: Run the Local Gateway Server (Laptop)

Create `server.cjs` on your laptop and run:
```bash
node server.cjs
```

`server.cjs`:
```javascript
const http = require('http');
let latestUplink = null;

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') { res.writeHead(200); res.end(); return; }

  if (req.method === 'POST' && req.url === '/api/uplink') {
    let body = '';
    req.on('data', chunk => { body += chunk.toString(); });
    req.on('end', () => {
      latestUplink = JSON.parse(body);
      console.log('🟢 FIRST LOCAL UPLINK DETECTED FROM PHONE:', latestUplink);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
    });
  } else if (req.method === 'GET' && req.url === '/api/uplink') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(latestUplink || {}));
  }
});

server.listen(5000, '0.0.0.0', () => {
  console.log('🚀 Local Uplink Server listening on http://0.0.0.0:5000');
});
```

---

### Step 3: Run the Web Dashboard

Start the React Web Dashboard on your laptop:
```bash
npm run dev
```

The Web Dashboard listens to `http://localhost:5000/api/uplink` and renders the **First-to-Sync Gateway Banner** live when mobile data/Wi-Fi connects!

---

## 7. Verification & Live Demo Walkthrough

1. **Simulate Offline Mesh Operations**:
   - Turn off Wi-Fi/Data on your phone.
   - Open **DeadDrop - Arjun (A1)** and add a missing person record ("*Saveeta - Found - Safe*").
2. **First-to-Sync Gateway Test**:
   - Connect phone to laptop Wi-Fi / Hotspot.
   - Tap **"Uplink HQ 🌐"** (or trigger automatic network sync).
3. **Inspect Results**:
   - Laptop terminal prints:
     `🟢 FIRST LOCAL UPLINK DETECTED FROM PHONE: { gatewayDevice: 'A1', gatewayUser: 'Arjun', opsPushed: 12 }`
   - The Web Dashboard immediately flashes the green alert banner:
     > 🟢 **LOCAL NETWORK UPLINK ESTABLISHED** — First-to-Sync Gateway: **Device A1 (Arjun)** — Pushed 12 ops to HQ
