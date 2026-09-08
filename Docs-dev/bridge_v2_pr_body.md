<!-- DRAFT PR (v2) — base: the v1 bridge PR branch (jtagusari:feat/bridge-v1), NOT main.
     head: jtagusari/NoiseModelling : feat/bridge-v2
     Title: feat: bridge v2 — deck-underside reflection, bottom+top diffraction, structural-noise split
     Open as: Draft, stacked on the v1 PR.
     Commits (the fix()/test() below fold into the feat() they correct before opening):
       feat(pathfinder): bridge source-relationship plumbing + mirror source
       feat(propagation): bridge bottom+top (BT) diffraction
       test(propagation): TBC bridge attenuation golden harness
       fix(propagation): only mark a spliced deck edge as DIFB   [-> BT commit]
       fix(propagation): re-hull the after-portion when splicing   [-> BT commit]
       fix(pathfinder): keep the deck underside edge out of the ground profile   [-> pathfinder commit]
       test(propagation): resync TBC02 bridge golden fixture from the fork   [-> pathfinder commit]
       fix(pathfinder): copy the processed bridge wall segment before mutating its Z
       feat: bridge structural noise via an EMISSION_TYPE split
       feat(scripts): Structural_noise_from_bridge emission-type output mode -->

## Context

**Step 3 of the bridge feature**, stacked on the v1 PR (`feat/bridge-v1`).

v0 treats the deck as a passive over-the-top screen; v1 adds on-deck source
elevation and the ASJ structural spectrum (as a separate table). Both leave
receivers *below or beside* the structure unmodelled, and the structural
spectrum is not yet placed at the deck. v2 adds:

1. a source below a deck gets a **mirror image across the deck underside** —
   the deck soffit reflecting road noise;
2. an imaginary/mirror source whose straight line to the receiver is blocked
   by a deck edge diffracts **around the deck body** — under the lower edge
   (a new `DIFB` point type) and back over the next obstacle's upper edge —
   the *bottom+top* (BT) route;
3. an **`EMISSION_TYPE` split** — a source on a deck is emitted twice, traffic
   noise at deck level and structural noise at the deck underside, keyed on the
   same source pk, with a gate routing each spectrum to its point.

`AttenuationCnossos` builds diffraction from the **upper** convex hull only, so
an under-then-over route has no representation there today. v2 adds it as
**guarded splices** onto the existing `CnossosPathBuilder` /
`AttenuationCnossos`, with **no `*Ext` type-fork** — the fork (`fix` branch) is
used only as the physics reference and as the source of regression fixtures.

Same hard constraint as v0/v1: **a scene with no bridge input computes exactly
as today, and every existing test stays green.** Every new propagation branch
is entered only when a path carries a `DIFB` point (impossible without a
downward deck edge and an imaginary/mirror source); every new pathfinder
branch is guarded by `hasBridges()` / a non-null `bridgeRelationship`.

## What this adds

### `feat(pathfinder): bridge source-relationship plumbing + mirror source`

| change | detail |
|---|---|
| `PathFinder.addBridgeVirtualSources()` | for a source below a deck and inside its footprint, append the image source reflected across the underside of the lowest deck above it (`MIRROR_SOURCE`), matching the fork's `BridgeAnalyzer.selectBridgeForMirrorSource`. Guarded by `hasBridges()`. |
| `SourcePointInfo` / `ProfileBuilder.getProfile()` | `SourcePointInfo` carries an optional `BridgeRelationship`; a new 5-arg `getProfile` overload applies it as a source-classification override, falling back to the existing geometric classification when null. |
| `CutProfile` / `CutPointSource` copy constructors | used by the propagation side to derive edge-filtered BT sub-profiles. |
| `CutPointSource` / `CutPoint` / `CutPointBridgeWall` JSON | flattened `bridgeRelationType` / `bridgePkOn` / `bridgePkAbove` accessors + `"BridgeWall"` subtype registration, so bridge cut profiles round-trip through Jackson (the v2 golden fixtures). |

### `feat(propagation): bridge bottom+top (BT) diffraction`

| change | detail |
|---|---|
| `PointPath.POINT_TYPE` | new `DIFB` (diffraction under a deck's lower edge). |
| `DistanceDifferenceCalculator` (new) | CNOSSOS Eq. 2.5.24/2.5.25 curved-path and signed path-length-difference geometry, ported verbatim from the fork (depends only on `Coordinate` / `LineSegment`). |
| `CnossosPath` | two nullable sub-route fields (`bridgeBottomRoute` / `bridgeTopRoute`). |
| `CnossosPathBuilder.spliceBridgeBottomEdge()` | when the source is `IMAGINARY_SOURCE_UNDER_BRIDGE` / `MIRROR_SOURCE` and a downward deck edge lies below the first hull segment (missed by the upper hull), splice its index in as an explicit bottom pivot marked `DIFB`. |
| `CnossosPathBuilder.computeBridgeBottomTopRoutes()` | a path carrying both `DIFB` and `DIFH` is split into a bottom route and a top route via four edge-filtered sub-profiles (`newCutProfile{WithoutBottomEdge, OnlyWithBottomEdge, AfterBottomEdge, BeforeTopEdge}`, adapted from the fork), chosen by `deltaB` vs `deltaH`, built by a bounded recursive call. |
| `AttenuationCnossos.aBoundary()` / `aDif()` | `aBoundary` returns `aBoundary(top) + aBoundary(bottom)` per band when the path has both a `DIFB` and a `DIFH` point and both sub-routes are set; `aDif` treats `DIFB` as a pure knife-edge (like `DIFV`: no ground term on the two legs). |

### `test(propagation): TBC bridge attenuation golden harness`

`BridgeAttenuationGoldenTest` feeds the fork's serialised bridge cut profiles
through upstream's pipeline and asserts the fork's stored global attenuation
(`aGlobalH`) is reproduced within 0.2 dB.

### `fix(pathfinder): copy the processed bridge wall segment before mutating its Z`

`createBridgeCutPoint` passed the processed bridge wall's own `LineSegment` into
`CutPointBridgeWall`, whose `modifyIntersectionHeight` rewrites `p0`/`p1` Z in
place. The processed walls are shared across every `getProfile` call, so a Z one
profile wrote (a DOWNWARD deck-underside edge at `deck − thickness`, for an
imaginary/mirror source) leaked into the next profile reusing that wall (an
UPWARD parapet edge at `deck + barrier`, for an actual on-deck source), turning
the shielding parapet into a non-obstructing edge. Fix: pass a fresh
`LineSegment` copy. Latent in the fork too; surfaces once a scene has both an
imaginary/below-deck and an actual on-deck source over one deck.

### `feat: bridge structural noise via an EMISSION_TYPE split`

A source on a deck radiates traffic noise at deck level **and** structural
(deck/girder) noise at the deck underside. This wires the fork's split onto the
upstream structure — the ASJ structural spectrum itself is unchanged (v1's
`RoadAsj`):

| change | detail |
|---|---|
| `SceneWithEmission.PeriodEmission` + new `input/EmissionType.java` | each emission row carries a `ROAD` / `BRIDGE` kind. An optional `EMISSION_TYPE` column on an LW emission table sets it; an absent column means `ROAD`, so existing scenes are unchanged. (The fork maps absent → a permissive `NULL`; `ROAD` avoids a latent double count on a raw LW table over a bridge.) |
| `AttenuationOutputSingleThread.checkIfEmissionIsAcceptable` | routes `ROAD` rows to the `ACTUAL_SOURCE_ON_BRIDGE` / `MIRROR_SOURCE` point and `BRIDGE` rows to the `IMAGINARY_SOURCE_UNDER_BRIDGE` point; a `BRIDGE` row on a source not on a deck is dropped. `ROAD ≠ BRIDGE`, so it is a no-op unless a deck source and a `BRIDGE` row both exist. |
| `PathFinder.addBridgeVirtualSources` | appends the deck-underside imaginary point for each on-deck source (same `sourcePk`, z at `soffit − 0.05`). With no `BRIDGE` row the gate keeps it silent — the reason the earlier auto-gen was removed, now safe. |

`BridgeStructuralNoiseTest`: `BOTH` equals the energy sum of the `ROAD`-only and
`BRIDGE`-only runs (no double count, no cross-routing); a `BRIDGE` row with no
deck is dropped. `BridgeNoiseMapTest` (red under the earlier auto-gen) is green.

### `feat(scripts): Structural_noise_from_bridge emission-type output mode`

Default is unchanged — the ASJ deck spectrum goes to a separate
`BRIDGE_STRUCT_LW` line-source table. New: set `emissionTable` + `tableSources`
(road sources with a `BRIDGE_PK` column) and the spectrum is appended to that
existing emission table as `EMISSION_TYPE='BRIDGE'` rows, one per (on-deck road
source, period). The two modes are mutually exclusive.

## Validation

The fork exercises the BT physics through its `TBC*` bridge test suite; v2
replays those fixtures plus two synthetic BT cases added to the fork:

- **TBC12** — imaginary source under a wide deck + a downstream wall. The
  homogeneous golden `aGlobalH` is reproduced **bit-for-bit**; favourable
  within 0.04 dB.
- **TBC13** — the same for a `MIRROR_SOURCE` (mirror image above the deck,
  obstructing downward edge, downstream wall). Golden within 0.2 dB.
- **TBC01 / 02(imaginary) / 03 / 07 / 11 / 20** — reproduced within 0.2 dB.
- **TBC31** — the compound real-DEM scene (TutoBridge topography + a 4 m wall);
  all four paths within 0.2 dB, including the mirror BT path (deck-edge DIFB +
  wall DIFH found by re-hulling the after-portion).

## Existing results are unchanged

- the TC01–TC28 CNOSSOS reference cases pass with unchanged per-band goldens;
- `AttenuationCnossos.aBoundary` / `aDif` gain a branch entered only when a
  `DIFB` point is present;
- pathfinder 252, propagation 61, emission 66, jdbc 61, scripts 100 (4
  pre-existing skips). No regression.

**`TBC02` path 1** (ACTUAL, source on the deck, receiver below and beside) sits
~2.6 dB below the fork's golden. Instrumentation shows an identical diffraction
pivot and identical scalar `delta`; the whole difference is that the receiver is
below the O→R mean plane, where upstream's `aDif` applies the CNOSSOS-EU
2.5.31/2.5.33 below-mean-plane branch and the fork's older `aDif` does not.
Upstream is the more CNOSSOS-correct value — nothing to port. The imaginary path
(the main under-deck contribution) matches exactly.

## Still to come

- **ROAD/BRIDGE in one pass** — `Road_Emission_from_Traffic` could emit both
  spectra at once (the fork does); today the `BRIDGE` spectrum comes from
  `Structural_noise_from_bridge` appending to the LW emission table. Traffic-flow
  input modes do not yet produce an `EMISSION_TYPE`.
- **Stacked decks** — `spliceBridgeBottomEdge` handles one downward edge; the
  fork re-hulls recursively for two or more (3+ level viaducts with a source
  below all of them). No `TBC*` covers this; two-deck cases (TBC20/21) have a
  single edge and work today.
- **Favourable-condition BT** — the favourable sub-routes are reproduced within
  ~0.05 dB, not bit-for-bit; `deltaF` (curved-path difference) for the BT
  sub-routes is not tightened.

## Notes

- Stacks on `feat/bridge-v1`; review v0 and v1 first.
- No `*Ext` type-fork: `ProfileBuilder` / `CutProfile` / `CnossosPath` /
  `AttenuationCnossos` keep their upstream structure; only guarded bridge
  branches are added.
