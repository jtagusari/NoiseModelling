# B0: upstream 設計 Discussion のドラフト

> これは upstream（Universite-Gustave-Eiffel/NoiseModelling）に投稿するための**下書き**。
> 種別は **Discussion（Ideas カテゴリ）** を想定（Issue ではなく）。投稿は本人が行う。
> 目的 = Bridge 機能 + ASJ 排出モデルを mainline に入れたい旨を伝え、`ProfileBuilder` への
> 組み込み方（薄い hook か、サービス分解か）と、進行中の `perf_*` PR との順序について合意を得る。
> 投稿本文は英語。以下そのまま貼れる形。

---

**Title:** Proposal: bridge / elevated-road noise modelling + ASJ (Japan) road emission model — integration guidance wanted

**Category:** Ideas

---

Hi maintainers,

We have been developing two additions to NoiseModelling on a fork
(`github.com/jtagusari/NoiseModelling`) and would like to contribute them back.
Before opening PRs we'd like your view on scope and on how to integrate the
pathfinder part, since `ProfileBuilder` is being actively worked on right now.

## 1. What the additions are

### a) ASJ road emission model (`noisemodelling-emission`)

An implementation of the Acoustical Society of Japan **RTN-Model 2023** road
traffic emission method, as a new package `emission.road.asj`
(`RoadAsj`, `RoadAsjParameters`, coefficient table `RoadAsj_2023.json`), with
38 unit tests. It is purely additive — no change to existing emission classes.
It is the emission side used by the bridge feature below, but it also stands on
its own as an alternative to the CNOSSOS road model for users working with
Japanese methods.

Branch: `feat/road-asj` (rebased on current `main`, `mvn -pl
noisemodelling-emission test` green on JDK 25).

### b) Bridge / elevated-road noise modelling (`noisemodelling-pathfinder` + propagation)

The ability to model noise from traffic running on an **elevated deck**
(viaduct, urban expressway): the deck geometry is a first-class object, sources
can be placed on the deck (well above the DEM), the deck acts as a
diffracting/reflecting body, and the area under and beside the structure is
handled. This is a common and currently unrepresented case — CNOSSOS tools
place sources at `DEM + height`, which cannot express a road 10–20 m up on a
structure.

Implementation (on the fork): ~13 new pathfinder classes
(`Bridge`, `BridgeService`, `BridgePoint`, `BridgePointManager`,
`BridgeTriangulation`, `BridgeGeometryBuilder`, `BridgeQueryHelper`,
`CutPointBridgeWall`, `BridgeAnalyzer`, `ElevationConverter`,
`LineStringSplitter`, `BridgeRelationship`, …), plus bridge-aware branches in
`ProfileBuilder` / `CutProfile` / `Scene`, a set of regression test cases
(`TBC*`), and a `TutoBridge` example dataset.

## 2. The integration question

The fork's bridge code was written on top of a **decomposed `ProfileBuilder`**
— building / wall / ground / topography / processed-wall / bridge each pulled
into their own service object. `BridgeService` is one peer among those.

To land the bridge feature on current `main` we see two routes:

- **(A) Thin integration.** Keep `ProfileBuilder` as it is today and add the
  bridge feature with the smallest possible set of hooks (a `bridgeService`
  field, an `IntersectionType.BRIDGE`, bridge-intersection handling in
  `CutProfile`). Smaller, more reviewable; the bridge classes still need a
  handful of small support types.
- **(B) Decompose first.** Upstream a behaviour-preserving `ProfileBuilder`
  service split as its own step, then add `BridgeService` alongside the others.
  Cleaner end state; much larger; and it would collide with the open `perf_*`
  PRs (`perf_profile_intersections`, `remove_unused_wall_index`,
  `perf_prepared_ground`, …).

We lean towards **(A)**, but a first experiment showed the bridge classes also
need ~9 small self-contained support classes (frequency-config /
geometry-factory / rtree helpers) and a few new accessors on `CutProfile` /
`CutPointSource` / `CutPoint`. So even (A) is not completely free of touching
the existing pathfinder types.

## 3. What we'd like to hear from you

1. Is **bridge / elevated-road modelling** something you want in mainline at
   all? If yes, roughly what shape (own module? behind a flag? …)?
2. Is the **ASJ emission model** welcome as a standalone additive PR now,
   independent of the bridge work?
3. **(A) vs (B)** for the `ProfileBuilder` integration — and if (A), how much
   change to the existing pathfinder types are you comfortable with in one PR?
4. How should this be **sequenced against the open `perf_*` PRs** so we don't
   step on each other?
5. Preferred delivery: one feature PR, or a stacked series (support types →
   bridge core → `CutProfile` hooks → JDBC wiring → WPS script + tutorial)?

We can provide a draft PR, the `TutoBridge` dataset, and before/after maps to
make the feature concrete. Thanks for taking a look.

---

## メモ（投稿前に詰めること）

- スクリーンショット/before-after ノイズマップを 1〜2 枚用意すると通りが良い（`TutoBridge` から生成）。
- ASJ を「日本メソッド」と前面に出すか、「任意の追加排出モデルの一例」と位置づけるか。前者は限定的に見える。
- `feat/road-asj` は push 済みか要確認（未 push）。Discussion 投稿と同時に push すると URL を貼れる。
- 投稿相手の主なアクティブ人物: nicolas-f, pierromond, sachabaclet, gpetit, mglesser。
- 返答パターン別の分岐は [merge_project.md](merge_project.md) §3.5「推奨: まず D」を参照。
