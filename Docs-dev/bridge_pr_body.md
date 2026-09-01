<!-- DRAFT PR — base: Universite-Gustave-Eiffel/NoiseModelling : main
     head: jtagusari/NoiseModelling : feat/bridge
     Title: feat: bridge / elevated-road noise modelling (v0, draft)
     Open as: Draft -->

## Goal

I want NoiseModelling to model an **elevated road structure** properly, not just
as a raised source. That means three things, together:

1. the **deck geometry** as an explicit obstacle — diffraction over the edge,
   shadow under and beside it (this PR);
2. **structure-radiated noise** — the deck and girders vibrating under traffic,
   which for a steel bridge can be the dominant component and which CNOSSOS has
   no coefficients for;
3. **reflection off the deck underside** — for receivers below or beside the
   structure.

CNOSSOS-EU does not cover any of this — the method has no treatment of elevated
structures, no structure-radiated source, and no deck-underside reflection, so
there is no reference procedure to follow. This is exploratory work that goes
beyond the standard's scope, and I'd like to build it in the open with the
project rather than carry it on a fork.

Throughout, a hard constraint: **no change to existing results or tests**. A
scene with no bridge input must compute exactly as it does today, and every
current test must stay green. In this PR the propagation module has no
production change at all, and the full suite passes unchanged
(pathfinder 249, propagation 55 incl. the TC01–TC28 CNOSSOS cases, jdbc 59,
scripts 96).

This PR is step 1 only, kept deliberately small. 2 and 3 are the point of the
work but come later; the design questions below are about making room for them.

## What this adds

A first, deliberately small implementation of **bridge / elevated-road noise
modelling**: traffic running on a raised deck (viaduct, urban expressway),
where the deck is an **explicit scene input with its own geometry** rather than
an approximation by raising source / receiver Z.

A deck is described by a set of *bridge points* — centre-line points carrying
deck height (absolute or relative to the DEM), thickness, per-side width,
parapet height, and construction type (girder / slab). `BridgeService`
triangulates the 3-D deck, exports its facets as processed walls, and answers
elevation / footprint / propagation-type queries. In `ProfileBuilder.getProfile()`
the source is classified against each deck (on it, under it, unrelated) and
bridge cut points are inserted where the source→receiver segment crosses a deck
facet. From there the deck edge flows through the **existing** CNOSSOS
convex-hull diffraction path — no change to the propagation math.

This is enough to run **DB table → noise map → deck shadow** end to end,
including through the `Noise_level_from_source` WPS block.

## Scope

**In (v0):**

| module | change |
|---|---|
| `noisemodelling-pathfinder` | deck geometry (`Bridge`, `BridgePoint`, `BridgeService`, …), source classification and bridge cut points in `ProfileBuilder.getProfile()`, `isFreeField()` counts a bridge intersection, small accessors on `Scene` / `CutPoint*` / `Wall` |
| `noisemodelling-propagation` | **no production change** — test only, confirming the deck edge diffracts as a barrier |
| `noisemodelling-jdbc` | optional `BRIDGE_POINTS` table loader in `DefaultTableLoader` |
| `noisemodelling-scripts` | optional `tableBridgePoints` input on `Noise_level_from_source`; `TutoBridge` example dataset |

Tests: pathfinder **249**, propagation **55** (incl. the 47 TC01–TC28 CNOSSOS
regression cases), jdbc **59**, scripts **96** — all green, no regression to
existing tests. `bridgeDeckShieldsAGroundReceiver` pins the per-octave
insertion loss to the CNOSSOS single-diffraction term for the path difference
derived from the geometry.

**Out (follow-up):**

- **Structure-radiated noise** (goal 2). Needs radiated-noise coefficients by
  construction type, which CNOSSOS has no equivalent for; I have an ASJ
  RTN-Model 2023 table for this on a separate branch. v0 models only the
  vehicles' own noise on the deck (unchanged CNOSSOS).
- **Deck-underside reflection** and mirror / imaginary sources for the open
  space under the structure (goal 3).
- Per-bridge-point deck height (v0 uses one height per deck); tutorial
  expansion with day/night maps.

## The reason this is a draft

Wiring a genuinely new obstacle type in the way every existing type is wired
means touching `ProfileBuilder` / `CutProfile` / `Scene` and adding accessors
on `CutPoint*`. Even this minimal version pulls in ~9 small self-contained
support classes plus those accessors, because the per-obstacle-type handling
in `ProfileBuilder` (~1650 lines) and `CutProfile` is interleaved in one place
— dedicated fields and R-trees, a fixed cut-point sequence in `getProfile()`,
`IntersectionType` branch points, and one `has…Intersection` boolean per type
in `isFreeField()` — with no extension point for a new type.

The open `perf_*` PRs in this area (`perf_profile_intersections`,
`perf_prepared_ground`, `remove_unused_wall_index`) are small surgical
optimizations — reuse one line-intersector across a profile's obstacle
candidates, prepare the ground footprints, drop a dead spatial index — and a
layer seam would rebase cleanly over them. But they do show that allocation in
`getProfile()`'s inner loop is measured here, so a route-B extraction would
need to demonstrate that per-layer dispatch adds no measurable cost on a large
map, not just that the tests stay green.

Before going further I'd like to agree on:

1. **Route A vs B.** (A) keep those classes as they are and take the feature as
   thin hooks (this PR), or (B) first do a focused, behaviour-preserving
   extraction of the per-obstacle-type handling behind a common interface, then
   add `BridgeService` alongside — larger up front, but it also gives the
   `perf_*` work a cleaner target. Same question, lighter, for
   `PathFinder` / `Scene`.
2. **Sequencing against the open `perf_*` PRs** — the overlap looks small (none
   of them touch the cut-point sequence or `isFreeField()`), but a route-B
   extraction should still land in an order the `perf_*` authors are happy with.
3. **Delivery shape** — one feature PR, or a stacked series (support types →
   bridge core → `CutProfile` hooks → JDBC → WPS + tutorial); own module / flag
   / part of the road workflow.
4. Whether bundling the **ASJ structural-noise table** later is acceptable, or
   the structural source should stay optional / user-supplied.

## What v1 will need from the core

This PR treats the deck as a single screen and rides the existing diffraction
path, so the propagation module is untouched. Full bridge diffraction cannot
stay that light: sound passing a deck **body** diffracts around its bottom
**and** top edge, and the boundary attenuation of that path is the sum of a
bottom-route and a top-route sub-path, each a normal Cnossos path built by a
re-entrant call on an edge-filtered profile. Supporting it means small,
bridge-guarded additions that do reach into the CNOSSOS core:

- two nullable child-path fields on `CnossosPath` (inert when null);
- a bottom+top decomposition step in `CnossosPathBuilder` / the path processor,
  entered only when the point list carries a bridge (`DIFB`) point;
- a matching branch in `AttenuationCnossos` boundary attenuation that returns
  `aBoundary(topRoute) + aBoundary(bottomRoute)` for such paths.

Each is guarded by a condition that is never true without a bridge in the
scene, so existing results are preserved by construction — but the change does
touch the class the TC01–TC28 cases pin, which is the main thing I'd want
agreement on before writing it. (This is far smaller than the fork's version,
which carries a full type-forked copy of the attenuation code for unrelated
reasons; the actual new physics is on the order of ~150 lines.)

## Notes

- Branched from `main` at `04793d5` (2026-08-14). Happy to rebase.
- `CutProfile.isFreeField()` now also returns false on a bridge intersection —
  without it `PathFinder` submits a truncated profile for a deck-shadowed
  receiver and the level collapses to the rejected-path floor.
- Unrelated: `Noise_level_from_source` swaps its two diffraction setters
  (`setComputeHorizontalDiffraction` / `setComputeVerticalDiffraction` are
  passed each other's argument), so `confDiffHorizontal` currently toggles
  vertical diffraction. The `TutoBridge` test works around it and documents it;
  can split a separate fix.
