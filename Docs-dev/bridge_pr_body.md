<!-- DRAFT PR — base: Universite-Gustave-Eiffel/NoiseModelling : main
     head: jtagusari/NoiseModelling : feat/bridge
     Title: feat: bridge / elevated-road noise modelling (v0, draft)
     Open as: Draft -->

## Goal

I want NoiseModelling to model an **elevated road structure** properly, not just as a raised source. That means three things, together:

1. the **deck geometry** as an explicit obstacle — diffraction over the edge, shadow under and beside it (this PR);
2. **reflection off the deck underside** — for receivers below or beside the structure;
3. **structure-radiated noise** — the deck and girders vibrating under traffic, which for a steel bridge can be the dominant component and which CNOSSOS has no coefficients for.

CNOSSOS-EU does not cover any of this — the method has no treatment of elevated structures, no structure-radiated source, and no deck-underside reflection, so there is no reference procedure to follow. This is exploratory work that goes beyond the standard's scope, and I'd like to build it in the open with the project rather than carry it on a fork.

Throughout, a hard constraint: **no change to existing results or tests**. A scene with no bridge input must compute exactly as it does today, and every current test must stay green. The propagation module has one small, guarded production change (see "What this adds"), and the full suite passes unchanged otherwise (pathfinder 200, propagation 56 incl. the TC01–TC28 CNOSSOS cases, jdbc 59, scripts 98 - see Scope for the breakdown).

This PR is step 1 only, kept deliberately small. 2 and 3 are the point of the work but come later — the "Follow-up" section maps where they land.

## What this adds

A first, deliberately small implementation of **bridge / elevated-road noise modelling**: traffic running on a raised deck (viaduct, urban expressway), where the deck is an **explicit scene input with its own geometry** rather than an approximation by raising source / receiver Z.

A deck is described by a set of *bridge points* — centre-line points carrying deck height (absolute or relative to the DEM), thickness, per-side width, parapet height, and construction type (girder / slab); `BridgeTriangulation` interpolates deck height/thickness between points, so a sloped or tapering deck is already a per-point input, not a single flat value. `BridgeService` triangulates the 3-D deck, exports its facets as processed walls, and answers elevation / footprint / propagation-type queries. In `ProfileBuilder.getProfile()` the source is classified against each deck (on it, under it, unrelated) and bridge cut points are inserted where the source→receiver segment crosses a deck facet. From there the deck edge flows through the **existing** CNOSSOS convex-hull diffraction path — no change to the propagation math.

Two more pieces make a source *on* the deck actually usable, not just shielded correctly for *other* sources passing nearby:

- **Ground reference**: `CutProfile.computePtsGround()` gains a guarded branch so a source classified on a deck uses the **deck surface**, not the terrain far below, as its CNOSSOS ground reference. Without this, height-above-ground (and everything downstream of it — ground effect, diffraction geometry) is simply wrong for an on-deck source, independent of anything structure-noise related.
- **`BRIDGE_PK` height resolution**: `Road_Emission_from_Traffic` forces every source Z to a flat 0.05 m (the CNOSSOS road-source convention), and the existing relative→absolute Z conversion only ever adds the DEM ground elevation. Run through the real traffic-flow pipeline, a road physically on a deck therefore ended up placed near the ground below it and was never classified as on the bridge. An optional `BRIDGE_PK` column (same convention as `tableSources` for the structure-noise script, see Follow-up) lets `ProfileBuilder` resolve that source's relative Z against the named deck's height instead of the DEM — absent column or null value keeps today's DEM-only behaviour, so non-bridge scenes are unaffected.

This is enough to run **DB table → noise map → deck shadow** end to end, including through the `Noise_level_from_source` WPS block and the real traffic-flow (`Road_Emission_from_Traffic`) pipeline, validated against a real elevated-expressway dataset (Seishin Bypass, Shizuoka, Japan).

## Scope

**In (v0):**

| module | change |
| --- | --- |
| `noisemodelling-pathfinder` | deck geometry (`Bridge`, `BridgePoint`, `BridgeService`, …), source classification and bridge cut points in `ProfileBuilder.getProfile()`, `isFreeField()` counts a bridge intersection, on-deck ground reference in `CutProfile.computePtsGround()`, `BRIDGE_PK`-aware relative→absolute Z resolution, small accessors on `Scene` / `CutPoint*` / `Wall` |
| `noisemodelling-propagation` | one guarded line in `CnossosPathBuilder`: use the true 3-D direct distance for the deck's off-axis edge, same fallback already used for vertical-edge diffraction |
| `noisemodelling-jdbc` | optional `BRIDGE_POINTS` table loader in `DefaultTableLoader`, `BRIDGE_PK`-aware source Z resolution in `fetchCellSource` |
| `noisemodelling-scripts` | optional `tableBridgePoints` input on `Noise_level_from_source`; `BRIDGE_PK` pass-through in `Road_Emission_from_Traffic`; `TutoBridge` example dataset + a real elevated-expressway dataset (Seishin Bypass) |

Tests: pathfinder **200**, propagation **56** (incl. the 47 TC01–TC28 CNOSSOS regression cases), jdbc **59**, scripts **98** (94 run + 4 environment-gated skips) — all green, no regression to existing tests. See "Tests" below for which ones are worth reading first to understand the feature, as opposed to the lower-level unit tests for the supporting classes.

**Out (follow-up):** deck-underside reflection and mirror sources (goal 2), structure-radiated noise (goal 3), tutorial expansion with day/night maps. Detail below.

## Tests

Most of the test count is unit tests for the new geometry/support classes
(`Bridge*Test`, `RoadAsj*` is not part of this PR). The ones that actually
show what the feature *does*, end to end:

| test | layer | what it shows |
| --- | --- | --- |
| `BridgeProfileBuilderTest.sourceOnDeckSitsAtDeckLevelInTheGroundProfile` | pathfinder | the on-deck ground-reference fix itself: the CNOSSOS ground profile uses deck height, not terrain, directly |
| `BridgeProfileBuilderTest.groundProfileUnchangedWithoutAnyBridge` | pathfinder | the guard: a non-bridge profile is byte-identical to today |
| `BridgePropagationTest.sourceOnDeckProducesADiffractingPathWithoutError` / `sameGeometryWithoutDeckIsADirectPath` | propagation | the deck edge reaching the *existing*, unmodified CNOSSOS diffraction path |
| `BridgePropagationTest.sourceOnDeckToReceiverBelowAndBesideUsesTrueDirectDistance` | propagation | the off-axis-edge distance fix itself: a non-collinear source/pivot/receiver geometry where the 2-D-unfolded distance and the true one visibly diverge, and CNOSSOS delta is computed from the true one |
| `BridgeNoiseMapTest.bridgeDeckLowersTheLevelAtAShieldedReceiver` | jdbc | `BRIDGE_POINTS` table → noise map, with/without the deck |
| `TestNoiseLevelFromBridge.bridgeDeckShieldsAGroundReceiver` | scripts (WPS) | per-octave insertion loss bracketed against the CNOSSOS single-diffraction term for the geometry's path difference |
| `TestNoiseLevelFromBridge.bridgePkCarriesTheDeckHeightThroughRoadEmissionFromTraffic` | scripts (WPS) | the `BRIDGE_PK` fix: with/without the column through the real traffic-flow pipeline, >11 dB apart |
| `TestNoiseLevelFromBridgeTraffic` / `TestNoiseLevelFromBridge.tutoBridgeCastsANoiseShadow` | scripts (WPS) | full run against the TutoBridge synthetic dataset and a real elevated-expressway dataset (Seishin Bypass, Shizuoka) |

## Follow-up (v1)

Not questions — nothing here changes existing results — just a map of where the rest of the feature lands, so the shape of this PR makes sense.

`ProfileBuilder` / `CutProfile` have no extension point for a new obstacle type, so even this minimal version adds ~9 small support classes and a few accessors on `Scene` / `CutPoint*` / `Wall`. I'm not proposing to restructure those classes.

**v1** is receivers *below or beside* the structure — deck-underside reflection, and diffraction around the deck body (under the lower edge, back over the upper edge). `AttenuationCnossos` builds diffraction paths from the *upper* convex hull of the profile, so an under-then-over route has no representation there today; it's a real piece of work rather than a guarded branch, so it is kept as its own step rather than folded into this PR.

(The on-deck ground reference and `BRIDGE_PK` height resolution originally planned for v1 turned out to be plain CNOSSOS-EU geometric correctness with no dependency on the structure-noise source, so they moved into this PR — see "What this adds".)

**v2** stays additive and behaviour-preserving for any scene with no bridge input — every new branch is guarded by a condition that cannot be true without a bridge:

- **Structure-radiated source**: an ASJ RTN-Model 2023 coefficient table for deck / girder radiated noise, added alongside the vehicles' CNOSSOS noise. Additive; existing emission models unchanged.

Each step keeps every new branch guarded by a condition that cannot be true without a bridge, so a non-bridge scene stays byte-identical — checked against this module's own existing golden/regression suite rather than a dedicated parity harness, which turned out not to be needed on top of that. Feedback on any of it is welcome.

## Notes

- Branched from `main` at `04793d5` (2026-08-14). Happy to rebase.
- `CutProfile.isFreeField()` now also returns false on a bridge intersection — without it `PathFinder` submits a truncated profile for a deck-shadowed receiver and the level collapses to the rejected-path floor.
