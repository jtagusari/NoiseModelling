# B0: upstream 設計 Discussion のドラフト

> これは upstream（Universite-Gustave-Eiffel/NoiseModelling）に投稿するための**下書き**。
> 種別は **Discussion（Ideas カテゴリ）** を想定（Issue ではなく）。投稿は本人が行う。
> 目的 = Bridge（高架道路）ノイズモデリング機能を mainline に入れたい旨を伝え、`ProfileBuilder` への
> 組み込み方（薄い hook か、サービス分解か）と、進行中の `perf_*` PR との順序について合意を得る。
> **追加したいのは Bridge 機能のみ。** ASJ 排出モデルは fork の Bridge 実装がたまたま使っているだけの
> オマケで、可能なら CNOSSOS 排出で置き換えて追加を避けたい。
> 一人称は "I"。投稿本文は英語。以下そのまま貼れる形。

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

- the deck geometry is a first-class object;
- sources sit on the deck, well above the DEM — CNOSSOS placement at
  `DEM + height` cannot express a road 10–20 m up on a structure;
- the deck itself acts as a diffracting / reflecting body, and the space under
  and beside it is handled;
- vehicles enter and leave the structure along its length.

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

### Note on emission (not part of the proposal)

The bridge sources need *some* road emission method. My fork happens to compute
them with a small helper based on the Acoustical Society of Japan RTN-Model 2023
coefficients (`emission.road.asj`). I'm **not proposing to add a new emission
model** — if the bridge sources can be driven by the existing CNOSSOS road
model, I'd rather do that and drop the helper. See question 5.

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
5. Can the bridge deck sources reasonably be driven by the **existing CNOSSOS
   road model**? If so I'll do that and drop the ASJ helper entirely.

I can provide a draft PR, the `TutoBridge` dataset, and before/after maps to
make the feature concrete. Thanks for taking a look.

---

## メモ（投稿前に詰めること）

- スクリーンショット/before-after ノイズマップを 1〜2 枚用意すると通りが良い（`TutoBridge` から生成）。
- ASJ は「fork がたまたま使っている排出ヘルパー、提案の対象外」で統一済み。CNOSSOS 化できるかは Q5。
- `feat/road-asj` は未 push。CNOSSOS 化の見通し次第では push 自体不要になりうる。
- 投稿相手の主なアクティブ人物: nicolas-f, pierromond, sachabaclet, gpetit, mglesser。
- 返答パターン別の分岐は [merge_project.md](merge_project.md) §3.5「推奨: まず D」を参照。
