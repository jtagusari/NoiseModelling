# B0: upstream 設計 Discussion のドラフト

> これは upstream（Universite-Gustave-Eiffel/NoiseModelling）に投稿するための**下書き**。
> 種別は **Discussion（Ideas カテゴリ）** を想定（Issue ではなく）。投稿は本人が行う。
> 目的 = Bridge（高架道路）ノイズモデリング機能を mainline に入れたい旨を伝え、`ProfileBuilder` への
> 組み込み方（薄い hook か、サービス分解か）と、進行中の `perf_*` PR との順序について合意を得る。
> **主眼は Bridge 機能。** 高架構造では**構造物音（デッキ・桁の放射音）が支配的**になり得るので、
> ASJ RTN-Model 2023 の橋梁形式別係数（`emission.road.asj`）を構造物音源の係数表として同梱する。
> 車両走行音は既存 CNOSSOS のまま。同梱の可否は Q5 で相談。一人称は "I"。投稿本文は英語。

---

**Title:** Proposal: bridge / elevated-road noise modelling — integration guidance wanted

**Category:** Ideas

---

Hi maintainers,

I've been developing a **bridge / elevated-road noise modelling** capability on
a fork (`github.com/jtagusari/NoiseModelling`) and would like to contribute it
back. Before opening a PR I'd like your view on scope and on how to integrate
the pathfinder part, since `ProfileBuilder` is being actively worked on right
now.

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
is a design point (question 5).

## 2. The integration question

The fork's bridge code was written on top of a **decomposed `ProfileBuilder`**
— building / wall / ground / topography / processed-wall / bridge each pulled
into their own service object. `BridgeService` is one peer among those.

To land the feature on current `main` I see two routes:

- **(A) Thin integration.** Keep `ProfileBuilder` as it is today and add the
  bridge feature with the smallest possible set of hooks (a `bridgeService`
  field, an `IntersectionType.BRIDGE`, bridge-intersection handling in
  `CutProfile`). Smaller, more reviewable.
- **(B) Decompose first.** Upstream a behaviour-preserving `ProfileBuilder`
  service split as its own step, then add `BridgeService` alongside the others.
  Cleaner end state; much larger; and it would collide with the open `perf_*`
  PRs (`perf_profile_intersections`, `remove_unused_wall_index`,
  `perf_prepared_ground`, …).

I lean towards **(A)**, but a first experiment showed the bridge classes also
pull in ~9 small self-contained support classes (frequency-config /
geometry-factory / rtree helpers) and need a few new accessors on `CutProfile`
/ `CutPointSource` / `CutPoint`. So even (A) touches the existing pathfinder
types somewhat.

## 3. What I'd like to hear from you

1. Is **bridge / elevated-road modelling** something you want in mainline at
   all? If yes, roughly what shape (own module? behind a flag? …)?
2. **(A) vs (B)** for the `ProfileBuilder` integration — and if (A), how much
   change to the existing pathfinder types are you comfortable with in one PR?
3. How should this be **sequenced against the open `perf_*` PRs** so we don't
   step on each other?
4. Preferred delivery: one feature PR, or a stacked series (support types →
   bridge core → `CutProfile` hooks → JDBC wiring → WPS script + tutorial)?
5. The bridge structural source needs radiated-noise coefficients by
   construction type, which CNOSSOS has no equivalent for. Is bundling the ASJ
   RTN-Model 2023 table (`emission.road.asj`) for that acceptable, or would you
   rather the structural source be optional / left to the user?

I can provide a draft PR, the `TutoBridge` dataset, and before/after maps to
make the feature concrete. Thanks for taking a look.

---

## メモ（投稿前に詰めること）

- スクリーンショット/before-after ノイズマップを 1〜2 枚用意すると通りが良い（`TutoBridge` から生成）。
- ASJ = 橋梁形式別の**構造物音（放射音）係数表**。CNOSSOS に相当なし。車両走行音は CNOSSOS のまま。同梱可否は Q5。
- `feat/road-asj` は未 push。Discussion 投稿時に push して URL を貼る。
- `RoadAsj_2023.json` は STEEL_BOX_STEEL / CONCRETE_BOX_CONCRETE など 7 形式 + 構造物音の周波数係数を収録。
- 投稿相手の主なアクティブ人物: nicolas-f, pierromond, sachabaclet, gpetit, mglesser。
- 返答パターン別の分岐は [merge_project.md](merge_project.md) §3.5「推奨: まず D」を参照。
