<!-- DRAFT PR (v1) — base: the v0 bridge PR branch (jtagusari:feat/bridge), NOT main.
     head: jtagusari/NoiseModelling : feat/bridge-v1
     Title: feat: bridge v1 — on-deck source elevation + ASJ structural noise
     Open as: Draft, stacked on the v0 PR. -->

## Context

This is **step 2 of the bridge feature**, stacked on the v0 PR
(`feat/bridge`). v0 treats the deck as a passive over-the-top screen and does
not touch the propagation or emission model. v1 adds the two things that make
an elevated *road* different from a raised source:

1. a source that sits **on the deck** gets the deck surface as its ground
   reference, not the terrain far below;
2. the **structure-radiated noise** — the deck and girders vibrating under
   traffic — as an additional source, using ASJ RTN-Model 2023 coefficients
   (CNOSSOS has none for this).

Same hard constraint as v0: **a scene with no bridge input computes exactly as
today, and every existing test stays green.**

## What this adds

| module | change |
|---|---|
| `noisemodelling-pathfinder` | `CutProfile.computePtsGround()`: a source carrying a bridge height sits on the deck surface in the ground profile; the reference is carried while the ray runs on the deck (`lastGroundZ`) and dropped back to the terrain at the deck's exit wall. All new branches are guarded by a condition that cannot be true without a bridge cut point. |
| `noisemodelling-propagation` | **no production change** — one test pinning the source-on-deck diffraction path geometry. |
| `noisemodelling-emission` | new self-contained `emission.road.asj` package: `RoadAsj`, `RoadAsjParameters`, `RoadAsj_2023.json` (7 girder+slab types), 38 tests. Computes deck/girder radiated noise by construction type. **No change to any existing file.** |
| `noisemodelling-scripts` | new `Structural_noise_from_bridge` WPS block: `BRIDGE_POINTS` + a per-deck heavy-vehicle traffic table → `BRIDGE_STRUCT_LW`, one line source per deck and period at deck height carrying the ASJ octave-band spectrum. Feed it to `Noise_level_from_source` as `tableSources` alongside the vehicles' CNOSSOS emission. |

### Per-bridge-point deck height

Already supported by the deck triangulation from v0 — each `BridgePoint`
carries its own height and `BridgeService` interpolates. v1 just exercises it.

### How the structural source behaves

End-to-end test: a deck scene run with the vehicle source alone, then with
the vehicle source + the `Structural_noise_from_bridge` output co-located on
the deck. Against a road-shaped vehicle spectrum (74 dB at 63 Hz rising to
92 dB at 1 kHz), the steel-bridge structural source lifts a near-deck
receiver's **63 Hz band by ~4.6 dB** while barely touching 4 kHz — the
low-frequency character that steel-bridge structural noise is known for. The
overall A-weighted change is modest (63 Hz is weighted down hard); it matters
for low-frequency assessment and for receivers close to the structure.

## Existing results are unchanged

No dedicated parity harness — the following already cover it:

- every new branch in `CutProfile` / `ProfileBuilder` is guarded by
  `bridgeCount > 0` / `instanceof CutPointBridgeWall` / a finite bridge
  height / `hasBridgeIntersection`, so a scene with no bridge executes none of
  the v1 code;
- the TC01–TC28 CNOSSOS reference cases pass with unchanged hardcoded
  per-band goldens;
- the JDBC `RegressionTest` / `NoiseMapByReceiverMakerTest` end-to-end
  goldens pass unchanged;
- `groundProfileUnchangedWithoutAnyBridge` asserts the `computePtsGround`
  change is inert on flat terrain with no bridge.

`AttenuationCnossos` is **not touched** in v1.

Tests: pathfinder 252, propagation 56, emission 66, jdbc 59, scripts (bridge
suites green). No regression.

## Still to come (v2)

Receivers *below or beside* the structure — deck-underside reflection, and
diffraction around the deck body (under the lower edge, back over the upper
edge). `AttenuationCnossos` builds diffraction paths from the *upper* convex
hull, so an under-then-over route has no representation there today; adding it
is real work rather than a guarded branch, kept separate from v1.

## Notes

- Stacks on `feat/bridge`; review v0 first.
- The ASJ coefficient table is bundled because CNOSSOS has no structural-noise
  equivalent; the structural source is opt-in (a separate script and table),
  existing emission models are untouched.
