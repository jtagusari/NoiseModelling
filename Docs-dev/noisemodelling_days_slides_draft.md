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

## Slide 4 — Propagation: sound can now pass *under* an obstacle

- Bridge deck: explicit geometry input (deck line + width + thickness + barrier height per point),
  triangulated between points — not approximated by raising Z
- A source **on** the deck uses the deck surface as its ground reference (not the terrain far below)
- **The core physical novelty**: in CNOSSOS-EU / NoiseModelling today, a sound ray only ever
  diffracts *over* an obstacle (built from the *upper* convex hull). A bridge is the first
  obstacle a ray can also pass **under** — a source below the deck (or its mirror image) can
  reach the receiver by going under the deck's lower edge
- That opens the possibility of **double diffraction on one path**: under the deck's lower edge,
  then back over the next obstacle's upper edge — both diffractions on the same ray
- To attenuate that, a **`CnossosPath` now holds two child `CnossosPath`s** — a "bottom" sub-route
  and a "top" sub-route — so the double diffraction is two ordinary single diffractions combined,
  each computed the normal way
- Combining the two contributions follows the **same multiple-diffraction approach the ASJ
  RTN-Model uses** — not a new ad hoc rule

> This is the single hardest piece of physics in the whole contribution and worth a full beat.
> If you have a diagram of the path (source under the deck → up over a downstream wall →
> receiver), and ideally a small sketch of `CnossosPath` containing `bottomRoute` /
> `topRoute` as child `CnossosPath`s, this is the slide to show them.

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

## Slide 6 — Validation

- Regression-tested against a **from-scratch NoiseModelling fork** that already had a full
  (differently-architected) bridge implementation running in production
- Golden test suite (`TBC*`): synthetic and real-DEM bridge scenes, fork's reference attenuation
  reproduced within **0.2 dB** (several cases **bit-for-bit**)
- Also validated on a **real dataset** — an actual elevated expressway (Seishin Bypass, Shizuoka,
  Japan) — not only synthetic geometry
- Full existing test suite stays green throughout: **emission 66 / pathfinder 252 / propagation
  65 / jdbc 62 / scripts 103**, zero regressions to any pre-existing test

> Have this slide ready to defend in Q&A — it's the evidence that the engineering approach on the
> next slide actually held up, not just a design intention.

---

## Slide 7 — How this was built

- The reference implementation (the `fix` fork) was developed over a long period on
  **NoiseModelling 5.x** as its base — a full, working bridge feature, but on an increasingly old
  `main` and, in places, a different internal architecture
- Porting that design onto **current `main`** was the remaining step — and this is where it gets
  interesting for this room: that port was carried out **with Claude, an AI coding agent**, in a
  single concentrated effort, rather than a manual multi-month rewrite
- The result, engineered against current `main`, keeps a hard constraint: every existing class
  keeps its structure (`ProfileBuilder`, `CutProfile`, `CnossosPathBuilder`, `AttenuationCnossos`, …)
  — new behaviour is added as **guarded branches**, entered only when bridge data is present:
  - no bridge input → identical code path, byte-identical output, to today's `main`
  - every new branch triggers only on a bridge relation / a `DIFB` point / `hasBridges()`
- No forked/duplicated "Ext" versions of core classes — same classes, same call sites; the
  original fork served only as the physics reference and the source of the golden test fixtures

> Frame this as a process point, not a gimmick: the design and physics validation took the long
> route (the original fork); reproducing that design cleanly against a moving upstream target,
> with full regression discipline, is what AI-assisted porting was good at. Reviewers can read
> the diff as "here is the bridge-only branch" everywhere, not "here is a parallel implementation
> to reconcile."

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

- **Multi-deck stacks**: a source under two or more stacked decks (3+ level viaducts) needs the
  bottom/top splice to recurse over several lower edges, not just one. Today's implementation
  handles a single edge (covers every case seen so far); the general recursive case is an
  identified, documented limitation, not yet built
- **Refactoring toward more maintainable code**: this feature's own code would benefit from the
  same kind of cleanup discussed next — clearer seams between the bridge-specific logic and the
  core CNOSSOS path building, so the next contributor doesn't have to read guard conditions to
  find where "bridge" begins and ends
- Smaller, non-blocking items: `Road_Emission_from_Traffic` doesn't yet emit `ROAD` and `BRIDGE`
  rows in one pass from raw traffic-flow input (works today via a companion script); favourable
  (curved-path) diffraction for the new route is within ~0.05 dB of reference, not bit-exact

> Keep this short and confident — "known, documented, not blocking" — rather than apologetic. The
> maintainability point is a deliberate bridge into the next slide.

---

## Slide 10 — Beyond this feature: an invitation

- The fork this was validated against also carries **broader pathfinder refactoring** —
  service-decomposed `ProfileBuilder`, a redesigned `CutProfile`, a decomposed `PathFinder`, a
  restructured `Scene` — done independently, for different reasons, over ~13 months of divergence,
  plus a substantial set of developer-facing documentation
- **Why it was done**: the code as it stood was hard to read, and there was no developer-facing
  documentation to orient a new contributor — the refactoring and the docs were written to fix
  that, not to change behaviour
- Not proposed as a patch here — the scope is too large for one review, and upstream has been
  moving `ProfileBuilder`/`PathFinder`/`Scene` in its own direction (perf-focused, incremental)
- Raising it because **today's pathfinder core has no seam for a new obstacle type** — bridges
  needed guarded branches in four different classes; a future obstacle type will hit the same wall
- Open question for the room: is there appetite for incremental, behaviour-preserving readability
  work and developer documentation — independent of, but motivated by, this bridge feature?

> This is the "plant a seed" slide, not a proposal — you're not asking for a decision today, just
> naming the friction (readability, missing docs, no seam for new obstacle types) so it's on the
> maintainers' radar next time someone hits it.

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
  wants the detail behind slide 6's headline numbers.
