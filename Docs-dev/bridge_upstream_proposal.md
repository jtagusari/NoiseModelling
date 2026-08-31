# Bridge upstream 提案 — 論点メモ（v0 PR の背景説明に流用）

> **2026-08-31 方針変更:** 単発の抽象 Discussion としては投稿しない。動く **Bridge v0** を現行 `main` の上に
> 最小差分で実装し、その **draft PR の説明**に下記の論点（pathfinder コアの拡張性・`perf_*` PR との順序・
> v1 で足すもの）を要約して載せる。以下は素材として保持。
>
> ---
>
> （以下、当初の Discussion 下書き。投稿するとしても v0 PR とセットで。）
> 種別は **Discussion（Ideas カテゴリ）** を想定（Issue ではなく）。投稿は本人が行う。
> 目的 = Bridge 機能を入れる前提で、**pathfinder コア（`ProfileBuilder` / `CutProfile` / `PathFinder` /
> `Scene`）が障害物タイプ拡張に対して閉じている**という設計問題を提起し、focused な
> behaviour-preserving 抽出（route B）を許容するか、薄い hook（route A）で通すかの合意を得る。
> 進行中の `perf_*` PR との順序も相談。「機能が必要か」は問わない（必要という前提）。
> **主眼は Bridge 機能。** 高架構造では**構造物音（デッキ・桁の放射音）が支配的**になり得るので、
> ASJ RTN-Model 2023 の橋梁形式別係数（`emission.road.asj`）を構造物音源の係数表として同梱する。
> 車両走行音は既存 CNOSSOS のまま。同梱の可否は Q6 で相談。一人称は "I"。投稿本文は英語。

---

**Title:** Proposal: bridge / elevated-road noise modelling — integration guidance wanted

**Category:** Ideas

---

Hi maintainers,

I've been developing a **bridge / elevated-road noise modelling** capability on
a fork (`github.com/jtagusari/NoiseModelling`) and want to contribute it back.

The reason I'm opening a discussion rather than a PR straight away: adding this
cleanly is hard against the current pathfinder core. `ProfileBuilder`,
`CutProfile`, and to a lesser extent `PathFinder` / `Scene` each carry the
handling for every obstacle type inline, and there's no seam to add a new one.
Before I write the integration I'd like to agree on **how much structural
change to those classes you're willing to take**.

## 1. The feature

The ability to model noise from traffic running on an **elevated deck**
(viaduct, urban expressway):

- the bridge deck is an explicit input with its own geometry, modelled as a
  solid body — not approximated by raising source / receiver Z;
- the deck sits well above the DEM and acts as a diffracting / reflecting
  obstacle; the ground under and beside it is handled;
- for an elevated structure the noise **radiated by the structure itself**
  (deck and girders vibrating under traffic) can be the dominant component,
  especially for steel bridges — the feature adds this as a structural source
  in addition to the vehicles' own noise.

Elevated urban expressways are a major noise source in dense cities and are not
representable in NoiseModelling today.

### Status

The feature is **implemented and working on a fork based on NoiseModelling
5.0.1** (`jtagusari/NoiseModelling`, `fix` branch). It adds a bridge object and
its handling in the pathfinder / propagation modules, bridge-aware branches in
`ProfileBuilder` / `CutProfile` / `Scene`, a set of regression test cases, and
a `TutoBridge` example dataset with day/night maps.

Current `main` (6.0.2-SNAPSHOT) has since reworked parts of the pathfinder and
propagation core, so the feature needs porting forward — how to do that
cleanly is what this discussion is about.

### Emission for the bridge sources

The structural source needs radiated-noise coefficients by bridge construction
type (steel box girder, concrete box girder, plate girder, …), which CNOSSOS
does not provide. The contribution ships these as a small package
(`emission.road.asj`: `RoadAsj`, `RoadAsjParameters`, `RoadAsj_2023.json`, 38
tests, additive), taken from the Acoustical Society of Japan RTN-Model 2023.
The vehicles' own tyre/powertrain noise on the deck can still use the existing
CNOSSOS model. Whether bundling the ASJ structural coefficients is acceptable
is a design point (question 6).

## 2. The pathfinder core is hard to extend

`ProfileBuilder` (~1650 lines) holds the loading, spatial indexing and
cut-profile handling for every obstacle type — buildings, walls, ground,
topography — interleaved in one class, with `IntersectionType` switch points
running through it and through `CutProfile`. `PathFinder` and `Scene` are
similarly broad. There is no seam to add a new obstacle type.

A bridge deck is a new obstacle type: it occludes and reflects like a building
but sits above the ground with open space under it, and sources are placed on
its surface. Wiring it in the way every existing type is wired means adding
bridge branches to `ProfileBuilder`, `CutProfile`, `Scene`, and new accessors
on `CutPointSource` / `CutPointWall` / `CutPoint`.

This friction isn't specific to bridges — the open `perf_*` PRs
(`perf_profile_intersections`, `remove_unused_wall_index`,
`perf_prepared_ground`, `perf_vedge_hull`, …) are all working around the same
classes.

On the fork these classes were restructured — `ProfileBuilder` split so each
obstacle type is a small service with a common lifecycle, `PathFinder` and
`Scene` slimmed down — and `BridgeService` is then just one more service. I'm
**not** asking you to take that restructuring. I want to agree on how much
structural change to `ProfileBuilder` / `CutProfile` / `PathFinder` / `Scene`
you'd accept to make room for the feature. Routes:

- **(A) Minimal change.** Add the bridge feature with the smallest possible set
  of hooks into the classes as they are today. A first experiment shows this
  still pulls in ~9 small self-contained support classes and the new accessors
  above — so it is not free either.
- **(B) A focused extraction first.** As its own behaviour-preserving PR(s),
  pull the per-obstacle-type handling in `ProfileBuilder` / `CutProfile` behind
  a common interface (arguably worth doing on its own, and it gives the
  `perf_*` work a cleaner target), then add `BridgeService` alongside. Larger
  up front; needs coordinating with the open `perf_*` PRs.

## 3. What I'd like to hear from you

1. Do you see the `ProfileBuilder` / `CutProfile` monolith as worth a
   **focused, behaviour-preserving extraction** in its own right (route B), or
   would you rather keep those classes as they are and take the bridge feature
   as thin hooks (route A)? Same question, lighter, for `PathFinder` / `Scene`.
2. If A: how much change to `ProfileBuilder` / `CutProfile` / `Scene` /
   `CutPoint*` are you comfortable with in one feature PR?
3. How should this be **sequenced against the open `perf_*` PRs** so we don't
   step on each other? (a B-style extraction would touch the same code they do)
4. Preferred delivery: one feature PR, or a stacked series (support types →
   bridge core → `CutProfile` hooks → JDBC wiring → WPS script + tutorial)?
5. Any preference on the feature's shape — its own module, behind a flag,
   part of the existing road workflow?
6. The bridge structural source needs radiated-noise coefficients by
   construction type, which CNOSSOS has no equivalent for. Is bundling the ASJ
   RTN-Model 2023 table (`emission.road.asj`) for that acceptable, or would you
   rather the structural source be optional / left to the user?

I can provide a draft PR, the `TutoBridge` dataset, and before/after maps to
make the feature concrete. Thanks for taking a look.

---

## メモ（投稿前に詰めること）

- スクリーンショット/before-after ノイズマップを 1〜2 枚用意すると通りが良い（`TutoBridge` から生成）。
- ASJ = 橋梁形式別の**構造物音（放射音）係数表**。CNOSSOS に相当なし。車両走行音は CNOSSOS のまま。同梱可否は Q6。
- `feat/road-asj` は未 push。Discussion 投稿時に push して URL を貼る。
- `RoadAsj_2023.json` は STEEL_BOX_STEEL / CONCRETE_BOX_CONCRETE など 7 形式 + 構造物音の周波数係数を収録。
- 投稿相手の主なアクティブ人物: nicolas-f, pierromond, sachabaclet, gpetit, mglesser。
- 返答パターン別の分岐は [merge_project.md](merge_project.md) §3.5「推奨: まず D」を参照。
