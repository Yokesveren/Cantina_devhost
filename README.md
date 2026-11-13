<div align="center">

# DeadDrop

### Resilient Offline P2P Mesh Ledger and First-to-Sync Emergency Uplink Gateway

*Disaster coordination that keeps working when the network dies.*

![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![React](https://img.shields.io/badge/React%20%2B%20TypeScript-20232A?logo=react&logoColor=61DAFB)
![Offline first](https://img.shields.io/badge/Offline--first-1D4ED8)
![Signed ops](https://img.shields.io/badge/Ed25519-signed%20operations-15803D)

</div>

---

## Contents

1. [Overview](#1-overview)
2. [Key features](#2-key-features)
3. [System architecture](#3-system-architecture)
4. [How an operation works](#4-how-an-operation-works)
5. [Peer-to-peer sync](#5-peer-to-peer-sync)
6. [Conflicts and scarce items](#6-conflicts-and-scarce-items)
7. [First-to-Sync uplink](#7-first-to-sync-uplink)
8. [Security model](#8-security-model)
9. [Partition and convergence](#9-partition-and-convergence)
10. [Multi-node roles](#10-multi-node-roles)
11. [Tech stack and project structure](#11-tech-stack-and-project-structure)
12. [Build, install and run](#12-build-install-and-run)
13. [Testing and evidence](#13-testing-and-evidence)
14. [Live demo walkthrough](#14-live-demo-walkthrough)
15. [Limitations and roadmap](#15-limitations-and-roadmap)

---

## 1. Overview

### The problem
During floods, earthquakes, conflicts and internet shutdowns, networks fail exactly when people need to coordinate. Rescuers fall back on paper and shouted updates, then try to reconcile everything later. Two teams promise the same boat. Supply counts disagree. A missing person is marked "found" on one phone and overwritten on another.

### The solution
DeadDrop is a coordination platform built for **zero-connectivity environments**.

| Principle | What it means in DeadDrop |
|---|---|
| **Offline first** | Every phone records and reads data locally. No server is required |
| **Actions, not state** | Every change is a small signed record called an *operation* (op). The screen is calculated from the set of ops |
| **Peer-to-peer sync** | Phones exchange only the ops the other is missing when they come near each other |
| **Safe merging** | Combining op sets gives the same result on every phone, in any order, and nothing is overwritten |
| **First-to-Sync uplink** | Whichever phone regains connectivity first pushes everything to the HQ dashboard |

```mermaid
flowchart LR
    P["Network dies"] --> O["Rescuers record offline"]
    O --> M["Phones sync by themselves<br/>when near each other"]
    M --> G["First phone with a connection<br/>uploads for everyone"]
    G --> H["HQ dashboard shows the<br/>full picture live"]
```

---

## 2. Key features

| Feature | Status |
|---|---|
| Offline recording of missing persons, shelter capacity and inventory movements | ✅ Built |
| Cryptographically signed operations (Ed25519) | ✅ Built |
| Per-region payload encryption (AES-GCM) | ✅ Built |
| Automatic device-to-device sync over Google Nearby Connections | ✅ Built |
| Deterministic state rebuilding, conflict detection and state hash | ✅ Built |
| Persistent on-device storage (`ops_store.json`) | ✅ Built |
| Double-spend prevention for scarce items, covered by the simulator suite | ✅ Built |
| First-to-Sync uplink with node telemetry (battery, status, ops held, state hash) | ✅ Built |
| Live HQ web dashboard with green uplink banner and live feeds | ✅ Built |
| Three role-based app flavors (A1, B1, C1) | ✅ Built |
| Multi-node JVM simulator and automated scenario tests | ✅ Built |

### Screenshots

| Field app | HQ dashboard | Uplink banner |
|---|---|---|
| ![Field app]<img width="819" height="1600" alt="image" src="https://github.com/user-attachments/assets/88876a06-fbae-4566-8cc1-6e3aefabc123" />
 | ![HQ dashboard]<img width="1911" height="911" alt="Dashboard" src="https://github.com/user-attachments/assets/38e3248f-5f0a-4b4c-9ce0-49bee9b769c6" />|

> Add the three images to `docs/images/` with these file names.

---

## 3. System architecture

### 3.1 End-to-end view

```mermaid
flowchart LR
    subgraph Field["Field zone: no internet"]
        direction TB
        A["Device A1<br/>Rescuer Arjun"]
        B["Device B1<br/>Rescuer Bhavna"]
        C["Device C1<br/>Lead Charan"]
        A <-->|"Nearby Connections"| B
        B <-->|"Nearby Connections"| C
    end

    subgraph Uplink["First-to-Sync uplink"]
        direction TB
        U["Any phone that gains<br/>a connection<br/>pushLocalUplink()"]
    end

    subgraph HQ["Command HQ"]
        direction TB
        S["Gateway server<br/>/api/uplink"]
        W["Web dashboard<br/>React + TypeScript"]
        S --> W
    end

    A -.-> U
    B -.-> U
    C -.-> U
    U ==> S
```

### 3.2 Inside one phone

```mermaid
flowchart TB
    UI["MainActivity<br/>Jetpack Compose UI"] --> VM["MainViewModel<br/>node state, sync, uplink trigger"]
    VM --> CORE
    subgraph CORE["core (Kotlin Multiplatform)"]
        direction LR
        OP["Op<br/>signed and encrypted"]
        SB["StateBuilder<br/>DerivedState + stateHash"]
        ST["OpStore interface"]
        OP --> SB
        ST --> SB
    end
    VM --> PS["PersistentOpStore<br/>ops_store.json"]
    VM --> NT["NearbyConnectionsTransport"]
    PS -.implements.-> ST
    NT <--> RADIO(["Nearby: Bluetooth and Wi-Fi"])
    VM -->|"when online"| UP["pushLocalUplink()"]
```

### 3.3 Design rules that make it work

```mermaid
mindmap
  root((DeadDrop))
    Store actions
      Append-only ops
      Never edited
      Never deleted
    Merge safely
      Union of op sets
      Same result in any order
      Repeats are harmless
    Prove authorship
      Ed25519 signatures
      Verified before use
    Protect data
      AES-GCM per region
      Local persistence
    Reach HQ
      First phone online
      Uplink gateway
```

---

## 4. How an operation works

### 4.1 Anatomy of an op

```mermaid
classDiagram
    class Op {
        +String opId  "device:counter, e.g. A1:1"
        +Kind kind  "SET, MINT or MOVE"
        +String region
        +bytes encryptedPayload  "AES-GCM"
        +bytes signature  "Ed25519"
    }
    class Kind {
        <<enumeration>>
        SET
        MINT
        MOVE
    }
    Op --> Kind
```

*Simplified for readability. `opId` is the device id plus that device's own counter, so two devices can never create the same id and phone clocks never decide correctness.*

| Kind | Used for |
|---|---|
| `SET` | Recording or changing facts: a missing person, shelter occupancy, a need |
| `MINT` | Creating stock of a scarce item (boats, medkits, blankets) |
| `MOVE` | Moving stock from one holder to another or handing it out |

### 4.2 Life of an op

```mermaid
flowchart TD
    A["Rescuer logs a record<br/>e.g. Saveeta, Missing, Safe"] --> B["Create Op<br/>opId = A1:1"]
    B --> C["Sign with device private key<br/>Ed25519"]
    C --> D["Encrypt payload for the region<br/>AES-GCM"]
    D --> E[("PersistentOpStore<br/>on this phone")]
    E --> F{"Peer nearby?"}
    F -- "No" --> E
    F -- "Yes" --> G["Exchange range sets<br/>who has which ops"]
    G --> H["Send only the missing ops"]
    H --> I{"Signature valid?"}
    I -- "No" --> X["Reject and ignore"]
    I -- "Yes" --> J["Store op on the receiver"]
    J --> K["StateBuilder rebuilds state<br/>conflicts and stateHash"]
    K --> L["Screen updates"]
```

---

## 5. Peer-to-peer sync

### 5.1 The exchange

```mermaid
sequenceDiagram
    autonumber
    participant A as Phone A1 (Arjun)
    participant B as Phone B1 (Bhavna)
    Note over A,B: Phones discover each other and connect (Nearby Connections)
    A->>B: Range set: "I hold A1:1..3"
    B->>A: Range set: "I hold B1:1..3"
    Note over A,B: Each side works out which ops the other is missing
    A->>B: Ops A1:1, A1:2, A1:3
    B->>A: Ops B1:1, B1:2, B1:3
    Note over A,B: Each receiver checks every signature before storing
    A->>A: Store B1:1..3, rebuild state
    B->>B: Store A1:1..3, rebuild state
    Note over A,B: Both phones now hold the same six ops and the same stateHash
```

### 5.2 What each phone knows

| Moment | Phone A1 holds | Phone B1 holds | Same state? |
|---|---|---|---|
| Before meeting | `A1:1, A1:2, A1:3` | `B1:1, B1:2, B1:3` | No |
| After sync | `A1:1..3, B1:1..3` | `A1:1..3, B1:1..3` | **Yes** |

### 5.3 Why only missing ops travel

Phones first swap a small summary (range sets) of what they hold. Only the difference is sent, so a short encounter over a weak link is enough, and sending something twice does no harm.

---

## 6. Conflicts and scarce items

### 6.1 Two people edit the same record while apart

```mermaid
flowchart TD
    S["Person P: status = missing"] --> A["Phone A edits<br/>status = found"]
    S --> B["Phone B edits<br/>status = missing, note added"]
    A --> M["Phones meet and merge op sets"]
    B --> M
    M --> Q{"Both edits live?"}
    Q -- "Yes" --> C["Conflict flagged<br/>both values shown to a human"]
    C --> R["Human picks one<br/>a new op records the decision"]
    R --> Z["All phones converge<br/>history is kept"]
```

Nothing is overwritten. Both versions stay in the log, so **no update is ever lost**.

### 6.2 Scarce items: no double allocation

Scarce items such as boats, medkits and blankets are tracked with `MINT` and `MOVE` operations. A device can only move stock that it holds, so two disconnected teams cannot both hand out the same unit.

```mermaid
flowchart LR
    M["MINT<br/>stock created"] --> H1["Holder A1"]
    H1 -->|"MOVE 2 blankets"| H2["Holder B1"]
    H1 -->|"MOVE 1 blanket"| N["Need: Family at School Road"]
    H1 --> X{"Balance enough?"}
    X -- "No" --> R["Refused locally"]
    X -- "Yes" --> OK["Allowed"]
```

The scenario suite in `sim` covers double-spending prevention (see [Testing](#13-testing-and-evidence)).

---

## 7. First-to-Sync uplink

As soon as **any one phone** gets connectivity (5G, LTE, Wi-Fi, satellite or a local laptop network), it becomes the **Emergency Uplink Gateway**. It sends everything it holds, which includes ops received from the mesh, to Command HQ, along with node telemetry (battery, online status, total ops held) and the consensus state hash.

```mermaid
sequenceDiagram
    autonumber
    participant A as Rescuer A1 (Arjun)
    participant B as Rescuer B1 (Bhavna)
    participant G as Gateway server
    participant W as HQ Web dashboard
    A->>A: Log "Missing Person" (signed offline op)
    A-->>B: P2P sync (Nearby Connections)
    Note over A: Connectivity established<br/>(Wi-Fi, 5G or local LAN)
    A->>G: pushLocalUplink(): ops, battery, status, stateHash
    G->>W: Real-time update
    W->>W: Green banner: "Device A1 (Arjun) first to sync"
    W->>W: Render live ops feed
```

```mermaid
flowchart TD
    S["Phone detects network state"] --> Q{"Connection<br/>available?"}
    Q -- "No" --> W["Keep working offline<br/>keep syncing with nearby phones"]
    Q -- "Yes" --> P["pushLocalUplink()<br/>become the gateway"]
    P --> D["Send ops, telemetry, stateHash"]
    D --> H["HQ dashboard updates live"]
    H --> B["Banner: LOCAL NETWORK UPLINK ESTABLISHED"]
```

Because phones share ops with each other first, **one** connected phone is enough to bring the whole group's data to HQ.

---

## 8. Security model

```mermaid
flowchart LR
    I["Incoming op"] --> V{"Signature valid<br/>for the claimed device?"}
    V -- "No" --> R["Rejected"]
    V -- "Yes" --> D["Decrypt region payload<br/>AES-GCM"]
    D --> S["Store and merge"]
```

| Threat | Defence |
|---|---|
| A forged or altered record | Every op is signed (Ed25519). Receivers verify before using it |
| A stolen or seized phone reading record contents | Op payloads are encrypted per region (AES-GCM) |
| Edits that silently destroy information | Ops are append-only. Conflicts are shown, never overwritten |
| Two teams claiming the same scarce item | Stock moves through `MINT` and `MOVE`, and a holder can only move what it holds |
| Replayed or repeated messages | Op ids are unique (`device:counter`), so duplicates are ignored |

> Keep private keys on the device only. Never commit private keys, secrets or signing material to the repository.

---

## 9. Partition and convergence

Devices repeatedly split and rejoin. After each merge they must hold the same ops and report the same `stateHash`.

```mermaid
flowchart TB
    subgraph T0["1. All connected"]
        direction LR
        a0["A1"] --- b0["B1"] --- c0["C1"]
    end
    subgraph T1["2. Network splits: each side keeps working"]
        direction LR
        a1["A1 edits"] ~~~ b1["B1 edits"] ~~~ c1["C1 edits"]
    end
    subgraph T2["3. Rejoin in any order"]
        direction LR
        a2["A1"] --- b2["B1"]
        b2 --- c2["C1"]
    end
    subgraph T3["4. Converged"]
        direction LR
        a3["same ops"] --- b3["same stateHash"]
    end
    T0 --> T1 --> T2 --> T3
```

```mermaid
xychart-beta
    title "Illustrative only: distinct states across 3 nodes during a split and merge"
    x-axis ["Connected", "Split", "Edits on each side", "Partial rejoin", "Converged"]
    y-axis "Distinct states" 0 --> 4
    line [1, 1, 3, 2, 1]
```

> This chart illustrates the idea and is **not measured data**. Replace it with real numbers from the simulator output (see [Testing](#13-testing-and-evidence)).

---

## 10. Multi-node roles

The Android app has three product flavors, one per emergency field role.

| Flavor | Device ID | Rescuer | Role | Region |
|---|---|---|---|---|
| `deviceA` | `A1` | Arjun | Field Rescuer, North Street | Region A |
| `deviceB` | `B1` | Bhavna | Field Rescuer, South Street | Region A |
| `deviceC` | `C1` | Charan | Group Lead, Base Camp | Region A |

```mermaid
flowchart LR
    A["A1 Arjun<br/>North Street"] <--> B["B1 Bhavna<br/>South Street"]
    B <--> C["C1 Charan<br/>Base Camp (Lead)"]
    A -.-> HQ(("HQ"))
    B -.-> HQ
    C -.-> HQ
```

---

## 11. Tech stack and project structure

| Layer | Technology |
|---|---|
| Shared core | Kotlin Multiplatform: `Op`, `StateBuilder`, `DerivedState`, `OpStore`, `InMemoryOpStore` |
| Mobile app | Android, Jetpack Compose, `MainActivity`, `MainViewModel` |
| Persistence | `PersistentOpStore` (JSON file `ops_store.json`) |
| P2P transport | Google Nearby Connections (`NearbyConnectionsTransport`) |
| Cryptography | Ed25519 signatures, AES-GCM payload encryption |
| Simulator | JVM: `VirtualNetwork`, `ScenarioSuiteTest` |
| HQ dashboard | React, TypeScript, Tailwind CSS |
| Gateway (demo) | Node.js `server.cjs` on a laptop |

```text
DeadDrop/
├── core/            Kotlin Multiplatform: Op, StateBuilder, OpStore
├── androidApp/      Android app (flavors deviceA, deviceB, deviceC)
├── sim/             JVM simulator: VirtualNetwork, ScenarioSuiteTest
├── apks/            Pre-built APKs for the three roles
├── docs/images/     Screenshots used in this README
├── server.cjs       Local gateway server for the demo
└── (web dashboard)  React + TypeScript + Tailwind HQ dashboard
```

---

## 12. Build, install and run

### Prerequisites
- Android Studio (2026.1.3 or newer) and JDK 11 or newer
- Android phones with USB debugging enabled
- Node.js 18 or newer for the dashboard and gateway

### Step 1: Install the three apps

**Pre-built APKs** (direct download):

| Role | APK |
|---|---|
| Device A1, Rescuer Arjun | [`apks/DeadDrop-DeviceA-Arjun.apk`](apks/DeadDrop-DeviceA-Arjun.apk) |
| Device B1, Rescuer Bhavna | [`apks/DeadDrop-DeviceB-Bhavna.apk`](apks/DeadDrop-DeviceB-Bhavna.apk) |
| Device C1, Lead Charan | [`apks/DeadDrop-DeviceC-Charan.apk`](apks/DeadDrop-DeviceC-Charan.apk) |

```bash
adb install apks/DeadDrop-DeviceA-Arjun.apk
adb install apks/DeadDrop-DeviceB-Bhavna.apk
adb install apks/DeadDrop-DeviceC-Charan.apk
```

**From source with Gradle:**

```bash
./gradlew :androidApp:installDeviceADebug :androidApp:installDeviceBDebug :androidApp:installDeviceCDebug
```

**Build only:**

```bash
./gradlew :androidApp:assembleDebug
```

### Step 2: Run the local gateway server (laptop)

<details>
<summary>Show <code>server.cjs</code></summary>

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

</details>

```bash
node server.cjs
```

### Step 3: Run the web dashboard

```bash
npm run dev
```

The dashboard polls `http://localhost:5000/api/uplink` and shows the First-to-Sync banner live.

---

## 13. Testing and evidence

The `sim` module runs several nodes in one JVM over a virtual network you control, then checks the results.

```mermaid
flowchart LR
    S["ScenarioSuiteTest"] --> V["VirtualNetwork<br/>cut and restore links"]
    V --> N["Several simulated nodes<br/>own keys, own store"]
    N --> C["Checks"]
    C --> C1["Same stateHash on all nodes"]
    C --> C2["Every created op present everywhere"]
    C --> C3["No double-spend of scarce items"]
```

| Scenario | What must hold | Result |
|---|---|---|
| Conflicting edits during a split | Both values kept, conflict flagged, hashes equal after rejoin | ⬜ fill in |
| Double-spend attempt on a scarce item | Second spend is refused | ⬜ fill in |
| Three-way split, rejoin in different orders | Same `stateHash` for every merge order | ⬜ fill in |
| Long isolation of one node | Converges after rejoining, no lost ops | ⬜ fill in |
| Duplicate and out-of-order delivery | Same final state | ⬜ fill in |
| Forged or altered op | Rejected | ⬜ fill in |

Run the suite (adjust the module path to match your Gradle setup):

```bash
./gradlew :sim:test
```

### Sync payload evidence

Fill in these numbers from your own measurements. Do not publish figures you did not measure.

| Measurement | Value |
|---|---|
| Bytes sent in one sync after a small change | ⬜ |
| Size of the full op log at that moment | ⬜ |
| Ratio (sent ÷ full) | ⬜ |

---

## 14. Live demo walkthrough

```mermaid
timeline
    title Live demo flow
    section Setup
        Before starting : Three phones with the three flavors installed : Wi-Fi and mobile data off
    section Offline work
        Step 1 : Arjun logs a missing person offline
        Step 2 : Bhavna and Charan log their own records
    section Mesh sync
        Step 3 : Phones meet and sync by themselves : Same ops and same stateHash appear
        Step 4 : Conflicting edit shown as a conflict, nothing lost
    section Uplink
        Step 5 : One phone joins the laptop Wi-Fi or hotspot : Taps Uplink HQ
        Step 6 : Laptop terminal prints the uplink : Dashboard flashes the green banner
```

1. **Simulate offline mesh operations.** Turn off Wi-Fi and mobile data. Open **DeadDrop - Arjun (A1)** and add a missing-person record, for example *Saveeta, Found, Safe*.
2. **First-to-Sync gateway test.** Connect one phone to the laptop Wi-Fi or hotspot. Tap **Uplink HQ** (or let the automatic network sync trigger).
3. **Inspect the results.** The laptop terminal prints:
   `🟢 FIRST LOCAL UPLINK DETECTED FROM PHONE: { gatewayDevice: 'A1', gatewayUser: 'Arjun', opsPushed: 12 }`
   The dashboard flashes:

   > 🟢 **LOCAL NETWORK UPLINK ESTABLISHED**: First-to-Sync Gateway: **Device A1 (Arjun)**, pushed 12 ops to HQ

---

## 15. Limitations and roadmap

### Honest limitations
- Phone radios have a short range. Data crosses distance because people move and phones carry and relay ops, so delivery can be delayed when groups stay far apart.
- The ledger tracks what devices *declare*. It cannot see physical reality, such as two people claiming the same boat.
- Signatures prove who wrote an op and that it was not altered. They do not prove that the author is a legitimate rescuer.
- The local gateway server in this repo is a demo setup, not a hardened production service.

### Roadmap

| Item | Description |
|---|---|
| 🔜 Simpler input | One screen with text or offline voice input, with fields extracted and confirmed before saving |
| 🔜 Fairness alerts | Flag when one rescuer holds far more of a scarce item than the rest |
| 🔜 Longer-range links | Plug a longer-range transport in behind the existing transport layer, with no change to the core |
| 🔜 Local-language support | Offline recognition and UI text in local languages |

---

<div align="center">

**Team:** *add team members here*

Built for a hackathon on resilient offline disaster coordination.

</div>
