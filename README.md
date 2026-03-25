# Flagsmith A/B Testing POC — Carousel Feature Flag

A self-contained Java proof-of-concept demonstrating:

- **Multivariate feature flags** for A/B testing (carousel visible vs. hidden)
- **Segment-controlled experiment enrolment** via a `Carousel_Cohort` trait — only identities carrying this trait enter the experiment
- **90/10 traffic split** applied within the segment — 90% control (no carousel), 10% test (show carousel)
- **50 simulated users** each with a name and a deterministic `deviceId`
- **20 identified users** who also have a `userId`, with **identity overrides** that preserve their A/B bucket when transitioning from `deviceId` to `userId` identity

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
3. Under **Value**, enter: `control` (this is the default value for users outside the experiment)
4. Enable the flag (toggle it ON)
5. Click **Create Feature**

Then open the flag editor and add one multivariate option:

| Option String Value | % Allocation |
|---------------------|-------------|
| `test`              | **10%**     |

The control value (`control`) receives the remaining **90%** automatically. Click **Update Feature**.

Your flag's default configuration should now look like:

```
carousel_ab_test  (default value = "control")
  └── test   10%   (multivariate option)
```

> **Important:** The 90/10 split above is the flag's global multivariate distribution. In this
> POC the split is applied *only within* the `carousel_experiment` segment (see Step 1.3).
> Users outside that segment always receive the flag's plain default value (`control`).

---

### 1.3 Create the `carousel_experiment` Segment

This segment acts as the experiment gate. Only identities carrying
`Carousel_Cohort = true` as a trait will enter the A/B split.

1. Go to **Segments → Create Segment**
2. Name: `carousel_experiment`
3. Add rule: **Trait** `Carousel_Cohort` **equals** `true`
4. Save the segment

---

### 1.4 Create the Bucket-Override Segments

These segments allow a `userId` identity to be pinned to the same variant
as its corresponding `deviceId` identity (see Phase 2). They sit at a higher
priority than the experiment segment so they always win.

Create two segments:

| Segment name              | Rule                                   |
|---------------------------|----------------------------------------|
| `override_test_bucket`    | `bucket_override` **equals** `test`    |
| `override_control_bucket` | `bucket_override` **equals** `control` |

---

### 1.5 Add Segment Overrides to the Flag

Open the `carousel_ab_test` flag and add three segment overrides **in this exact priority order**
(drag to reorder; lower number = higher priority):

| Priority | Segment                   | Value / Split               | Notes |
|----------|---------------------------|-----------------------------|-------|
| 1        | `override_test_bucket`    | `test` (100%)               | Pins identity to test variant |
| 2        | `override_control_bucket` | `control` (100%)            | Pins identity to control variant |
| 3        | `carousel_experiment`     | `control` 90% / `test` 10%  | The actual A/B split |

The priority ordering is critical. When a user has `bucket_override = test`, the override
segment fires before `carousel_experiment` is evaluated, locking them into the correct bucket
regardless of how Flagsmith's hashing would otherwise assign their `userId` identity.

---

### 1.6 Copy Your API Key

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
java -Dflagsmith.api.key=ser.YOUR_KEY \
     -jar target/flagsmith-poc-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Or via environment variable:

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

Each user is sent to Flagsmith as an identity keyed by their `deviceId`, carrying the
`Carousel_Cohort = true` trait. Flagsmith evaluates the `carousel_ab_test` flag:

1. Is `bucket_override` set? → check override segments (priority 1 & 2). No on first run.
2. Does `Carousel_Cohort = true`? → yes, so `carousel_experiment` segment matches.
3. Apply the 90/10 multivariate split within that segment.

Because `deviceId` values are derived deterministically (via `UUID.nameUUIDFromBytes`),
results are **stable across runs** — the same user always lands in the same bucket.

To exclude a user from the experiment entirely, set `inCarouselCohort = false` in
`UserFactory`. That identity will skip all three segment overrides and receive the
flag's plain default value (`control`).

Expected Phase 1 output:
```
[Alice_1           ] [cohort]   deviceId=device-<uuid>  → ⬜ control
[Bob_2             ] [cohort]   deviceId=device-<uuid>  → ⬜ control
[Carol_3           ] [cohort]   deviceId=device-<uuid>  → 🔵 test
...
Totals → control: ~45 | test: ~5
```

### Phase 2 — `userId` identity override for 20 identified users

For each of the 20 users who have been assigned a `userId`:

1. The `deviceId` variant (from Phase 1) is read — this is their "true" bucket.
2. The `userId` identity is registered in Flagsmith with:
   - `Carousel_Cohort = true` (keeps them in the experiment segment)
   - `bucket_override = <control|test>` (the value from step 1)
3. Because `override_test_bucket` / `override_control_bucket` sit at priority 1 & 2, the
   `bucket_override` trait fires before the `carousel_experiment` split, returning the
   same variant the `deviceId` received.
4. The POC checks that both identities landed in the same bucket and reports the result.

Expected Phase 2 output:
```
[Alice_1           ]
  deviceId : device-<uuid>  → ⬜ control
  userId   : user-<uuid>    → ⬜ control
  Bucket preserved? ✅ YES

[Carol_3           ]
  deviceId : device-<uuid>  → 🔵 test
  userId   : user-<uuid>    → 🔵 test
  Bucket preserved? ✅ YES
```

---

## Architecture

```
FlagsmithPoc.java           ← entry point, orchestrates phases
  |── ConfigService.java   ← Manages config
  ├── UserFactory.java       ← generates 50 deterministic users (all in cohort by default)
  ├── FlagsmithService.java  ← wraps the Flagsmith Java SDK
  │     ├── getVariantForDevice()          Phase 1
  │     ├── getVariantForIdentifiedUser()  Phase 2 (read)
  │     └── applyUserIdOverride()          Phase 2 (write + read)
  ├── User.java              ← model: name, deviceId, userId?, inCarouselCohort
  ├── FlagVariant.java       ← enum: CONTROL | TEST | DISABLED
  └── ResultSummary.java     ← collects & prints the final report
  
FlagsmithDeleteIdentities.java  ← Clean up script to delete all identities.  
```

### Trait reference

| Trait key         | Type    | Set by                      | Purpose |
|-------------------|---------|-----------------------------|---------|
| `Carousel_Cohort` | boolean | All SDK calls               | Segment membership — gates entry into the experiment |
| `bucket_override` | string  | `applyUserIdOverride()` only | Pins a `userId` identity to the same variant as its `deviceId` |
| `device_id`       | string  | All SDK calls               | Stored for auditing / debugging in the Flagsmith dashboard |
| `user_id`         | string  | Phase 2 calls               | Stored for auditing / debugging |
| `name`            | string  | All SDK calls               | Human-readable label in the dashboard |

---

## Segment Override Priority — Visual Summary

```
Incoming identity
        │
        ▼
┌─────────────────────────────────────────────┐
│  Priority 1: override_test_bucket           │
│  bucket_override = "test"  →  return "test" │──► 🔵 test
└─────────────────────────────────────────────┘
        │ no match
        ▼
┌──────────────────────────────────────────────────┐
│  Priority 2: override_control_bucket             │
│  bucket_override = "control" →  return "control" │──► ⬜ control
└──────────────────────────────────────────────────┘
        │ no match
        ▼
┌──────────────────────────────────────────────────┐
│  Priority 3: carousel_experiment                 │
│  Carousel_Cohort = true  →  90% / 10% split      │──► ⬜ control (90%)
│                                                  │──► 🔵 test    (10%)
└──────────────────────────────────────────────────┘
        │ no match  (Carousel_Cohort = false or absent)
        ▼
   Flag default value: "control"                    ──► ⬜ control (outside experiment)
```

---

## Identity Override — Technical Deep Dive

Flagsmith uses **consistent hashing** (MurmurHash on the identity key + flag ID)
to assign each identity to a multivariate bucket. Two different identity keys
(e.g. `device-abc` and `user-xyz`) will almost certainly hash to different buckets
even if they represent the same person.

This POC implements **Option A** below. Options B and C are noted for completeness.

### Option A — Traits + Segment Rules (implemented, works on all tiers)

Store the device bucket as a trait on the `userId` identity:
```
bucket_override = "control"   # or "test"
```
A higher-priority segment override matches on this trait and forces the correct
value before Flagsmith's multivariate hashing is applied.

**Limitation:** requires two SDK calls per identified user (one to read the
`deviceId` variant, one to register the `userId` with the override trait).

### Option B — Management API (hard override, paid plans only)

Use the Flagsmith REST Management API to pin an identity to a specific value,
bypassing hashing entirely:

```http
POST /api/v1/environments/{env_key}/identities/
Content-Type: application/json

{
  "identifier": "user-<uuid>",
  "feature_states": [
    {
      "feature": { "name": "carousel_ab_test" },
      "feature_state_value": "test",
      "enabled": true
    }
  ]
}
```

See [Flagsmith docs — identity overrides](https://docs.flagsmith.com/clients/rest#identity-overrides).

### Option C — Unified identity key (best practice for greenfield projects)

If possible, always use `userId` as the primary identity key from the start and
pass `deviceId` as a trait. This eliminates the bucket mismatch problem entirely
because there is only ever one identity key per person.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| All users get `disabled` | Flag is OFF in Flagsmith | Enable the flag in the dashboard |
| All users get `control` with no split | `carousel_experiment` segment override missing | Add the segment override with 90/10 multivariate split (Step 1.5) |
| All users get `control` even with override | Override segments have lower priority than `carousel_experiment` | Drag `override_*` segments above `carousel_experiment` in the overrides list |
| `bucket_override` trait not taking effect | Override segments not created | Follow Step 1.4 |
| Users outside cohort land in `test` | `Carousel_Cohort` trait not being sent | Verify `setInCarouselCohort(true/false)` in `UserFactory` and check `FlagsmithService` passes the trait |
| `FlagsmithClientError` at startup | Wrong or missing API key | Set `flagsmith.api.key` in `config.properties` |
| Phase 2 bucket mismatch (`⚠️ NO`) | Override segment priority wrong, or `Carousel_Cohort` not set on `userId` identity | Check segment priority order; ensure `applyUserIdOverride()` passes the cohort trait |

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
        │   ├── FlagsmithPoc.java                  # main() — orchestrates Phase 1 & Phase 2
        │   ├── FlagsmithService.java              # Flagsmith SDK wrapper
        │   ├── User.java                          # model: name, deviceId, userId?, inCarouselCohort
        │   ├── UserFactory.java                   # generates 50 deterministic users
        │   ├── FlagVariant.java                   # enum: CONTROL | TEST | DISABLED
        │   └── ResultSummary.java                 # collects results and prints final report
        └── resources/
            ├── config.properties                  # ← put your Flagsmith API key here
            └── logback.xml                        # logs to stderr; report goes to stdout
```
