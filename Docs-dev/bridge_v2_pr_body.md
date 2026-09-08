<!-- DRAFT PR (v2) — base: the v1 bridge PR branch (jtagusari:feat/bridge-v1), NOT main.
     head: jtagusari/NoiseModelling : feat/bridge-v2
     Title: feat: bridge v2 — deck-underside reflection + bottom+top diffraction
     Open as: Draft, stacked on the v1 PR.
     Commits (the three fix() below fold into the feat() they correct before opening):
       feat(pathfinder): bridge source-relationship plumbing + mirror source
       feat(propagation): bridge bottom+top (BT) diffraction
       test(propagation): TBC bridge attenuation golden harness
       fix(propagation): only mark a spliced deck edge as DIFB   [-> BT commit]
       fix(propagation): re-hull the after-portion when splicing   [-> BT commit]
       fix(pathfinder): match fix's deck-edge ground-profile point order   [-> pathfinder commit] -->

## Context

**Step 3 of the bridge feature**, stacked on the v1 PR (`feat/bridge-v1`).

v0 treats the deck as a passive over-the-top screen; v1 adds on-deck source
elevation and ASJ structural noise. Both leave receivers *below or beside* the
structure unmodelled. v2 adds those paths:

1. a source below a deck gets a **mirror image across the deck underside** —
   the deck soffit reflecting road noise;
2. an imaginary/mirror source whose straight line to the receiver is blocked
   by a deck edge diffracts **around the deck body** — under the lower edge
   (a new `DIFB` point type) and back over the next obstacle's upper edge —
   the *bottom+top* (BT) route.

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

## Scope decision: structural noise stays a separate source

The fork models bridge structural noise as an `IMAGINARY_SOURCE_UNDER_BRIDGE`
placed at the deck soffit, selected through an `EMISSION_TYPE` column and an
emission-type acceptance gate. v2 **does not port that split** — bridge
structural noise remains v1's opt-in `Structural_noise_from_bridge` /
`BRIDGE_STRUCT_LW` separate source table. v2's `IMAGINARY_SOURCE_UNDER_BRIDGE`
handling is therefore the **propagation geometry only** (the BT route); it
activates when a cut profile genuinely carries such a source. Wiring the
emission-type split is deferred (see "Still to come").

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
- pathfinder 252, propagation 61, emission 66, jdbc 59, scripts 100 (4
  pre-existing skips). No regression.

## Still to come (v2.2)

- **`TBC02` path 1** (ACTUAL over the deck's outer edge) — ~3 dB: the fork's
  `aDif` uses the deck geometry where upstream treats the cut point as a plain
  thin screen. The imaginary path (the main under-deck contribution) matches.
- **Structural noise as `IMAGINARY_SOURCE_UNDER_BRIDGE`** — the fork's
  `EMISSION_TYPE` + emission-type acceptance gate, so the structural spectrum
  is auto-placed at the deck soffit instead of a separate table.
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
