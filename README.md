# Flagsmith A/B Testing POC — Carousel Feature Flag

A self-contained Java proof-of-concept demonstrating:

- **Multivariate feature flags** for A/B testing (carousel visible vs. hidden)
- **90/10 traffic split** — 90% control (no carousel), 10% test (show carousel)
- **50 simulated users** each with a name and a deterministic `deviceId`
- **20 identified users** who also have a `userId`, with **identity overrides** to preserve their A/B bucket across both identities

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17 or later (tested on Java 21) |
| Maven | 3.8+ |
| Flagsmith account | Free tier at [app.flagsmith.com](https://app.flagsmith.com) |

---

## Step 1 — Set Up Flagsmith

### 1.1 Create a Project & Environment

1. Sign up / log in at **https://app.flagsmith.com**
2. Create a new **Project** (e.g. `Carousel POC`)
3. Note the **Development** environment that is created automatically

### 1.2 Create the Multivariate Feature Flag

1. Go to **Features → Create Feature**
2. Set the flag key to exactly: `carousel_ab_test`
3. Under **Value**, enter: `control` (this is the default / control variant)
4. Enable the flag (toggle it ON)
5. Click **Create Feature**

### 1.3 Add the Multivariate Options (A/B variants)

1. Click on `carousel_ab_test` to open the flag editor
2. Click **+ Add Multivariate Option**
3. Add the following option:

| Option String Value | % Allocation |
|---------------------|-------------|
| `test`              | **10%**     |

4. The control value (`control`) receives the remaining **90%** automatically
5. Click **Update Feature**

Your flag should now look like:

```
carousel_ab_test
  ├── control   90%   (default value)
  └── test      10%   (multivariate option)
```

### 1.4 (Optional) Configure Segments for Identity Overrides

For the identity override mechanism (Phase 2) to reliably lock a `userId`
identity to the same bucket as its `deviceId`, create a segment:

1. Go to **Segments → Create Segment**
2. Name it `override_test_bucket`
3. Rule: **Trait** `bucket_override` **equals** `test`
4. Save the segment

Then on the `carousel_ab_test` flag:

1. Click **+ Add Segment Override**
2. Select `override_test_bucket`
3. Set the value to `test` and enable it

Repeat for `override_control_bucket` → value `control`.

This guarantees that even if Flagsmith's hashing assigns a different bucket
to the `userId` identity, the `bucket_override` trait forces the correct value.

### 1.5 Copy Your API Key

1. Go to **Settings → Keys** in your environment
2. Copy the **Client-side Environment Key** (starts with `ser.`)

---

## Step 2 — Configure the POC

Edit `src/main/resources/config.properties`:

```properties
flagsmith.api.key=ser.YOUR_ACTUAL_KEY_HERE
```

Or pass it at runtime:

```bash
java -Dflagsmith.api.key=ser.YOUR_KEY -jar target/flagsmith-poc-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Or set an environment variable:

```bash
export FLAGSMITH_API_KEY=ser.YOUR_KEY
mvn exec:java -Dexec.mainClass=com.poc.flagsmith.FlagsmithPoc
```

---

## Step 3 — Build & Run

```bash
# From the project root
mvn clean package -q

java -jar target/flagsmith-poc-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## What Happens at Runtime

### Phase 1 — All 50 users evaluated by `deviceId`

Each user is sent to Flagsmith as an **identity** keyed by their `deviceId`.
Flagsmith uses a consistent hashing algorithm (MurmurHash) on the identity key
to allocate users to variants. Because the `deviceId` is deterministic (derived
from the user's name via UUID.nameUUIDFromBytes), results are **repeatable** across
runs.

Expected output:
```
[Alice_1           ] deviceId=device-<uuid>  → ⬜ control
[Bob_2             ] deviceId=device-<uuid>  → ⬜ control
[Carol_3           ] deviceId=device-<uuid>  → 🔵 test
...
Totals → control: ~45 | test: ~5
```

### Phase 2 — userId identity overrides for 20 identified users

For each of the 20 users who have a `userId`:

1. Their `deviceId` variant is read from Phase 1
2. The `userId` identity is registered in Flagsmith with a `bucket_override`
   trait set to that variant
3. The `carousel_ab_test` flag is evaluated for the `userId` identity
4. The POC checks whether both identities landed in the same bucket

```
[Alice_1           ]
  deviceId : device-<uuid>  → ⬜ control
  userId   : user-<uuid>    → ⬜ control
  Bucket preserved? ✅ YES
```

---

## Architecture

```
FlagsmithPoc.java          ← entry point, orchestrates phases
  |── ConfigService.java   ← Manages config
  ├── UserFactory.java      ← generates 50 deterministic users
  ├── FlagsmithService.java ← wraps the Flagsmith Java SDK
  │     ├── getVariantForDevice()       Phase 1
  │     ├── getVariantForIdentifiedUser() Phase 2 (read)
  │     └── applyUserIdOverride()       Phase 2 (write + read)
  ├── FlagVariant.java      ← enum: CONTROL | TEST | DISABLED
  └── ResultSummary.java    ← collects & prints the final report
  
FlagsmithDeleteIdentities.java  ← Clean up script to delete all identities.
```

---

## Identity Override — Technical Deep Dive

Flagsmith uses **consistent hashing** (MurmurHash on the identity key + flag ID)
to assign each identity to a multivariate bucket.  Two different identity keys
(e.g. `device-abc` and `user-xyz`) will almost certainly hash to different buckets.

To preserve the bucket across identity transitions:

### Option A — Traits + Segment Rules (implemented in this POC)

Store the device's bucket as a trait on the userId identity:
```
bucket_override = "control"   # or "test"
```
Then create a **segment override** in Flagsmith that matches on this trait
and forces the correct flag value.  This works on **all tiers**.

### Option B — Management API (hard override, paid plans)

Use the Flagsmith REST Management API to pin an identity to a specific variant:

```http
POST /api/v1/environments/{env_key}/identities/{identity}/overrides/
{
  "feature": "carousel_ab_test",
  "feature_state_value": "test",
  "enabled": true
}
```

This creates a **hard identity-level override** that ignores hashing entirely.
See [Flagsmith docs](https://docs.flagsmith.com/clients/rest#identity-overrides).

### Option C — Unified identity (best practice for new projects)

If possible, always use `userId` as the primary identity key and pass
`deviceId` as a trait.  This avoids the mismatch problem entirely.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| All users get `disabled` | Flag is OFF in Flagsmith | Enable the flag in the dashboard |
| All users get `control` | Multivariate option not configured | Add the `test` option with 10% allocation |
| `bucket_override` trait not taking effect | Segment override not configured | Follow Step 1.4 above |
| `FlagsmithClientError` | Wrong API key | Check `config.properties` |
| Everyone in `test` bucket | Allocation percentages inverted | Set `test` = 10%, `control` = 90% |

---

## Project Structure

```
flagsmith-poc/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/poc/flagsmith/
        │   ├── ConfigService.java                 # Handles Flagsmith config
        │   ├── FlagsmithDeleteIdentities.java     # Clean up script
        │   ├── FlagsmithPoc.java                  # main()
        │   ├── FlagsmithService.java              # SDK wrapper
        │   ├── User.java                          # user model
        │   ├── UserFactory.java                   # generates 50 users
        │   ├── FlagVariant.java                   # CONTROL / TEST / DISABLED
        │   └── ResultSummary.java                 # report formatting
        └── resources/
            ├── config.properties                  # ← put your API key here
            └── logback.xml
```
