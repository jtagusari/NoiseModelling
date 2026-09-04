# fix ブランチを upstream/main へ取り込む方策

> 更新日: 2026-08-31 / 対象: `fix` (= `origin/fix`) → `upstream/main`
> upstream = `https://github.com/Universite-Gustave-Eiffel/NoiseModelling.git`
> ファイル単位の仕分け表 → [fix_diff_inventory.md](fix_diff_inventory.md)
> upstream Discussion のドラフト → [bridge_upstream_proposal.md](bridge_upstream_proposal.md)

---

## 1. 現状把握

### 1.1 分岐状況

| 項目 | 値 |
|------|-----|
| 共通祖先 (merge-base) | `46c9380a` "Merge pull request #820 ..." (**2025-07-30**) |
| `fix` が先行するコミット | **85**（うちマージコミット 6、実コミット 79） |
| `upstream/main` が先行するコミット | **642** |
| `fix` の変更規模 (merge-base→fix) | **376 ファイル / +79,387 / −6,400** |
| ローカル `fix` と `origin/fix` | 完全一致（差分なし） |

`fix` は約 **13 か月前**に分岐し、その後両側が独立して大規模に進化している。

### 1.2 ナイーブにマージした場合

`git merge-tree fix upstream/main` の結果:

- **CONFLICT 行 68 / 競合ファイル約 58**
- うち modify/delete 競合が 5 件（構造が根本的にずれているサイン）:
  - `fix` が削除・upstream が変更: `NoiseMapDatabaseParameters.java`, `SceneDatabaseInputSettings.java`, `TestPathFinder.java`
  - upstream が削除・`fix` が変更: `cnossos/Path.java`, `RayAttenuationComputeOutputTest.java`

競合は「機械的に解ける空白差分」ではなく、**両側が同じコアクラスを別方向に書き換えた**もの。手作業マージは現実的でない。

### 1.3 構造的な非互換（マージを難しくしている主因）

| 変更 | upstream/main | fix |
|------|---------------|-----|
| WPS スクリプト | `wps_scripts/` を廃止し **`noisemodelling-scripts/`** に改称・再配置 | `wps_scripts/` を大幅改修（+ `wpsbuilder/`） |
| チュートリアルモジュール | `noisemodelling-tutorial-01` を削除 | 残存・改修 |
| 新規追加 | `installer/`, `Dockerfile`, `docker-compose.yml`, `CONTRIBUTING.md` | `Docs-dev/`（開発メモ33ファイル）, `.mvn/`, `.vscode/` 等 |
| cnossos path | PR #991 cnossospath-refactor で `CnossosPath`/`Path` 系を再設計 | `AcousticPath*` / `CnossosPathExt` / path builder 分解で**別方向に**再設計 |
| Attenuation 出力 | `AttenuationOutput` を子クラスに分割、ノイズマップを行列積で計算、`MeteoType` 導入 | `AttenuationOutputSingleThread` 等を独自改修 |
| 受信点/音源の Z | PR #1016/#1021 で「Z を標高として扱う」グローバルパラメータを追加済み | per-row `HEIGHT_TYPE` 列で相対/絶対を制御 |
| pathfinder | 部分的リファクタ | **137 ファイル / +25,547** の大規模分解（多数の `*Service` クラス、Bridge 機能） |

### 1.4 `fix` 側の主な独自変更（内訳）

| 領域 | 規模 | 内容 |
|------|------|------|
| pathfinder | 137f / +25.5k | `profilebuilder` の Service 化、`BridgeAnalyzer`/`ElevationConverter`、Path builder 分解、Bridge 機能一式 |
| propagation | 82f / +9.6k | `AcousticPath*`, `CnossosPathExt`, `CnossosPathProcessor`, セグメント計算の分解 |
| jdbc | 64f / +9.4k | `TableInputSettings` Builder 化、`ComputationSettings`/`ReceiverGenerationSettings`/`EmissionInputSettings` 等の設定クラス分離 |
| emission | 15f / +1.1k | **`road/asj/` = ASJ RTN-Model 2023 道路音源モデル新規実装**（テスト4本・約800行、加算的） |
| Docs-dev | 33f | 開発用ドキュメント（個人メモ、upstream 送付対象外） |
| test_cases | 68 JSON | 自動生成のリグレッション期待値（`fix` の path 実装に強く依存） |

### 1.5 コミット履歴の品質

- 実コミット 79 件の**大半がコミットメッセージ = `git status` 出力の貼り付け**（例: `modified: ...\tnew file: ...`）。
- `fix-code-quality-improvements` との相互マージが 6 回混入。
- **このままでは upstream への PR 履歴として提出できない**。スカッシュまたは再構成が必須。

### 1.6 upstream の関連 issue / PR（2026-08-31 時点）

`gh` CLI は未導入だが GitHub REST API（未認証・公開リポジトリ）で参照可能。

**fix の作業に直結する open issue:**

| # | タイトル | 対応スライス | メモ |
|---|----------|-------------|------|
| **#956** | Setup of Roads with absolute Z and DEM get Z=2*Z for their noise sources | **S3** | 二重変換バグ。メンテナ（nicolas-f / pierromond）が `Noise_level_from_traffic` と `Road_Emission_from_Traffic` の不整合を認め「2ブロックを揃えたい／ドキュメント更新」と発言。**S3 提案の入口** |
| #881 | Throw an exception if DEM points/linestrings does not have a Z (altitude) value | S2 | `ElevationConverter` 周辺 |
| #979 | Wrong slope computation | S3 近傍 | 音源 Z / スロープ |
| #880 | Support for LineString building | S2 近傍 | fix の `LineStringSplitter.java` |
| #909 | Check for screens and long building following DEM | S2 | Bridge / DEM 追従 |
| #1029 | Extend geometry types in DefaultTableLoader | S4 | `DefaultTableLoader` 改修 |

**upstream が同領域を能動改修中（S5 が衝突する裏付け）:** sachabaclet の perf 系 open PR が多数 ——
`perf_vedge_hull` / `perf_shared_path_geometry` / `perf_mirror_receiver_index` / `perf_receiver_batches` /
`perf_profile_intersections` / `remove_unused_wall_index` / `perf_prepared_ground` など。すべて `ProfileBuilder`・
path geometry・attenuation ループ・receiver batching を触る。upstream の方針は「小さな最適化パッチの積み重ね」。

**S1/S2 は新規テーマ:** ASJ・日本の道路音源・bridge virtual source に関する issue / PR は**存在しない**。
提案 issue（または Discussion）を新規に立てる必要がある。

---

## 2. 前提: ブランチ丸ごとのマージ / rebase は行わない

1. upstream が `fix` と**同じコアクラス（cnossos path, AttenuationOutput, CutProfile, scripts モジュール）を別設計で作り直した**。差分の当て込みでは整合が取れない。
2. 642 コミットへの段階 rebase は、79 個の巨大かつ抽象度の異なるコミットを毎回衝突解決しながら通すことになり非現実的。
3. `fix` の変更の多くは upstream に出す性質のものではない（開発メモ、生成物、IDE 設定、個人ビルド調整）。
4. upstream はレビュー時に**小さく焦点の絞れた PR**を求める。

→ upstream の最新の上で**必要な部分だけを作り直して PR にする**。ただし「何を作り直すか」の単位は、
下記 3.1 の依存関係で決まる。

---

## 3. 依存関係と 2 つのトラック

### 3.1 `fix` の実際の依存グラフ

当初の「独立した機能スライス（S1→S2→…）」という見立ては誤り。`fix` の主要成果は**相互依存した 1 つの
アーキテクチャ改修**であり、Bridge 機能はその最上段に乗っている:

```text
[F1] ProfileBuilder のサービス分解
     BuildingService / WallService / GroundService / TopographyService / ProcessedWallService
     + 基盤（FrequencyConfig, ClearableService, GeometryFactoryProvider, ProfileUtils, RTreeUtils …）
        ↓  ProfileBuilder は BridgeService.PropagationType を import。サービスはコンストラクタ注入
[F2] CutProfile の作り直し  ★最難関
     API 非互換の書き換え（getGPath→calculateWeightedGroundAbsorption,
     computePts2D*→generateElevationProfile2D, フィールド private 化 + setter,
     hasBridgeIntersection / ascendBridge / discendBridge 追加, PROFILE_TYPE/curvedPath 削除）
     ← upstream も #1032 等で CutProfile を能動変更中（凸包長, computePtsGround …）
        ↓  全 path 消費側（PathFinder, CnossosPathBuilder, テスト）が波及
[F3] PathFinder の分解
     ReceiverProcessor / ReceiverBatchScheduler / PathExecutionManager /
     DiffractionPathBuilder / ReflectionPathBuilder / DirectAndDiffractionEvaluator /
     SourceCollector / SourcePointInfo / ReceiverPointInfo（PathFinder 1142→254 行）
        ↓
[F4] Scene の作り直し（181→689 行）
     HeightType enum を addSource / addReceiver など ~15 シグネチャに織り込み,
     sourceHeightType / receiverHeightType マップ, SceneBuilder, BridgeRelationship
     ← issue #956（絶対 Z + DEM の二重加算）に関連
        ↓
[F5] Bridge 本体
     BridgeService, BridgeAnalyzer, ElevationConverter, profilebuilder/Bridge*,
     BridgeRelationship, CutPointBridgeWall, bridge テストケース（TBC*）
        ↓
[F6] ASJ 排出（= Bridge virtual source の音源側）
     emission/road/asj/*（feat/road-asj に移植済み）
        ↓
[F7] JDBC 配線（bridge points テーブル読込, RoadEmissionBuilder の ASJ 経路 …）
        ↓
[F8] scripts（noisemodelling-scripts）Bridge チュートリアル / Noise_level_from_bridge_traffic
```

**帰結:** F5（Bridge）を upstream にクリーンに入れるには、F1〜F4 が upstream 側と整合している必要がある。
F6（ASJ）を単独 PR にしても「呼び出し側は？」となる。

### 3.2 クラス単位の乖離実測（merge-base → upstream/main → fix, 行数）

| クラス | merge-base | upstream/main | fix | 評価 |
|--------|-----------:|--------------:|----:|------|
| `ProfileBuilder` | 1672 | 1658（ほぼ不変） | 1458 | upstream は構造を維持。fix はサービスへ抽出 |
| `CutProfile` | 277 | 518（+241） | 643（+366） | **両側が別方向に大改修。API 非互換。最難関** |
| `PathFinder` | 1142 | 1049（−93） | 254（−888） | upstream はほぼ現状維持。fix は解体 |
| `Scene` | 181 | 185（+4） | 689（+508） | upstream はほぼ不変。fix は責務を大幅追加 |

upstream は `ProfileBuilder` / `PathFinder` / `Scene` の構造を**意図的に安定させたまま**、perf 系
マイクロパッチ（`perf_profile_intersections`, `remove_unused_wall_index`, `perf_prepared_ground` …）を
積んでいる。F1/F3 の大規模分解はこれら open PR を軒並み無効化する。

### 3.3 トラック A: 機能ミニマル（Bridge を upstream の現行構造の上に薄く再実装）★採用

- F1〜F4 の**アーキテクチャ改修は移植しない**。upstream の現行 `ProfileBuilder` / `CutProfile` /
  `PathFinder` / `Scene` をそのまま使う。
- Bridge が本当に必要とする最小限の変更だけを加える（`CutProfile` に bridge 交差判定を足す等）。
- ASJ（F6）と Bridge（F5）を 1 つの機能 PR に束ねる。事前に設計 issue。
- **Pros:** レビュー可能なサイズ。upstream がアーキテクチャ改修を望むかに依存しない。数週間規模。
- **Cons:** サービス足場が無い分 Bridge 実装が不格好になりうる。`fix` のリファクタ資産は private に留まる（or 破棄）。
- ただし F1〜F4 の中に「`fix` の方が明らかに正しい／良い」独立変更があれば、Bridge に**先行して**単独 PR
  にする（下記 3.5 の調査結果を参照）。

### 3.4 トラック B: 基盤ファースト（F1→F2→F3→F4→F5 を順に upstream 化）

ユーザー提案の順序。各ステップは「差分の移植」ではなく「`fix` の設計判断を、upstream の現行コードの上で
**リファクタとして再導出**する」作業。

| PR | 内容 | 前提条件 / リスク |
|----|------|-------------------|
| B0 | **GitHub Discussion（RFC）**: pathfinder アーキテクチャ改修の是非をメンテナに問う | **これが承認されない限り B1 以降に着手しない** |
| B1 | `ProfileBuilder` → `BuildingService` / `WallService` / `GroundService` / `TopographyService` 抽出（振る舞い不変、テスト緑） | open な perf 系 PR とのコンフリクト調整。マージ順の合意 |
| B2 | `CutProfile` 作り直し | ★最難関。upstream の CutProfile 変更（#1032 等）との統合。全 path 消費側の書き換え |
| B3 | `PathFinder` 分解（`ReceiverProcessor` ほか） | B1/B2 が前提 |
| B4 | `Scene` 作り直し + `HeightType`（#956 と紐付け） | B2 が前提 |
| B5 | Bridge 本体（`BridgeService` ほか）+ ASJ | B1〜B4 が upstream にマージ済みであること |
| B6 | JDBC 配線 / scripts チュートリアル | B5 の後 |

- **Pros:** 最終形が綺麗。Bridge が一貫した基盤に乗る。
- **Cons:** 数四半期規模。**メンテナの継続的な関与とアーキテクチャ方針の合意が必須**（外部依存）。
  B2（CutProfile）で頓挫するリスクが高い。upstream は現状 `ProfileBuilder`/`PathFinder`/`Scene` の
  構造を維持したがっている兆候（perf パッチ運用）があり、承認可否は不透明。

### 3.5 F1〜F4 の「先行単独 PR」候補 — 調査結果（2026-08-31）

**結論: 現時点で「`fix` が明らかに正しく、かつ分離可能で、upstream 未修正」という強い候補は無い。**

理由 —— `fix` が F1〜F4 で触った**正しさ系のテーマは upstream が既に別 PR で先に修正済み**:

| `fix` が触ったテーマ | upstream の対応（マージ済み） | 判定 |
|---------------------|------------------------------|------|
| 絶対 Z + DEM の二重加算 | #1016 / #1021 `receiver-source-z-is-alti`（`ZIsAltitude` フラグ + `DefaultTableLoader` で1回だけ変換） | upstream 済。`fix` 版は別設計であって改善差分ではない |
| 側方回折の凸包長 | #1032 `fix-convexHullLength` | upstream 済 |
| 2D DEM で Z 無しの例外 (#881) | #974 `fix_issue_881_2D_DEM_exception` | upstream 済 |
| 反射の取り込み判定 | #1017 修正 / PR #1018 `fix-maxReflDist` | upstream 済 or 対応中 |
| スロープ計算 (#979) | 未修正だが **`fix` も未変更**（`RoadCnossosParameters.setSlopePercentage` に手を入れていない） | 候補なし |

`fix` の F1〜F4 差分の実体は **(a) 構造リファクタ**（カプセル化 `Wall.p0`→`getP0()`、サービス抽出、リネーム）
**または (b) Bridge 用スキャフォールド**（`ElevationConverter` は `Bridge`/`BridgeRelationship` を import、
`DEFAULT_SOURCE_HEIGHT_BELOW_BRIDGE` 等）であり、既存アルゴリズムへのピンポイントなバグ修正は見当たらない。
加えて `fix` のコミットは巨大混在で cherry-pick 不能 —— 抽出はどのみち手作業の再実装になる。

**したがって Bridge に先行して出せる単独 PR は無い。**

- **#956**: 調査した（2026-08-31）。upstream では `Noise_level_from_traffic` は既に **deprecated**、
  維持経路（`Road_Emission_from_Traffic` → `Noise_level_from_source`）には `confSourcesZIsAltitude` /
  `confReceiversZIsAltitude` フラグが既にあり、正しく設定すれば二重加算は起きない。残る差は
  「`Docs/Input_*.rst` が `Zobject = Zdem + Zsource` を無条件に書いていて、Z が相対前提であることに
  触れていない」だけ。**docs 3 行の PR は本質的でなく、出すメリットが薄いので見送り**（2026-08-31 判断）。
  必要になったら Bridge の doc 更新に混ぜる。
- （任意）F1/F3 の**振る舞い不変の局所リファクタ**（`SourcePointInfo` / `ReceiverPointInfo` の値オブジェクト化、
  `SourceCollector` 抽出など）も、upstream の「構造維持 + perf パッチ」方針では *それ自体が改善* とは
  見なされにくく、B0 Discussion の合意が前提。単発では出さない。

### 3.6 手法: replay

`fix` の履歴は**書き換えない**。共通祖先（`46c9380a`）ではなく `fix` の **HEAD ツリー**から、必要なファイル群
だけを `integ/upstream-sync`（= `upstream/main`）の上に置いて、まともなメッセージでコミットし直す。

```bash
git switch -c feat/<slice> integ/upstream-sync
git checkout fix -- <replay 対象パス...>          # HEAD ツリーから取り出す
#   必要なら git restore -p / git checkout -p でハンク単位に絞る
JAVA_HOME="C:\Program Files\Java\jdk-25.0.4.1" mvn -pl <module> -am test
git commit -m "feat(...): ..."
```

- 「差分（コミット）の cherry-pick」ではなく「最終ファイルの再配置」。`fix` の巨大混在コミットは無視できる。
- **replay できるのは、upstream 側でそのファイルが乖離していない場合だけ**（新規追加 or upstream 未変更）。
  乖離しているファイル（F1/F2/F3/F7、および F5 の hook）は replay 不可 → 手書きで再実装。
- F6 ASJ（`feat/road-asj`）はこの手法で完了済み（6 ファイル、既存ファイル変更ゼロ、66 tests green）。

#### F5（Bridge）の replay 対象と手書き対象

> **2026-08-31 実施メモ（`feat/bridge`, commit `bccfa91a`）:** 下記 A の 13 ファイルを置いて
> `mvn compile` したところ **116 エラー**。当初の依存見積り（「F1 は `RTreeUtils` の 1 本だけ」）は
> **誤り** —— import grep が同一パッケージ参照と static クラス利用を拾えていなかった。実際の閉包は下記。

**A-1. Bridge 新規ファイル（13、置換済み）**

pathfinder / main: `profilebuilder/{BridgePoint 758, BridgeService 741, Bridge 685, BridgePointManager 446,
BridgeTriangulation 440, BridgeGeometryBuilder 334, BridgeQueryHelper 296, CutPointBridgeWall 193}`,
`{BridgeAnalyzer 102, ElevationConverter 240, LineStringSplitter 201}`, `path/BridgeRelationship`,
`utils/geometry/RTreeUtils`。

**A-2. さらに随伴が必要な F1 インフラ（追加・upstream に無い・自己完結、~520 行）**

`profilebuilder/` の `FrequencyInitializable`(16) `ElevationComputable`(11) `ClearableService`(8)
`ProcessedFacetsExportable`(27) `GeometryFactoryProvider`(17) `LoggingUnmodifiableList`(89)
`ProfileUtils`(114) `RayWallIntersection`(94) `ProcessedWallService`(160)。
→ `BridgeService implements FrequencyInitializable, ElevationComputable, ClearableService, ProcessedFacetsExportable`、
`ProcessedWallService` / `GeometryFactoryProvider` / `LoggingUnmodifiableList` / `ProfileUtils` を利用。
transitively閉じている（`WallService`/`BuildingService` 等へは波及しない）。

**B. 手書き（upstream の既存クラスに hook を足す）** — コンパイルエラーから確定:

| クラス | 追加するもの |
|--------|-------------|
| `ProfileBuilder` | `getBridge(int)` `getBridgeByPk(long)` `getBridgeCount()` + `IntersectionType.BRIDGE` 定数 + `bridges` 保持と `addBridge` |
| `Scene` | `getProfileBuilder()` getter、**`HeightType` enum**（`BridgePoint` が ~10 箇所で使用 → 最小の enum を持ち込むか、`BridgePoint` から剥がす）|
| `CutPointSource` | `getBridgeRelationship()` `getBridgeHeight()` + backing field |
| `CutPointWall` / `CutPointBridgeWall` | `getWallAlpha()` `getIntersectionType()` の親子整合 |
| `CutProfile` | `generateCutPointCoordinates2D()` `setCutPoints(ArrayList<CutPoint>)` ← **`fix` の F2 API**。upstream の `CutProfile` に薄いラッパを足すか、Bridge 側呼び出しを upstream API に書き換え |
| `CutPoint` | `getPrimaryKey()` `setObstructingAcousticRay(boolean)` |

**判明した含意:** F5 は F1 インフラ一式（小）＋ F2 の一部 API ＋ F4 の `HeightType` に依存しており、
「fork の Bridge クラス群をそのまま replay」は成立しない。A-2 の随伴（~520 行）＋ B の hook 群まで
込みで初めてコンパイルが通る。fork の `Bridge` 実装自体も重い（`BridgePoint` 補間・`BridgeTriangulation`・
girder/slab type・edge 回折・背面ミラー反射・底面 virtual source）。

#### 方針（2026-08-31 確定）: Bridge クラス群は fork をそのまま持ち込み、hook だけ手書き

抽象 Discussion 単発（[bridge_upstream_proposal.md](bridge_upstream_proposal.md)）ではなく、
**動く Bridge を現行 `main` に載せた PR** を出す。ただし `BridgePoint` / `Bridge` とその周辺ヘルパー
（`BridgePointManager` `BridgeGeometryBuilder` `BridgeTriangulation` `BridgeQueryHelper` `BridgeService`
`CutPointBridgeWall` `BridgeAnalyzer` `ElevationConverter` `LineStringSplitter` `BridgeRelationship`）は
**fork の実装をそのまま replay**する（作り直しは無駄が多すぎる）。**「最小」= 既存 pathfinder クラスへの
変更を最小に**、の意。

持ち込む範囲:

- **A-1** Bridge 新規 13 ファイル（置換済み `bccfa91a`）
- **A-2** 随伴 F1 インフラ 9 ファイル（~520 行、自己完結。§上記）
- **B** 既存クラスへの hook（§上記の表）。ここだけ手書き。方針:
  - `ProfileBuilder` / `Scene`：アクセサ追加（`getBridge*` / `getProfileBuilder`）+ `bridges` 保持 + `IntersectionType.BRIDGE`
  - `CutProfile`：fork の `generateCutPointCoordinates2D()` / `setCutPoints(...)` を upstream の `CutProfile` に
    **薄いラッパとして追加**（本体ロジックは upstream のまま）。bridge 交差の挿入点だけ足す
  - `Scene.HeightType`：fork の enum を**最小形でそのまま追加**（`BridgePoint` が使うため。~20 行）
  - `CutPointSource` / `CutPointWall` / `CutPoint`：`getBridgeRelationship` 等のアクセサを追加
- **含めない**: F1 の他サービス分解、F2 の `CutProfile` 全面書き換え、F3、propagation 側の ASJ 減衰拡張（下記 C）

v0 で削るもの（fork にはあるが後回し）: デッキ底面の反射（v1）、ASJ 構造物音源（v1、`feat/road-asj`）。
v0 のデッキ音源は通常の CNOSSOS。

#### v0 実施結果（2026-08-31, `feat/bridge`）

| コミット | 内容 | 結果 |
|---|---|---|
| `bccfa91a` | A-1: Bridge 13 ファイル replay | コンパイル不可（既知） |
| `2b8bf1bf` | A-2（F1 インフラ 9）+ Tier 1 hook（`Scene.HeightType`, `IntersectionType.BRIDGE`, `CutPoint.obstructingAcousticRay`, `CutPointSource.bridgeRelationship/bridgeHeight`, `CutProfile.setCutPoints/generateCutPointCoordinates2D/hasBridgeIntersection`, `Wall.getPrimaryKey`, replay ファイルの API 適合） | `mvn compile` SUCCESS（18 ファイル） |
| `47e03a32` | Tier 2: `ProfileBuilder` に `bridgeService` + bridge 専用 `ProcessedWallService`。`finishFeeding` で deck elevation / facet export / rtree。`getProfile` の後段に **並行パス `addBridgeCutPts()`**（`checkPropagationType` + セグメントループの `case BRIDGE` + `setEffectiveBridgeCutPoint`）。buildings/walls/ground の既存パスは無改修。fork の `Bridge*Test` 8 本を replay（`PathFinderBridgeTest` は fork の `SceneBuilder`/`PathFinder` API 依存のため除外） | `clean test` **245 pass / 0 fail**（152 bridge 単体含む） |
| `8bfd4a40` | `getProfile` が音源を橋に対して分類（on / under / unrelated）。橋関連音源のみ `addBridgeCutPts` 稼働（未関連の ray×deck 遮蔽は F7 の分類配線待ち）。`BridgeProfileBuilderTest`（end-to-end 4 本） | `clean test` **249 pass / 0 fail**。propagation + jdbc もコンパイル可 |

**当初目標（最小限の実装がエラーなく動く）は達成。** 既存クラスへの変更は accessor / enum 追加中心で最小。
`ProfileBuilder` の cut フローには `addBridgeCutPts()`（~60 行）と `classifySourceAgainstBridges()`（~25 行）を追加。

**C. propagation 側 — ✅ v0 は無改修で到達（2026-08-31, `feat/bridge` `287ec1b7`）**

- 実測: bridge cut point を含む `CutProfile` は `CnossosPathBuilder.computeCnossosPathsFromCutProfile()` を
  **エラーなく通過**。デッキ縁は 2D 断面の凸包頂点になり、汎用の回折メカニズムで**バリアとして減衰を生む**
  （`CutPointBridgeWall extends CutPointWall` かつ `intersectionType` が BUILDING_ENTER/EXIT なので既存分岐で拾われる）。
- `BridgePropagationTest`（2 本）: デッキ上音源 → 回折点あり、同一形状でデッキ無し → 直達。
  propagation フルスイート **55 pass / 0 fail**（TC01–TC28 の回帰 47 本含む、回帰なし）。
- **propagation の production コード変更ゼロ。** 正しい橋の物理（下部開放・水平面・構造物音）は v1
  = fork の `AttenuationCnossosExt`（1108）/ `CnossosPathExt` の必要部分の移植。

**D. F7/F8（B の後）**: `jdbc/BridgePointMappingTest` `BridgeTrafficTest` + `TutoBridge/*.geojson`、

**D. F7/F8（B の後）**: `jdbc/BridgePointMappingTest` `BridgeTrafficTest` + `TutoBridge/*.geojson`、
`noisemodelling-scripts` の Bridge チュートリアル / `Noise_level_from_bridge_traffic`。

### 3.7 直近の進め方

1. ~~`feat/bridge` で A-2 + B（hook）+ Tier 2 配線~~ → ✅ `8bfd4a40`、pathfinder 249 pass。
2. ~~propagation で減衰まで通す~~ → ✅ 無改修で到達（§3.6-C、`287ec1b7`、propagation 55 pass）。
3. ~~jdbc: `BRIDGE_POINTS` テーブルからデッキを読む最小ローダ~~ → ✅ `70512c9d`。
   `GridMapMaker.bridgePointsTableName` + `DefaultTableLoader.fetchCellBridges`（`BRIDGE_PK` でグループ化 →
   `Bridge` 1個 → `addBridge`）。`BridgeNoiseMapTest`（end-to-end: 同一シーンを橋あり/なしで実行し、
   デッキが受音点の LEQ を下げる）。jdbc 59 pass / 0 fail。
   → **DB テーブル → ノイズマップ → 橋による遮蔽**の end-to-end が動作。
4. ~~`noisemodelling-scripts` に WPS 入力~~ → ✅ `79c07ead`。`Noise_level_from_source` に任意入力
   `tableBridgePoints` 追加（SRID チェックは `tableDEM` と同形、未指定なら no-op）。
   `TestNoiseLevelFromBridge`（WPS ブロック経由の end-to-end: `tableBridgePoints` の有無でデッキが
   受音点の LEQ を下げる）。scripts `TestNoiseModelling`+`TestNoiseLevelFromBridge` 7 pass。
5. ~~`TutoBridge` geojson データセット + チュートリアルテスト~~ → ✅ `8a171475`。
   小さな自己完結データセット（dem/buildings/lw_roads/bridgepoints/receivers, SRID 2154, ~26 KB）+
   `TestNoiseLevelFromBridge.tutoBridgeDatasetRunsThroughTheWpsBlock`。
   **ここで判明した v0 の限界（下記）** のため、定量的な「影の grading」assert は外し、
   「import + 実行が通る／橋ありで背後の受音点が大きくならない」だけを確認。
6. ~~**コミット整理**（`wip` を squash して 4 個のクリーンなコミットに）~~ → ✅ `feat/bridge` を
   `04793d59`(= `upstream/main`) の上に 4 コミット（pathfinder / propagation / jdbc / scripts）に
   再構成。ツリーは squash 前と byte 一致（退避タグ `backup/feat-bridge-presquash`）。
   `origin/feat/bridge` に push 済。full clean test: pathfinder 249 / propagation 55 / jdbc 59 /
   scripts 96、失敗 0。**draft PR 本文**は [bridge_pr_body.md](bridge_pr_body.md) に作成
   （`gh` 未導入＋本人投稿の方針のため未投稿）。
7. **v1（方針 C, 2026-09-04 決定）** — ブランチ `feat/bridge-v1`（`feat/bridge` 起点）。
   詳細 → [bridge_v1_core_analysis.md](bridge_v1_core_analysis.md) §6。
   - **含む**: デッキ幾何の拡張、デッキ上音源の `CutProfile` 標高処理（ascend/descend、`instanceof
     CutPointBridgeWall` ガード）、bridge point ごとのデッキ高さ、**ASJ 構造物音源**（`feat/road-asj` の
     `emission.road.asj` を CNOSSOS-EU に加算する追加音源項）、`fix` の該当 TBC golden 移植。
   - **v2 送り**: デッキ裏面反射 / BT 回折（下端くぐり上端越え）/ ミラー音源 / 虚音源（`_imaginary`・
     `_mirror`・下部開放）。理由: これらは `fix` の F3（CNOSSOS パス層全面再設計、`AcousticPath` /
     `AcousticPathConfiguration` / `CnossosPathProcessor` / `DistanceDifferenceCalculator` /
     `PivotPointCalculator` / `CnossosPathExt` / `AttenuationCnossosExt` — upstream 8 → fix 21 ファイル）
     に結合。upstream の `CnossosPathBuilder` は上側凸包ベースで「下をくぐって上へ」の機構が無い。
     → v2 で A（upstream API に対し BT をゼロから再実装）か B（F3 パス層を橋プロファイル専用の並行
     サブシステムとして移植）を選択。当初の「~150 行加算」見積もりは撤回。
   - `fix` の F1（service 分解）/ F2（`CutProfile` 書き換）/ seam(route B) は持ち込まない。
   - 各ステップ parity harness（非橋シーンで全受音点×バンド完全一致）＋ JMH で検証。

#### v0 の「デッキ外受音点で -∞ dB」問題 — ✅ 1 行修正で解消（2026-08-31, `df210025`）

- **原因**（診断済み）: CNOSSOS 式のバグではない。`CutProfile.isFreeField()` が building/topography だけを
  見ていて bridge を見ていなかったため、bridge で遮蔽された断面が「free field」扱いになり、
  `PathFinder.directPath` が `stopAtObstacleOverSourceReceiver` の early-return で**切り詰められた断面**
  （地面カット点・Z 補間なし）をそのまま減衰計算に渡していた → 退化した profile → -∞ 相当。
  直接 `CnossosPropagationModel.computeAttenuation` に**完全な** bridge 断面を渡すと、upstream の
  `AttenuationCnossos` は**妥当な回折減衰を計算する**（実測: aBoundary +12〜20 dB、aGlobal 約 -55〜-67 dB）。
- **修正**: `isFreeField()` に `&& !hasBridgeIntersection` を追加（1 行）。これで bridge が building と同じ挙動に:
  鉛直回折 ON なら デッキ縁を越える回折直達パスが正しく計算される。
  TutoBridge のデッキ背後の受音点は -∞ ではなく **約 -10〜-15 dB**（バリア + 2 m 遮音壁相当）に。
- **`AttenuationCnossosExt` / `CnossosPathExt` の移植は不要**（v0 では）。fork のあれらは
  下部開放・ミラー音源・構造物音などの高度なケースを扱うもので、v1 で必要になったら移植する。
- 回帰確認: pathfinder 249 / propagation 55（TC01–TC28 の 47 本含む）/ scripts bridge 2 — 全緑。
- 別件（upstream の既存バグ）: `Noise_level_from_source` が `setComputeHorizontalDiffraction` と
  `setComputeVerticalDiffraction` の引数を取り違えている（`confDiffHorizontal` が実際には鉛直回折を有効化する）。
  Bridge PR とは別に報告候補。

**v0 到達点まとめ（`feat/bridge`, squash 後 4 コミット, base `04793d59`）:** pathfinder（幾何＋断面, 249）→
propagation（デッキ縁バリア, 無改修, 55）→ jdbc（`BRIDGE_POINTS` ローダ, 59）→ scripts（`tableBridgePoints`
入力 + TutoBridge, 96）。全緑・失敗 0、既存 upstream テストの回帰ゼロ。`bridgeDeckShieldsAGroundReceiver` は
経路差 δ=0.109 m から CNOSSOS 単一回折式でバンド別挿入損失をブラケット。**WPS ブロックで「テーブル →
ノイズマップ → 橋による遮蔽」が動作。**

### 3.8 upstream に出さないもの

`Docs-dev/**`, 生成 `test_cases/*.json`（68 個）, `.vscode/`, `.mvn/`, `javadoc-warnings-unique.txt`,
`nbactions.xml`, `preparation.sh`, `test_coverage_summary.md`, `noisemodelling-tutorial-01`（upstream で削除済み）。

### 3.9 F6（ASJ）の実施状況

**ブランチ:** `feat/road-asj`（`integ/upstream-sync` = `upstream/main` 起点、コミット `5a645a53` 1 本）

移植ファイル（6 / +1,076 行、**既存ファイル変更ゼロ**）:

- `emission/road/asj/RoadAsj.java`, `RoadAsjParameters.java`
- `emission/src/main/resources/.../road/asj/RoadAsj_2023.json`
- `emission/src/test/java/.../road/asj/RoadAsj{,Formula,Parameters}Test.java`（計 38 テスト）

検証: `JAVA_HOME=jdk-25 mvn -pl noisemodelling-emission test` → **66 passed / 0 failed**。

`RoadAsj_2023.json` は ASJ RTN-Model 2023 の**橋梁形式別 構造物音（デッキ・桁の放射音）係数表**
（STEEL_BOX_STEEL 等 7 形式）。CNOSSOS に相当が無い。**Bridge v0 には含めず v1 送り**
（v0 のデッキ音源は通常の CNOSSOS）。v0 PR で「follow-up で ASJ 構造物音を足す」と言及する。
`feat/road-asj` はそのまま保持、push は v1 着手時。

---

## 4. 次のアクション

### 完了済み

1. ~~`fix` にタグを打って保全~~ → ✅ `archive/fix-2026-08-31`
2. ~~`integ/upstream-sync` ブランチを作成~~ → ✅ 作成済み（`upstream/main` を追跡）
3. **JDK 25** → ✅ `C:\Program Files\Java\jdk-25.0.4.1` 導入済み。
   現行 `upstream/main` は `pom.xml` で `<source>25</source><target>25</target>`。
   ビルド/テスト時は `JAVA_HOME` を jdk-25 に向ける（例: `JAVA_HOME="C:\Program Files\Java\jdk-25.0.4.1" mvn ...`）。
   `.m2` に無い新依存（jackson 2.21 / commons-codec 1.19 / h2gis 2.2.6-SNAPSHOT 等）はオンラインで取得が必要。
4. ~~F6 ASJ を `feat/road-asj` に移植・テスト~~ → ✅ 66 passed（3.9 参照。push は保留）
5. ~~F1〜F4 の先行単独 PR 候補を調査~~ → ✅ 強い候補なし（3.5。正しさ系は upstream が既に別 PR で修正済み）
6. ~~インベントリの「要確認」項目を精査~~ → ✅ 単独 PR 級なし（[fix_diff_inventory.md](fix_diff_inventory.md) の X 表。
   最も近いのは CI.yml の deploy ガードだが価値小・upstream CI と乖離）
7. ~~#956 を調査~~ → ✅ コード修正不要（`confSourcesZIsAltitude` で解決済、`Noise_level_from_traffic` は deprecated）。
   docs のみの差だが本質的でなく **PR は見送り**（3.5）。着手した `issue-956-traffic-source-z` ブランチは破棄。
8. ~~`feat/bridge` で fork の Bridge 全 replay を試行~~ → ✅ WIP `bccfa91a`。116 エラー。依存閉包を実測（§3.6）。
   **replay 全部入りは断念。fork コードは reference 扱いに**（2026-08-31）。
9. ~~抽象 Discussion のドラフト作成~~ → ✅ [bridge_upstream_proposal.md](bridge_upstream_proposal.md)。
   ただし**単発の抽象 Discussion は出さず、動く v0 PR の背景説明に流用**する方針に変更（2026-08-31）。
10. ~~fork ヘルパーはそのまま持ち込み、hook だけ手書き（`feat/bridge`）~~ → ✅ v0 完了（§3.6 の実施結果表、
    最終 `8bfd4a40`、pathfinder **249 pass / 0 fail**）。

### これから（§3.7 の順）

1. ~~propagation で減衰まで通す~~ → ✅ 無改修で到達（§3.6-C、`287ec1b7`、propagation 55 pass）。
2. `noisemodelling-scripts` に最小ローダ/WPS + `TutoBridge` 縮小データ、before/after マップ 1〜2 枚。
   （現状 `getProfile` の内蔵分類でも動くが、DB/WPS 入口には `BridgePoint` を読むローダが要る = F7 の最小版）
3. **draft PR** を upstream に。説明に pathfinder コア拡張性・`perf_*` 順序・v1 予定を要約。
4. レビュー反応で v1（音源分類の F7 本格配線・per-point 高さ・底面反射・ASJ 構造物音・
   fork の `AttenuationCnossosExt`/`CnossosPathExt` 移植）。

### 作業ツリーの注意

`fix` ↔ `feat/*` のブランチ切替で `wps_scripts/` `tests/` `.gradle/` 等が未追跡ファイルとして残る
（`upstream/main` に無いため）。PR には影響しないが、気になるら退避する。`git clean` は `fix` 由来の
中身を消すので使わない。

---

## 5. やらないこと / 非推奨

- ❌ `git merge upstream/main` を `fix` 上で実行（58 競合、コアクラス全滅）
- ❌ `fix` を `upstream/main` に段階 rebase（79 巨大コミット × 642 コミットの衝突解決）
- ❌ `fix` 全体を 1 本の PR にする（79k 行、レビュー不能、履歴が git status 貼り付け）
- ❌ `fix` ブランチへの破壊的操作（force push / 履歴書き換え）—— アーカイブとして残す

---

## 6. 参考コマンド

```bash
# competitor: 分岐点と差分規模
git merge-base fix upstream/main
git rev-list --left-right --count upstream/main...fix
git diff --shortstat $(git merge-base fix upstream/main)..fix

# 競合ファイル一覧（マージせずに確認）
git -c merge.conflictStyle=merge merge-tree --write-tree --name-only fix upstream/main | sed -n '2,200p'

# fix 独自の新規ファイル（本体コードのみ）
git diff --diff-filter=A --name-only $(git merge-base fix upstream/main)..fix -- '*.java' '*.groovy' \
  | grep -v -i 'test\|/resources/'

# 特定ファイルの fix ↔ upstream 差分
git diff upstream/main fix -- <path>

# upstream 側で当該機能が既に入っていないか
git log --oneline upstream/main -- <path>
```
