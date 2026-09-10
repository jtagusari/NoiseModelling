# NoiseModelling Days — Talk Draft
Working title: **"Modelling Bridge and Elevated-Road Noise in NoiseModelling: CNOSSOS-EU + ASJ RTN-Model"**

> Draft outline for a ~10-slide talk. Each `##` is one slide: a short title, the on-slide bullets,
> and speaker notes in a blockquote. Numbers are pulled from `Docs-dev/bridge_v2_pr_body.md`,
> `bridge_v1_pr_body.md`, and `merge_project.md` — check them against the branches before printing.

---

## Slide 1 — Title

- Modelling Bridge and Elevated-Road Noise in NoiseModelling
- Integrating CNOSSOS-EU propagation with the ASJ RTN-Model 2023 structural-noise source
- [Your name], [affiliation]
- NoiseModelling Days [year]

> One line, spoken: "I'm going to show a feature branch that's ready to propose upstream: noise
> prediction for traffic on elevated bridges and viaducts, including the noise radiated by the
> structure itself."

---

## Slide 2 — Why bridges

- Elevated urban expressways and viaducts are a major noise source in dense cities
- NoiseModelling has no representation for them today:
  - no bridge deck geometry (diffraction / reflection off the structure)
  - no source model for **structure-radiated noise** — deck and girders vibrating under traffic
- For steel bridges in particular, structural noise can **dominate** at low frequency
- CNOSSOS-EU has no coefficients for this — it only knows tyre/powertrain noise

> Motivate with a concrete image if you have one: a viaduct over a residential street, the deck
> as a diffracting screen, and the "hum" of a steel bridge under traffic that CNOSSOS alone can't
> predict.

---

## Slide 3 — Two separate problems, one feature

- **Propagation**: the deck is a solid body above the terrain — it diffracts and reflects, and
  receivers under/beside it need a real path around it, not just raised source/receiver heights
- **Emission**: the structure itself radiates noise with a spectrum CNOSSOS doesn't model —
  needs bridge-type-specific coefficients (steel box girder, concrete box girder, plate girder, …)
- Contribution addresses both, as two source types on the same deck:
  - the vehicle's own CNOSSOS noise, at deck level
  - the deck/girder structural noise, from the ASJ RTN-Model 2023, at the deck underside

> This is the framing slide — everything after this is "how", so make sure the two-part
> structure (propagation geometry vs. new emission source) lands before moving on.

---

## Slide 4 — Propagation: the deck as an obstacle

- Bridge deck: explicit geometry input (deck line + width + thickness + barrier height per point),
  triangulated between points — not approximated by raising Z
- A source **on** the deck uses the deck surface as its ground reference (not the terrain far below)
- A source **under/beside** the deck gets:
  - a **mirror image** across the deck underside (the soffit reflecting road noise back down)
  - **bottom+top (BT) diffraction**: duck under the deck's lower edge, then back over the next
    obstacle's upper edge — a new diffraction path type CNOSSOS's upper-convex-hull construction
    doesn't produce on its own

> If you have a diagram of the BT path (source → under deck edge → over downstream wall →
> receiver) this is the slide to show it. It's the single hardest piece of physics in the whole
> contribution and worth a beat.

---

## Slide 5 — Emission: ASJ RTN-Model 2023 as a second source

- New `emission.road.asj` package: bridge-type-specific structural-noise coefficients
  (7 girder/slab construction types), from the Acoustical Society of Japan RTN-Model 2023
- Vehicles keep their existing CNOSSOS emission — this is purely **additive**
- `EMISSION_TYPE` routing: a source on a deck is emitted **twice**, same source id —
  - `ROAD` → traffic noise, at deck level
  - `BRIDGE` → structural noise, at the deck underside
  - a gate gives each emission row to the right point, no double-counting
- Effect is frequency-shaped: in one worked example, structural noise lifted the near-deck
  receiver's 63 Hz band by **~4.6 dB** while barely touching 4 kHz — the low-frequency signature
  steel bridges are known for

> The 4.6 dB / 63 Hz number is from the v1 PR body's worked example — restate it as "modest
> overall, but decisive for low-frequency assessment near the structure" rather than a big flashy
> number; the point is it's physically the right *shape*.

---

## Slide 6 — Engineering constraint: minimal upstream footprint

- Built against current `main`, not a rewrite: every existing class keeps its structure
  (`ProfileBuilder`, `CutProfile`, `CnossosPathBuilder`, `AttenuationCnossos`, …)
- New behaviour is added as **guarded branches**, entered only when bridge data is present:
  - no bridge input → identical code path, byte-identical output, to today's `main`
  - every new branch triggers only on a bridge relation / a `DIFB` point / `hasBridges()`
- No forked/duplicated "Ext" versions of core classes — same classes, same call sites

> This is the slide that answers "will this be a nightmare to review and maintain" before anyone
> asks it. Emphasize: reviewers can read the diff as "here is the bridge-only branch" everywhere,
> not "here is a parallel implementation to reconcile."

---

## Slide 7 — Validation

- Regression-tested against a **from-scratch NoiseModelling fork** that already had a full
  (differently-architected) bridge implementation running in production
- Golden test suite (`TBC*`): synthetic and real-DEM bridge scenes, fork's reference attenuation
  reproduced within **0.2 dB** (several cases **bit-for-bit**)
- Also validated on a **real dataset** — an actual elevated expressway (Seishin Bypass, Shizuoka,
  Japan) — not only synthetic geometry
- Full existing test suite stays green throughout: **emission 66 / pathfinder 252 / propagation
  65 / jdbc 62 / scripts 103**, zero regressions to any pre-existing test

> Have this slide ready to defend in Q&A — it's the evidence that "guarded branches" in slide 6
> actually held up, not just a design intention.

---

## Slide 8 — Status: ready to propose

- Feature built as **three stacked, incrementally reviewable PRs**:
  1. **v0** — deck as an over-the-top screen (no propagation changes)
  2. **v1** — on-deck source elevation + ASJ structural source (separate table)
  3. **v2** — deck-underside reflection + bottom/top diffraction + structural noise merged into
     the same emission-type routing as the traffic source
- All three: implemented, tested, draft PR bodies written
- This is a **discussion-and-review starting point**, not a take-it-or-leave-it patch — open to
  maintainer feedback on approach before/instead of merging as-is

> Say plainly that nothing is merged yet — this is the "here's what exists and what I'd like
> feedback on" slide, which sets up slide 10's call to action.

---

## Slide 9 — What's still open

- `Road_Emission_from_Traffic` doesn't yet emit both `ROAD` and `BRIDGE` rows in one pass from raw
  traffic-flow input (works today via a companion script + an optional `BRIDGE_PK` column)
- Multi-deck stacks (3+ levels, a source under all of them) — the single-edge case works, the
  general recursive case is an identified, documented limitation
- Favourable-condition (curved-path) diffraction for the new BT route is within ~0.05 dB of
  reference, not bit-exact — traced to a general upstream/fork difference in how curved
  propagation is modelled, not a bug in this feature
- Feedback wanted: is the guarded-branch approach (vs. a bigger core refactor) the right shape for
  this kind of feature going forward?

> Keep this short and confident — "known, documented, not blocking" — rather than apologetic.

---

## Slide 10 — Beyond this feature: an invitation

- The fork this was validated against also carries **broader pathfinder refactoring** —
  service-decomposed `ProfileBuilder`, a redesigned `CutProfile`, a decomposed `PathFinder`, a
  restructured `Scene` — done independently, for different reasons, over ~13 months of divergence
- Not proposed as a patch here — the scope is too large for one review, and upstream has been
  moving `ProfileBuilder`/`PathFinder`/`Scene` in its own direction (perf-focused, incremental)
- Raising it because **today's pathfinder core has no seam for a new obstacle type** — bridges
  needed guarded branches in four different classes; a future obstacle type will hit the same wall
- Open question for the room: is there appetite for a focused, behaviour-preserving extraction
  (services / a plugin point for obstacle types), independent of this bridge feature?

> This is the "plant a seed" slide, not a proposal — you're not asking for a decision today, just
> naming the friction so it's on the maintainers' radar next time someone hits it.

---

## Slide 11 — Thank you / links

- Fork: `github.com/jtagusari/NoiseModelling` (`fix` branch — reference implementation, TutoBridge
  dataset, ASJ coefficient source)
- Draft PRs: `feat/bridge` (v0) → `feat/bridge-v1` → `feat/bridge-v2`, stacked
- Contact: [your email]
- Questions / discussion welcome now or async on GitHub

> Have the fork URL and PR branch names on screen so people can find them without you dictating.

---

## Appendix / backup slides (not counted in the ~10)

Keep these ready but off the main deck, in case of specific questions:

- **A1 — BT diffraction detail**: the `spliceBridgeBottomEdge` / `computeBridgeBottomTopRoutes`
  mechanism, `DIFB` point type, `aBoundary` = `aBoundary(bottom) + aBoundary(top)` — for an
  audience that wants the CNOSSOS-equation-level explanation.
- **A2 — TutoBridge screenshots**: before/after noise map for the Seishin Bypass dataset, if you
  render one (`Docs-dev/merge_project.md` §4 mentions this as an optional deliverable — worth
  doing for the talk even if it wasn't needed for the PR).
- **A3 — TBC02 aside**: the one intentionally-not-matched golden case, and why upstream's CNOSSOS
  2.5.31/2.5.33 branch is the *more* correct value — good material if someone asks "did everything
  match exactly?".
- **A4 — Full test/regression table**: per-module before/after counts across v0/v1/v2, if someone
  wants the detail behind slide 7's headline numbers.
