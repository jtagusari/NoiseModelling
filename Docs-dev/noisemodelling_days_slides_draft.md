# NoiseModelling Days — Talk Draft
Working title: **"Modelling Bridge and Elevated-Road Noise in NoiseModelling: CNOSSOS-EU + ASJ RTN-Model"**

> Draft outline for a ~10-slide talk. Each `##` is one slide: a short title, the on-slide bullets,
> and speaker notes in a blockquote. Numbers are pulled from `Docs-dev/bridge_v2_pr_body.md`,
> `bridge_v1_pr_body.md`, `bridge_v1_core_analysis.md`, and `merge_project.md` — check them
> against the branches before printing.

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
- NoiseModelling has no representation for them today. Three gaps, all missing at once:
  - no bridge deck geometry — nothing to diffract or reflect off
  - no way to place a source **at the bridge's own height** — a source could only be positioned
    relative to the ground far below, never relative to a deck
  - no source model for **structure-radiated noise** — the structure itself also radiates noise,
    and that needs to be modelled too
- CNOSSOS-EU has no coefficients for structural noise at all — only tyre/powertrain noise

> Keep this factual and short — three concrete missing pieces, not a debate about which bridge
> type is worse. The "structure also radiates noise" point is deliberately understated here; the
> frequency-shape detail belongs on the structural-noise slide, not this one.

---

## Slide 3 — A source on the bridge deck

- A source physically on the deck needs the **deck surface as its ground reference**, not the
  terrain far below — otherwise height-above-ground (and everything downstream of it: ground
  effect, diffraction geometry) is simply wrong
- That requires the deck to be a real geometry input: a line + width + thickness + barrier height
  per point, **triangulated** between points so a source anywhere on a curved or sloped deck
  still resolves to the right local height
- This is the most basic of the three scenarios, but it was **entirely unsupported before** —
  there was no notion of "this source's ground is a deck, not the terrain"

> Keep this one short and concrete — it's the baseline case that the next two slides build on.

---

## Slide 4 — Structural noise: a second source on the same deck

- The deck and girders **radiate noise themselves**, vibrating under traffic — CNOSSOS has no
  coefficients for this, so it needs a source model of its own
- New `emission.road.asj` package: bridge-type-specific structural-noise coefficients
  (7 girder/slab construction types), from the Acoustical Society of Japan RTN-Model 2023
- Vehicles keep their existing CNOSSOS emission — this is purely **additive**: `EMISSION_TYPE`
  routing lets one source id emit twice, `ROAD` (traffic, at deck level) and `BRIDGE` (structural,
  at the deck underside), gated so each spectrum reaches only its own point — no double-counting
- Effect is frequency-shaped: in one worked example, the structural contribution lifted a
  near-deck receiver's 63 Hz band by **~4.6 dB** while barely touching 4 kHz — the low-frequency
  character structural (steel-bridge) noise is known for

> This is where the low-frequency detail belongs, as supporting evidence, not the headline of
> slide 2. Keep the framing as "here's the shape of the effect", not "steel bridges are a problem".

---

## Slide 5 — A source under the bridge: reflection and double diffraction

- **The core physical novelty**: in CNOSSOS-EU / NoiseModelling today, a sound ray only ever
  diffracts *over* an obstacle (built from the *upper* convex hull). A bridge deck is the first
  obstacle a ray can also pass **under**
- The deck underside also **reflects** sound back down — a source below or beside the deck gets a
  mirror image across the soffit, same as any other reflecting surface
- Going under the deck's lower edge can put another obstacle in the way further along the path —
  **double diffraction on a single ray**: under the deck, then back over the next obstacle
- To attenuate that, a **`CnossosPath` now holds two child `CnossosPath`s** — a "bottom" sub-route
  and a "top" sub-route — so the double diffraction is two ordinary single diffractions combined,
  each computed the normal way; combining them follows the **same multiple-diffraction approach
  the ASJ RTN-Model uses**, not a new ad hoc rule

> This is the single hardest piece of physics in the whole contribution and worth a full beat. If
> you have a diagram of the path (source under the deck → up over a downstream wall → receiver),
> and ideally a small sketch of `CnossosPath` containing `bottomRoute` / `topRoute` as child
> `CnossosPath`s, this is the slide to show them.

---

## Slide 6 — How this was built

- The reference implementation (the `fix` fork) started from **NoiseModelling 5.x** and grew from
  there — not a from-scratch design, but a long period of hands-on work on a real base
- Along the way, understanding the existing pathfinder code well enough to add bridges to it
  meant **refactoring it and writing developer documentation as a way of understanding it** —
  the feature grew together with that understanding, not after it
- Once the design was proven that way, the remaining step was porting it onto **current `main`**
  — and this is where it gets interesting for this room: that port was carried out **with Claude,
  an AI coding agent**, in a single concentrated effort, rather than a manual multi-month rewrite

> Frame this as a process point, not a gimmick: the design and physics validation took the long
> route (real development, real refactoring, real docs, on the original fork); reproducing that
> design cleanly against a moving upstream target, with full regression discipline, is what
> AI-assisted porting was good at.

---

## Slide 7 — Status: ready to propose

- Feature built as **three stacked, incrementally reviewable PRs**:
  1. **v0** — deck as an over-the-top screen (no propagation changes)
  2. **v1** — on-deck source elevation + ASJ structural source (separate table)
  3. **v2** — deck-underside reflection + bottom/top diffraction + structural noise merged into
     the same emission-type routing as the traffic source
- Built as **guarded branches** on top of current `main`: no bridge input means byte-identical
  behaviour to today, and no forked/duplicated core classes to reconcile later
- All three: implemented, tested, draft PR bodies written
- This is a **discussion-and-review starting point**, not a take-it-or-leave-it patch — open to
  maintainer feedback on approach before/instead of merging as-is

> Say plainly that nothing is merged yet — this is the "here's what exists and what I'd like
> feedback on" slide.

---

## Slide 8 — What's still open

- **Multi-deck stacks**: a source under two or more stacked decks (3+ level viaducts) needs the
  bottom/top splice to recurse over several lower edges, not just one. Today's implementation
  handles a single edge (covers every case seen so far); the general recursive case is an
  identified, documented limitation, not yet built
- **Refactoring toward more maintainable code**: this feature's own code would benefit from the
  same kind of cleanup discussed later — clearer seams between the bridge-specific logic and the
  core CNOSSOS path building

> Keep this short and confident — "known, documented, not blocking" — rather than apologetic.

---

## Slide 9 — Measured vs. predicted

- [Placeholder] Comparison between predicted levels (this implementation) and field measurements
  near an elevated bridge/viaduct
- Being prepared separately, outside this repository — fill in once available
- What it should show: predicted vs. measured spectra or overall levels at one or more receivers,
  ideally including the low-frequency band where the structural-noise contribution matters most

> This slide is the credibility anchor for the acoustics audience — physics and code discipline
> convince the developers in the room, a real measurement comparison convinces the acousticians.
> Don't skip it even if it only has one receiver.

---

## Slide 10 — Beyond this feature: what the refactoring actually bought

- Two concrete examples from the reference fork, not a general "cleaner code" claim:
  - **One class, one job**: `PathFinder` — the class that runs the whole ray-tracing loop — went
    from **1142 lines doing everything** to **254 lines**, with the rest extracted into ~9 focused
    classes (`ReceiverProcessor`, `SourceCollector`, `DiffractionPathBuilder`,
    `ReflectionPathBuilder`, …), each owning one responsibility instead of one class owning all of
    them
  - **Narrower public surface**: `ProfileBuilder` — previously one class fielding every obstacle
    type's logic through a wide public API — split into focused services (building / wall /
    ground / topography), each exposing only what its own callers need
- Not proposed as a patch here — the scope is too large for one review, and upstream has been
  moving `ProfileBuilder`/`PathFinder`/`Scene` in its own direction (perf-focused, incremental)
- Raising it because **today's pathfinder core has no seam for a new obstacle type** — bridges
  needed guarded branches in four different classes; a future obstacle type will hit the same wall
- Open question for the room: is there appetite for incremental, behaviour-preserving readability
  work — fewer public methods per class, one responsibility per class — independent of this
  bridge feature?

> Anchor the numbers (1142→254, "one class → ~9 classes") — a specific before/after is far more
> convincing than "the code is more readable now". These are the two pain points worth naming if
> asked "why refactor at all": too many public methods per class, and one class doing too much.

---

## Slide 11 — Conclusion

- Bridges and elevated roads are a real, previously unmodelled noise source in NoiseModelling:
  deck geometry, on-deck sources, structural noise (ASJ RTN-Model), and — the hard part —
  reflection and double diffraction for sources under or beside the deck
- Built as guarded, additive branches on current `main`: existing scenes are untouched, nothing
  is a fork of a core class
- The design was proven over a long development on NoiseModelling 5.x, then ported onto current
  `main` with AI-assisted coding in a single concentrated pass — three stacked PRs, ready for
  review and discussion, not a finished decision
- Open items: multi-deck stacks, code-quality follow-up, and a broader question about whether the
  pathfinder core should grow a seam for new obstacle types
- Fork: `github.com/jtagusari/NoiseModelling` (`fix` branch) · Draft PRs: `feat/bridge` (v0) →
  `feat/bridge-v1` → `feat/bridge-v2` · Contact: [your email]

> Close by inviting discussion now or async on GitHub — this is a summary slide, not a new point,
> so keep it tight and let it double as the "questions?" slide.

---

## Appendix / backup slides (not counted in the ~10)

Keep these ready but off the main deck, in case of specific questions:

- **A1 — BT diffraction detail**: the `spliceBridgeBottomEdge` / `computeBridgeBottomTopRoutes`
  mechanism, `DIFB` point type, `aBoundary` = `aBoundary(bottom) + aBoundary(top)` — for an
  audience that wants the CNOSSOS-equation-level explanation behind slide 5.
- **A2 — TutoBridge screenshots**: before/after noise map for a real elevated-expressway dataset
  (Seishin Bypass, Shizuoka, Japan), if you render one (`Docs-dev/merge_project.md` §4 mentions
  this as an optional deliverable).
- **A3 — Matching the fork's golden test cases**: how the implementation was checked against the
  reference fork's own bridge test suite (`TBC*`) — reproduced within 0.2 dB, several bit-for-bit
  — for anyone who asks how correctness was actually verified before proposing this.
- **A4 — TBC02 aside**: one intentionally-not-matched golden case, and why upstream's CNOSSOS
  2.5.31/2.5.33 branch is the *more* correct value there — good material if someone asks "did
  everything match the fork exactly?".
- **A5 — Full regression counts**: emission 66 / pathfinder 252 / propagation 65 / jdbc 62 /
  scripts 103 tests, zero regressions to any pre-existing test, across v0/v1/v2 — the detail
  behind the "guarded branches" claim on slide 7.
