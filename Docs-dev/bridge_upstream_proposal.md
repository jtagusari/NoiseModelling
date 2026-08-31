# B0: upstream 設計 Discussion のドラフト

> これは upstream（Universite-Gustave-Eiffel/NoiseModelling）に投稿するための**下書き**。
> 種別は **Discussion（Ideas カテゴリ）** を想定（Issue ではなく）。投稿は本人が行う。
> 目的 = ① Bridge（高架道路）ノイズモデリング機能を mainline に入れたい旨、②その前提として
> **`ProfileBuilder` が障害物タイプ拡張に対して閉じている**という設計問題を提起し、focused な
> behaviour-preserving 抽出（route B）を許容するか、薄い hook（route A）で通すかの合意を得る。
> 進行中の `perf_*` PR との順序も相談。
> **主眼は Bridge 機能。** 高架構造では**構造物音（デッキ・桁の放射音）が支配的**になり得るので、
> ASJ RTN-Model 2023 の橋梁形式別係数（`emission.road.asj`）を構造物音源の係数表として同梱する。
> 車両走行音は既存 CNOSSOS のまま。同梱の可否は Q6 で相談。一人称は "I"。投稿本文は英語。

---

**Title:** Proposal: bridge / elevated-road noise modelling — integration guidance wanted

**Category:** Ideas

---

Hi maintainers,

I've been developing a **bridge / elevated-road noise modelling** capability on
a fork (`github.com/jtagusari/NoiseModelling`) and would like to contribute it
back. Two things to discuss before I open a PR: whether the feature is wanted,
and — because adding it cleanly is hard against the current `ProfileBuilder` —
whether a small structural change to `ProfileBuilder` would be welcome.

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

### What the change touches

Pathfinder / propagation: ~13 new classes
(`Bridge`, `BridgeService`, `BridgePoint`, `BridgePointManager`,
`BridgeTriangulation`, `BridgeGeometryBuilder`, `BridgeQueryHelper`,
`CutPointBridgeWall`, `BridgeAnalyzer`, `ElevationConverter`,
`LineStringSplitter`, `BridgeRelationship`, …), plus bridge-aware branches in
`ProfileBuilder` / `CutProfile` / `Scene`, regression test cases (`TBC*`), and a
`TutoBridge` example dataset.

### Emission for the bridge sources

The structural source needs radiated-noise coefficients by bridge construction
type (steel box girder, concrete box girder, plate girder, …), which CNOSSOS
does not provide. The contribution ships these as a small package
(`emission.road.asj`: `RoadAsj`, `RoadAsjParameters`, `RoadAsj_2023.json`, 38
tests, additive), taken from the Acoustical Society of Japan RTN-Model 2023.
The vehicles' own tyre/powertrain noise on the deck can still use the existing
CNOSSOS model. Whether bundling the ASJ structural coefficients is acceptable
is a design point (question 6).

## 2. The `ProfileBuilder` problem

`ProfileBuilder` (~1650 lines) currently holds the loading, spatial indexing
and cut-profile handling for every obstacle type — buildings, walls, ground,
topography — interleaved in one class, with `IntersectionType` switch points
scattered through it and through `CutProfile`. There is no seam for adding a
new obstacle type.

A bridge deck is a new obstacle type: it occludes and reflects like a building
but sits above the ground with open space under it, and sources are placed on
its surface. Adding it means threading bridge-specific code through
`ProfileBuilder` and `CutProfile` the same way every existing type is threaded
— which makes an already hard-to-follow class harder.

This isn't only a bridge problem: the open `perf_*` PRs
(`perf_profile_intersections`, `remove_unused_wall_index`,
`perf_prepared_ground`, `perf_vedge_hull`, …) are all working around the same
monolith.

On the fork, `ProfileBuilder` was split so each obstacle type
(building / wall / ground / topography / bridge) is a small service with a
common lifecycle. `BridgeService` is then just one more service. I'm **not**
asking you to adopt that whole split, but I'd like to know how much structural
change you'd accept. Routes I see:

- **(A) No structural change.** Add the bridge feature with the smallest
  possible set of hooks into today's `ProfileBuilder` / `CutProfile`
  (a `bridgeService` field, `IntersectionType.BRIDGE`, bridge-intersection
  handling in `CutProfile`). A first experiment shows this still pulls in ~9
  small self-contained support classes and needs a few new accessors on
  `CutProfile` / `CutPointSource` / `CutPoint` — so it is not free either.
- **(B) A focused extraction first.** Pull the per-obstacle-type handling
  behind a common interface as its own behaviour-preserving PR (useful on its
  own merits, and it would give the `perf_*` work a cleaner target), then add
  `BridgeService` alongside. Larger up front; needs coordinating with the open
  `perf_*` PRs.

## 3. What I'd like to hear from you

1. Is **bridge / elevated-road modelling** something you want in mainline at
   all? If yes, roughly what shape (own module? behind a flag? …)?
2. Do you see the `ProfileBuilder` monolith as a problem worth a **focused,
   behaviour-preserving extraction** (route B) — independently of the bridge
   work — or would you rather keep it as is and take the bridge feature as
   thin hooks (route A)?
3. If A: how much change to `ProfileBuilder` / `CutProfile` / `CutPoint*` are
   you comfortable with in one feature PR?
4. How should this be **sequenced against the open `perf_*` PRs** so we don't
   step on each other? (a B-style extraction would touch the same code they do)
5. Preferred delivery: one feature PR, or a stacked series (support types →
   bridge core → `CutProfile` hooks → JDBC wiring → WPS script + tutorial)?
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
