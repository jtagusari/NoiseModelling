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
work but come later — the "Follow-up" section maps where they land.

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

**Out (follow-up):** structure-radiated noise (goal 2), deck-underside
reflection and mirror sources (goal 3), per-bridge-point deck height (v0 uses
one height per deck), tutorial expansion with day/night maps. Detail below.

## Follow-up (v1)

Not questions — nothing here changes existing results — just a map of where
the rest of the feature lands, so the shape of this PR makes sense.

`ProfileBuilder` / `CutProfile` have no extension point for a new obstacle
type, so even this minimal version adds ~9 small support classes and a few
accessors on `Scene` / `CutPoint*` / `Wall`. I'm not proposing to restructure
those classes.

**v1** stays additive and behaviour-preserving for any scene with no bridge
input — every new branch is guarded by a condition that cannot be true without
a bridge:

- **`CutProfile` elevation** gains ascend/descend handling guarded by
  `instanceof CutPointBridgeWall` — dead code when the profile carries no
  bridge cut point. This lets a source that sits *on* the deck get the right
  ground/elevation profile.
- **Per-bridge-point deck height** (v0 uses one height per deck).
- **Structure-radiated source**: an ASJ RTN-Model 2023 coefficient table for
  deck / girder radiated noise, added alongside the vehicles' CNOSSOS noise.
  Additive; existing emission models unchanged.

**v2** is receivers *below or beside* the structure — deck-underside
reflection, and diffraction around the deck body (under the lower edge, back
over the upper edge). `AttenuationCnossos` builds diffraction paths from the
*upper* convex hull of the profile, so an under-then-over route has no
representation there today; adding it is a real piece of work rather than a
guarded branch, so it is kept separate from v1.

Each step lands with a parity harness (full per-receiver / per-band equality
against `main` on non-bridge scenes) and JMH figures showing the guarded
branches cost nothing when inactive. Feedback on any of it is welcome.

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
