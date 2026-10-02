# fix ブランチを upstream/main へ取り込む方策

> 更新日: 2026-09-17 / 対象: `fix` (= `origin/fix`) → `upstream/main`
> upstream = `https://github.com/Universite-Gustave-Eiffel/NoiseModelling.git`
> ファイル単位の仕分け表 → [fix_diff_inventory.md](fix_diff_inventory.md)
> PR 本文 → [bridge_pr_body.md](bridge_pr_body.md)（v0）／ [bridge_v1_pr_body.md](bridge_v1_pr_body.md)（旧 v1、要更新）／
> [bridge_v2_pr_body.md](bridge_v2_pr_body.md)（旧 v2、要更新）／ v1 core 分析 → [bridge_v1_core_analysis.md](bridge_v1_core_analysis.md)
>
> **現況（2026-09-17）:** Bridge を v0 / v1 / v2 の 3 段スタック PR に分割（→ §4 ロードマップ表）。
> 全段フルクリーン回帰・全緑、いずれも `origin` **未 push**（ローカル専用、force-push の心配なし）。
> - **v0**（`feat/bridge`）: デッキ = 上越え衝立。propagation 無改修。＋ デッキ上音源の地盤基準化・
>   `BRIDGE_PK` 相対 Z 解決（§4.14 で v1/v2 から降格）＋ ユニットテスト整理 4 コミット（§4.16）。
> - **v1**（`feat/bridge-v1`）: **BT 回折のみ**（デッキ下をくぐる／上を越える二重回折）。
>   ASJ 構造物音は元 v1 の内容だったが §4.15 で v2 へ移動し、BT を先に置く順序に入れ替え済み。
> - **v2**（`feat/bridge-v2`）: **ASJ 構造物音源の追加**（`emission.road.asj`＋`EMISSION_TYPE`振り分け）。
>   BT 回折は v1 から継承。
> - **未着手:** v0/v1/v2 いずれの draft PR も未投稿。`bridge_v1_pr_body.md`/`bridge_v2_pr_body.md` は
>   入れ替え前の内容のまま（v1/v2 のスコープ入れ替えを反映して要更新、§4.15）。
> - 各章の位置づけ: §1-§3 は upstream 統合方針の調査・決定の経緯（決定済み、変更なし）。
>   §4 はロードマップ表 + v0/v1/v2 それぞれの実装ログ（§4.10-§4.16、日付順）。§5-§6 は運用ルールと
>   参考コマンド。

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
   - 非橋シーンの結果不変は**既存 golden スイートで担保**（下記）。専用 parity harness は作らない。

   **進捗（`feat/bridge-v1`）:**
   - ✅ ② デッキ上音源の標高 — `8217815d`。`CutProfile.computePtsGround()` に橋分岐
     （`lastGroundZ`/`bridgePkOn`、非橋では inert）。デッキ上音源は地面参照＝デッキ面、
     デッキ縁 exit で地形に戻る。pathfinder 251 / propagation 55、非橋 elevation は byte 不変。
   - ✅ ③ per-point デッキ高さ — v0 replay で既に対応済（`BridgeTriangulation` が `BridgePoint` 間を
     三角形補間、`BridgeTriangulationTest` 等でカバー）。傾斜デッキの demo データセットは任意。
   - ✅ ④a ASJ emission パッケージ — `26a27d40`。`feat/road-asj`（`5a645a53`）から `emission.road.asj`
     6 ファイルを replay（既存ファイル変更ゼロ、emission 66 テスト）。
   - ✅ ④b ASJ 配線 — `af6ff6d2`。方針: **専用スクリプト**（2026-09-04 決定）。
     `Structural_noise_from_bridge.groovy`: `BRIDGE_POINTS`（デッキ中心線 + 桁/床版形式）＋
     デッキ別・期間別の重車両交通テーブル → `BRIDGE_STRUCT_LW`（デッキ 1 本 × 期間ごとに
     デッキ高さの線音源、ASJ RTN-Model 2023 のバンド別スペクトル、`RoadAsj.evaluateBridgeVirtualSource`
     ＝ MV+HGV 駆動）。それを `Noise_level_from_source` の `tableSources` に流す（車両 CNOSSOS と併用）。
     `TestStructuralNoiseFromBridge` 3 本（低周波優位・昼>夜・流量スケール・重車両ゼロで silent）。
   - ✅ `5a3cfdde` — v1 の on-deck 標高に合わせ `bridgeDeckShieldsAGroundReceiver` を再校正
     （デッキ上音源をデッキ面に載せる → 遮蔽シーンの挿入損失が全バンド ~1.2〜1.5 dB 減。
     「橋なし」数値は v0 と byte 一致）。
   - ✅ **クリーンフルスイート BUILD SUCCESS**（2026-09-04）: emission 66 / pathfinder 251 /
     propagation 55 / jdbc 59 / scripts 99（baseline 96 +3）、回帰ゼロ。
     （途中の偽 `NoClassDefFoundError` は旧 `nohup mvn &` のゾンビプロセスが `target/` を
     並行破壊したもの。`taskkill //F //IM java.exe` + `.m2`/`target` purge で解消。以後
     バックグラウンド実行はツールの `run_in_background` のみ使用。）
   - ✅ ASJ end-to-end テスト — `3f1460b4` / `9a864160`。`structuralNoiseAddsToTheReceiverLevel`:
     デッキシーンを車両のみ vs 車両+ASJ構造物音で `Noise_level_from_source` 実行。構造物音の
     追加で全バンド上昇、63 Hz は道路形状スペクトルに対し **+4.6 dB**（4 kHz は ~0）。
     鋼橋構造物音の低周波優位を end-to-end で確認。
   - ✅ ⑤ TBC 相当の回帰テスト — `2688023b`。`fix` の TBC JSON fixture は `SceneBuilder` / F2
     `CutProfile` スキーマ依存で移植不可 → `feat/bridge-v1` ネイティブで同シナリオを固定:
     `BridgePropagationTest.sourceOnDeckPathGeometryIsStable`（2D パス: 音源デッキ面 h=10.6、
     受音側高欄上端の単一回折 5 m/h=11、e=0、SR 距離 50.73 — v1 の上越え+on-deck 標高を pin）、
     `BridgeProfileBuilderTest.sourceOnOneDeckIsRelatedToThatDeckOnly`（2 デッキ、音源はデッキ A に
     ACTUAL_SOURCE_ON_BRIDGE / bridgePkOn=100、両デッキ横断で bridge cut 点 ≥2）。
     `_imaginary`/`_mirror` は v2。
   - **非橋シーンの結果不変 — 専用 parity harness は不要**（2026-09-04 判断）:
     (a) v1 の全分岐が `bridgeCount>0` / `instanceof CutPointBridgeWall` / `!isNaN(bridgeHeight)` /
     `hasBridgeIntersection` でガード = 非橋で 1 行も実行されない、
     (b) TC01–TC28（propagation 47 本、受音点×バンド dB のハードコード golden）が期待値そのままで緑、
     (c) jdbc `RegressionTest` / `NoiseMapByReceiverMakerTest`（実シーン end-to-end golden）緑、
     (d) `groundProfileUnchangedWithoutAnyBridge`（② の `computePtsGround` 変更が非橋で inert を直接アサート）。
     v1 PR ではこの 4 点を明記すれば足りる。
   - ⏳ JMH — ② は `computePtsGround` に局所変数 2 個 + `instanceof` を足すのみ、`AttenuationCnossos`
     無改修 → 優先度低。v1 PR 前に `getProfile()` の簡易ベンチだけ取る。

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
- **訂正（当初「バグ」と誤認）:** `Noise_level_from_source` の
  `.setComputeHorizontalDiffraction(compute_vertical_diffraction)` /
  `.setComputeVerticalDiffraction(compute_horizontal_diffraction)` は引数の取り違えではない。
  WPS 入力名 `confDiffVertical`/`confDiffHorizontal` は「エッジの向き」（垂直エッジ＝建物の角、
  水平エッジ＝デッキ縁やバリア上端）を指す一方、`Scene` の setter 名は「回折で経路が曲がる平面」
  （horizontal＝水平面内＝垂直エッジ回折、vertical＝垂直面内＝水平エッジ回折）を指す。`Scene.java` の
  フィールド javadoc（"Compute horizontal diffraction rays over vertical edges" /
  "vertical diffraction over horizontal edges"）と `DirectAndDiffractionEvaluator` の実装
  （`horizontalDiffraction` → `DiffractionPathBuilder.computeVEdgeDiffraction`、`verticalDiffraction`
  → 通常の上端越え経路）がこれを裏付ける。2 つの命名規則（エッジの向き ↔ 経路が曲がる平面）が逆なだけで、
  対応付け自体は正しい。

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
（STEEL_BOX_STEEL 等 7 形式）。CNOSSOS に相当が無い。**Bridge v0 には含めず v1 に統合**
（v0 のデッキ音源は通常の CNOSSOS）。

**2026-09-04:** v1（`feat/bridge-v1`）で `emission.road.asj` 6 ファイルを `feat/road-asj`（`5a645a53`）
から直接 replay（コミット `26a27d40`、emission 66 テスト緑）。配線は専用スクリプト
`Structural_noise_from_bridge.groovy`（§3.7 step 7 ④b）。→ `feat/road-asj` ブランチは v1 に取り込まれ、
単独用途はなくなった（削除可、当面保持）。

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

### ロードマップ（v0 / v1 / v2）

3 段階に分割、各段は前段の上に積む stacked PR。v0 = CNOSSOS-EU 準拠のジオメトリ・位置補正、
v1 = BT 回折（伝搬物理）、v2 = ASJ 構造物音（新しい音源モデル）— §4.15 で v1/v2 の順序を入れ替え済み。

| | ブランチ | スコープ | 状態 |
|---|---|---|---|
| **v0** | `feat/bridge`（`04793d59` + 9 コミット、tip `4c84fd36`（§4.18 で再較正））| デッキ = 上越えの厚い衝立。既存 CNOSSOS 凸包回折を通す。propagation **無改修**。jdbc に `BRIDGE_POINTS` ローダ、scripts に `tableBridgePoints` 入力、TutoBridge データセット。＋ **デッキ上音源の地盤基準化**（①、§4.14 で降格）＋ **BRIDGE_PK 相対 Z 解決**（②、§4.14 で降格）＋ ユニットテスト冗長性整理 4 コミット（§4.16、95→57 件） | ✅ フルクリーン回帰 全緑。[bridge_pr_body.md](bridge_pr_body.md) 作成済 / **未投稿**。⚠️ `origin/feat/bridge` は旧版のまま — 再 push 必要 |
| **v1（回折パス）** | `feat/bridge-v1`（新 `feat/bridge` + 3 コミット、**§4.15 で v2 と入れ替え済**、tip `299a4857`（§4.18 で再較正））| **BT 回折のみ**（ミラー音源技法＋下面/上面二重回折の合成、`DIFB` 点）＋ TBC04-30 系の回折ゴールデンテスト。ASJ 構造物音は v2 へ移動、代わりに §4.15 で見つかった v0/v1 の「橋の下の音源が正しく回折されない」ギャップがここで解消される | ✅ フルクリーン回帰 全緑（emission 28 / pathfinder 200 / propagation 64 / jdbc 59 / scripts 98 skip4） |
| **v2** | `feat/bridge-v2`（新 `feat/bridge-v1` + 5 コミット、**§4.15 で v1 と入れ替え済**、tip `4672285f`（§4.18 で再較正））| **ASJ 構造物音源の追加**（`emission.road.asj` ＋ `Structural_noise_from_bridge` script）＋ `EMISSION_TYPE` 分離（構造物音をデッキ裏面に正しく配置）＋ デッキ下受音点のミラー音源／BT 回折は v1 から継承。「構造物音が BT 経路を正しく見つける」統合テスト | ✅ フルクリーン回帰 全緑（emission 66 / pathfinder 200 / propagation 65 / jdbc 62 / scripts 103 skip4）。[bridge_v2_pr_body.md](bridge_v2_pr_body.md) は旧順序時点の内容のまま — **v1/v2 入れ替えを反映して要更新**。詳細 → §4.10（BT）・§4.15（入れ替え）・§4.16（テスト整理） |
| **v2.2** | （v2 に内包）| 構造物音の soffit 配置（option 1: `EMISSION_TYPE` ＋ `checkIfEmissionIsAcceptable`）／ 多段スタック DOWNWARD エッジ／ favourable BT 曲線経路 | ✅ **option 1 完了**（§4.12、2026-09-08）。**多段スタックは `BridgeService` のコア設計に起因する既知の制限と判明・対応見送り**（2026-09-09、§4.10 #3）。✅ **テストカバレッジ監査完了**（2026-09-09）: TBC04-31 の残り全て移植（propagation 65）＋ BT×構造物音の統合テスト追加（jdbc 62）。✅ **favourable BT 曲線経路の~0.04-0.05dBずれは対応不要と結論**（2026-09-09、upstream/fix の一般的な favourable 実装方針の違いに起因、TBC02 と同種）。**v2.2 は実質完了**。残: v2.3（`Road_Emission_from_Traffic` の ROAD/BRIDGE 同時生成）。TBC31 全 4 経路 解消（§4.13）。**TBC02 path 1 の ~2.6 dB は `aDif` の CNOSSOS 2.5.31/2.5.33 対応差で upstream が正しい＝対応不要**（§4.10 #2）|

### 直近のタスク（2026-09-17 時点で残っているもの）

v0/v1/v2 の実装・テスト整理・v1↔v2 入れ替えは全て完了・全緑（§4.14/§4.15/§4.16）。
残っているのは投稿系の作業のみ:

1. **v0 draft PR を投稿**（本人）。base = `04793d59`（= `upstream/main`、2026-08-14 以降不動、rebase 不要）。
   `compare/main...jtagusari:feat/bridge` から Draft。本文 = [bridge_pr_body.md](bridge_pr_body.md)（現行版で流用可）。
2. **`feat/bridge` / `feat/bridge-v1` / `feat/bridge-v2` を `origin` に push**（本人）。
   `feat/bridge` は §4.14/§4.16 で再編済みのため `origin/feat/bridge`（旧版）への push は force-push 相当になる。
3. **`bridge_v1_pr_body.md` / `bridge_v2_pr_body.md` を更新**（§4.15 の入れ替えを反映）—
   現行の内容は入れ替え前（旧 v1=ASJ、旧 v2=BT）のまま。**新 v1 = BT 回折、新 v2 = ASJ 構造物音**に
   合わせて本文を書き直してから投稿する。
4. v0 のレビューを見て **v1 draft PR**（base = `feat/bridge`）、さらに **v2 draft PR**（base = `feat/bridge-v1`）
   を stacked 投稿。
5. （任意・低優先）JMH `getProfile()` 簡易ベンチ。
6. （任意）TutoBridge から before/after ノイズマップ画像 1〜2 枚（PR 添付用）。
7. （任意・低優先）`BridgeFactoryTest`/`RoadAsjTest` 等、§4.16 で手つかずのまま残した小規模テストファイルの整理。

> 環境メモ: 2026-09-07 セッション中、作業ツリーが繰り返し破壊される事象があった（phantom staging・
> `git checkout` の "unable to update HEAD"）。原因は **pCloud**（本人が修正済み）。以後のフルクリーン
> 回帰はすべて `BUILD SUCCESS`。

### 4.10 v2 実装計画（方式 A、2026-09-07 着手）

ブランチ `feat/bridge-v2`（`feat/bridge-v1` 起点）。**方式 A** = upstream の
`CnossosPathBuilder` / `CnossosPath` / `AttenuationCnossos` にガード付きで splice、`fix` の
`*Ext` 型フォーク（~3000 行）は持ち込まず、`fix` を物理の参照にする。

**スコープ:** BT 回折（デッキ本体を下端くぐり→上端越え）＋ デッキ裏面反射（鏡像音源）＋
デッキ下の虚音源（`_imaginary` / `_mirror` / 下部開放）。

| # | ファイル | 変更 | 規模 | 非橋での状態 |
|---|---|---|---|---|
| 1 | `propagation/cnossos/PointPath.java` | `POINT_TYPE` に `DIFB` 追加（upstream は `DIFV`/`DIFH`/`DIFH_RCRIT` のみ）| 加算 1 行 | 未使用 |
| 2 | `propagation/cnossos/DistanceDifferenceCalculator.java` | **`fix` から verbatim 移植**（194 行、CNOSSOS Eq.2.5.24/25、`Coordinate`/`LineSegment` のみ依存、`*Ext` 非依存）| 新規 194 行 | 呼ばれない |
| 3 | `propagation/cnossos/CnossosPath.java` | nullable フィールド `bottomRoute`/`topRoute` + accessor（サブクラスにしない）| 加算 ~30 行 | null → 不活性 |
| 4 | `propagation/cnossos/CnossosPathBuilder` | `CutPointBridgeWall` DOWNWARD からデッキ**下端**の `DIFB` PointPath を発行（上側凸包には乗らないので専用検出）| ~60〜100 行、**正しさリスク中** | `CutPointBridgeWall` DOWNWARD 不在 → 発行なし |
| 5 | `propagation/cnossos/` 新規 `BridgeDiffraction.java` | BT 分解（`fix` の `setDiffractionPathParametersBT` + `newCutProfile{WithoutBottomEdge,OnlyWithBottomEdge,AfterBottomEdge,BeforeTopEdge}` を upstream API に適応）。path に DIFH+DIFB 両方あるときだけ発火、`computeCnossosPathFromCutProfile` を再入呼び出し | ~150 行 | 発火条件を満たさない |
| 6 | `propagation/cnossos/AttenuationCnossos.aBoundary` | BT 再帰分岐: `diffPtsT≥1 && diffPtsB≥1` なら `aBoundary(top) + aBoundary(bottom)` をバンドごと和で返す | 加算 ~20 行、**CNOSSOS コア内** | `diffPtsB` 空 → 分岐に入らない |
| 7 | pathfinder | **ほぼ v0 replay 済（2026-09-07 プローブ確認）**: `BridgeService` は `calculateFirstBridgeCutpoint` / `checkPropagationType`（6 種）/ mirror-receiver reflection を保持、`ProfileBuilder.addBridgeCutPts` は `IMAGINARY_SOURCE_TO_UPPER_RECEIVER` 配線済。デッキ下音源→デッキ上受音点の `getProfile()` は `IMAGINARY_SOURCE_UNDER_BRIDGE` ＋ `CutPointBridgeWall(DIR=DOWNWARD, z=デッキ下端)` を出力。残: `MIRROR_SOURCE`（裏面反射）分類、必要なら虚音源点生成 | ~50〜120 行 | `bridgeCount==0` でガード |
| 8 | 検証 | `fix` の `TBC*_attenuation_output.json`（`aGlobal[]` 配列、移植可能）を golden 化 + プログラム的シナリオテスト（`_actual`/`_imaginary`/`_mirror`/`_not_related`）| 新規テスト | — |

**見積もり（改訂）:** pathfinder 側は v0 replay で大半済 → propagation ~450 行 ＋ pathfinder ~100 行 ＋ テスト。
最大の正しさリスクは #4（DIFB 発行を upstream の上側凸包ベース `CnossosPathBuilder` に組み込む）と
#5（BT 分解の upstream API 適応）。TBC golden で検証。

**進捗:**
- ✅ #1〜#3（`DIFB` enum / `DistanceDifferenceCalculator` verbatim / `CnossosPath` 子経路フィールド）
  — `feat/bridge-v2` `0b419fa3`。加算のみ、emission 66 / pathfinder 252 / propagation 56 緑。
- ✅ #7 の実態調査 — v0 replay で `DIFB` 可能な `CutPointBridgeWall(DOWNWARD)` まで出ることを確認。
- ✅ #4/#5 のメカニズム調査（`fix` を精読）:
  - **DIFB の発生源** = `fix` `PivotPointCalculator.computeHorizontalEdgePivotPoints`。
    音源が `ACTUAL_SOURCE_ON_BRIDGE` / `SOURCE_NOT_RELATED_TO_BRIDGE` なら**通常の上側凸包**（DIFB なし、
    upstream と同じ）。音源が `IMAGINARY_SOURCE_UNDER_BRIDGE` / `MIRROR_SOURCE` のとき:
    プロファイルから DOWNWARD 橋壁（デッキ下端）を集め、凸包第 1 セグメント P0→P1 に対し
    その x で線形補間した y と比較 → **下端が補間線より下なら** その点を `BOTTOM_OF_OBSTACLE`（=DIFB）
    として pivot 列に差し込み、以降を再 hull（`TOP_OF_OBSTACLE` = DIFH）。pivot 列 =
    `[Source, DIFB(下端), DIFH(上端)…, Receiver]` → 「下をくぐって上へ」。
  - upstream の `CnossosPathBuilder.computeCnossosPathFromCutProfile` も `getConvexHullIndices` ベース
    なので、この「DOWNWARD 下端を hull に差し込む」ロジックは移植可能。ただし当該メソッドは
    hull + point 列 + segment 列 + 減衰入力を一括生成する ~380 行なので splice は要注意。
  - **TBC golden** = `TBC*_attenuation_output.json`（`aGlobal`/`aGlobalH/F`/`L` 等の 8 バンド配列、
    `numPaths` 分）。数値配列なので移植可。ただし cut profile fixture（`TBC*_imaginary.json`）は
    `fix` の `CutProfile` スキーマ → プログラム的にシーン再現し `aGlobal[]` を golden 配列と比較する方が確実。
    シーン幾何は `fix` `PathFinderBridgeTest` の javadoc に記載（Bridge1 pk=100 中心線 x=10 y=0..20 z=10;
    Bridge2 pk=101 x=12 y=0..20 z=5 半幅 10m barrier 1m）。

- ✅ #7（imaginary 音源生成）— `feat/bridge-v2` `8099944b`。`PathFinder.computeRaysAtPosition` が
  ソースリスト構築後（`hasBridges()` ガード）、デッキ上ソースごとにデッキ下端の imaginary 音源を
  `Bridge.generateVirtualSourcesAtBridgeBottom` で生成し、`IMAGINARY_SOURCE_UNDER_BRIDGE` 関係を付与。
  位置からは分類不能なため `SourcePointInfo.bridgeRelationship`（nullable）＋ `ProfileBuilder.getProfile`
  の 5 引数オーバーロード（override 適用 or 従来分類）を追加。非橋・null では従来と完全同一。
  **検証: プローブ出力が `fix` の `TBC01_imaginary.json` と一致**
  （imaginary 音源 (10,10,9.45) ＋ `CutPointBridgeWall(15,11.05,9.5) DOWNWARD BUILDING_EXIT bridgePk=100`、
  `obstructingAcousticRay=false`）。emission 66 / pathfinder 252 / propagation 56。
- 📌 判明: `fix` の `TBC01_attenuation_output.json` path0（imaginary）は `aBoundaryH=[-3,...]`
  = **回折なし・直達+地面のみ**（DOWNWARD 壁は非遮蔽なので寄与なし）。#4/#5 の DIFB/BT が要るのは
  壁が遮蔽（`obstructingAcousticRay=true`）になる幾何のみ。`_imaginary` の多くは直達+地面で足りる可能性。

- ✅ **#8（TBC golden ハーネス）+ 検証結果** — `feat/bridge-v2` `c2a81cd8` / `3083f33f`。
  `CutPoint` に `CutPointBridgeWall` を `@JsonSubType "BridgeWall"` 登録、`CutPointSource` に
  `bridgePkOn`/`bridgePkAbove`/`bridgeRelationType` の flatten アクセサ追加（`fix` と同じ。挙動不変）。
  `fix` の `TBC01/02/03/07/11/20` fixture を copy、`BridgeAttenuationGoldenTest` が upstream の
  **無改修 `AttenuationCnossos`** に通して golden `aGlobalH` と比較。
  - **✅ TBC01 / TBC07 / TBC11 / TBC20（imaginary・actual 両パス）— golden を 0.2 dB 以内で再現。**
    これらは「デッキ上音源 → デッキ真下/斜め下の受音点、他障害物なし」の**素直な imaginary**。
    `_imaginary` fixture では DOWNWARD デッキ壁が**常に非遮蔽**（全 TBC で確認）→ 幾何的に
    「直達 + 地面」なので fix も upstream も同じ計算になる。→ このクラスは #4/#5/#6 不要。
  - ⚠️ **TBC31_imaginary: ~12 dB 乖離**（imaginary 音源 ＋ 建物 Wall ＋ 実地形の複合）。
    upstream 無改修では**再現できない**。imaginary 音源移植だけでは不十分なケースが存在する。
  - ⚠️ TBC02 actual: ~3 dB 乖離（デッキ外縁が薄壁回折縁になる下方受音点）。
  - ⚠️ TBC03: imaginary fixture が path builder の既存 assertion に引っかかる。
  - ⚠️ **`_mirror`（TBC04/09/10/21/31_mirror）: DOWNWARD デッキ壁が `obstructingAcousticRay=true`。
    ここが本来の BT ケース**（デッキ下面で遮蔽 → 回り込み）。MIRROR 音源生成も未配線。**#4/#5/#6 が要る。**

**v2 の到達点（2026-09-08 更新）:** #4/#5/#6（BT 回折）を upstream に実装完了。`*Ext` 型フォークなし。

- ✅ **#4 DIFB splice** — `CnossosPathBuilder.spliceBridgeBottomEdge`。音源が
  `IMAGINARY_SOURCE_UNDER_BRIDGE` / `MIRROR_SOURCE` で、DOWNWARD 橋壁が凸包第 1 セグメントの
  補間線より下なら、その index を hull に差し込み `DIFB` として mark。ガード外では発火なし。
- ✅ **#5 BT route 分解** — `CnossosPathBuilder.computeBridgeBottomTopRoutes` ＋ 4 helper
  （`newCutProfile{WithoutBottomEdge, OnlyWithBottomEdge, AfterBottomEdge, BeforeTopEdge}`、`fix` から適応）。
  path に DIFB＋DIFH 両方あるとき、`deltaB` vs `deltaH` で split を選び bottom/top サブ経路を
  `computeCnossosPathFromCutProfile` の再入呼び出しで構築、`CnossosPath.bridge{Bottom,Top}Route` に格納。
  `newCutProfileOnlyWithBottomEdge` は `fix` に合わせ `instanceof CutPointWall`（橋壁も含む）で全壁 drop
  → 「bottom edge 優勢」split の S→B レグは ground のみに退化。
- ✅ **#6 aBoundary 和** — `AttenuationCnossos.aBoundary` に `hasDifB && hasDifT && 両 route 非 null` の分岐、
  `aBoundary(top) + aBoundary(bottom)` をバンドごと和で返す。`aDif` は `DIFB` を `DIFV` 同様の純ナイフエッジ
  （地面効果なし）として扱う。
- ✅ **虚音源の自動生成は撤去（option 2、§4.11）** — `PathFinder.addBridgeVirtualSources` は
  `MIRROR_SOURCE` 生成のみ残し、`IMAGINARY_SOURCE_UNDER_BRIDGE`（構造物音になるべき）の自動生成は削除。
  `BridgeNoiseMapTest` 回復。
- ✅ **合成 BT テスト** — `fix` に `TBC12`（imaginary BT）`abae3180` / `TBC13`（mirror BT）`d90e8cf4` を追加、
  v2 の `BridgeAttenuationGoldenTest` が `fix` の fixture を投入して検証。
  - TBC12 homogeneous golden **bit 一致**、favourable 0.04 dB 以内。
  - TBC13（mirror BT）golden 0.2 dB 以内。
  - TBC03 は BT 実装で解消 → asserted CASES に昇格。TBC02 は golden path 0（imaginary）のみ assert。
  - **TBC31（複合実 DEM）— 全 4 経路 golden 一致**（2026-09-08、3 バグ修正）:
    ① `210c44b1` デッキ端がプロファイル最高点で hull に自然に乗ると DIFB 誤認 → 偽 BT。橋壁を hull から除去。
    ② `9548c63f` splice 後の後半（edge→受音点）を再 hull していなかった → mirror 経路が単独 DIFB。`upperHullAfter` 追加。
    ③ DOWNWARD 橋壁（デッキ下面エッジ）を地盤標高プロファイルに入れる扱いの是正（§4.13）。imaginary 経路の
    S→O 平均面のずれ ~0.5 dB を解消。`fix`・v2 とも「DOWNWARD 橋壁は地形点のみ、天端を地盤に入れない」に統一
    （v2 `84ced451` ＝ 旧 `7d17821b` を amend、`fix` `e4300320`）。旧 `7d17821b`（fix の `(天端, 地形)`
    2 点順に寄せる中間版）は「天端が地盤に入る」点で同じく不適切だったため置換。
  - **assert 済: TBC01, TBC02(imaginary), TBC03, TBC07, TBC11, TBC12, TBC13, TBC20, TBC31（全 4 経路）。**
- ✅ **`fix` 本来の（非合成）mirror golden も配線**（2026-09-09、`feat/bridge-v2` `41970376`）。
  それまで TBC04/05/06/09/10/21 の `_mirror`/`_not_related` fixture は golden test 未配線のまま
  リポジトリに存在しているだけだった（TBC13 が唯一の mirror BT 検証）。`fix` の
  `AttenuationComputeOutputCnossosBridgeTest` から fixture を追加コピーして判明:
  TBC05/06 は DOWNWARD 壁が非遮蔽（直達＋地面、`CASES` 配列で assert）。
  TBC04/09/10/21 は壁が遮蔽だが下流に障害物が無いため **DIFB 単独**（上端越えを伴わない片側回折）に
  なる — 新設 `assertDifbGoldenReproduced` で検証、golden と bit 一致。真の下端くぐり→上端越え
  （BT）の反射版は引き続き TBC13（合成ケース）のみが担保。propagation 61→65 テスト。

**非橋不変:** 全分岐が「橋がなければ偽」の条件でガード（`DIFB` 点なし／`bridge{Bottom,Top}Route` 非 null／
`IMAGINARY`・`MIRROR` 関係／`hasBridges()`）。emission 66 / pathfinder 252 / propagation 65 / jdbc 59 /
scripts 100 緑、TC01–TC28 期待値不変。`AttenuationCnossos.aBoundary`/`aDif` に加算分岐（`DIFB` 条件下のみ）。

#### `fix` の機能を完全再現するために残っているステップ（2026-09-08 棚卸し）

**✅ 再現済み（v0+v1+v2）:**

- デッキ幾何・三角形分割（`Bridge` / `BridgePoint*` / `BridgeGeometryBuilder` / `BridgeTriangulation` /
  `BridgeQueryHelper` / `BridgeService` / `BridgeAnalyzer` / `BridgeRelationship` — `fix` からバイト一致、
  差分は upstream API 適応のみ `setProcessedObstructionIndex` 等）。
- 音源の幾何分類（`ProfileBuilder.getProfile` → `classifySourceAgainstBridges`）: `ACTUAL_SOURCE_ON_BRIDGE` /
  `SOURCE_NOT_RELATED_TO_BRIDGE` を位置ベースで判定（`fix` の `BRIDGE_PK` 列方式と違い、SOURCE テーブルに
  橋列は不要）。`bridgeRelationship` 上書き経路（`getProfile` 5 引数）で虚音源／ミラー音源にも対応。
- ミラー音源生成（下部道路 → デッキ裏面鏡像 `MIRROR_SOURCE`、`PathFinder.addBridgeVirtualSources`、
  `BridgeAnalyzer.selectBridgeForMirrorSource` 相当を inline）。
- v0: デッキ = 上越え厚スクリーン。v1: デッキ上音源の `CutProfile` 標高、per-point デッキ高、ASJ 構造物音（別テーブル）。
- v2: BT 回折（imaginary / mirror 音源 → デッキ下端くぐり `DIFB` ＋ 下流障害物上越え `DIFH`）。
  `BridgeService.mirrorRelax` 幾何は `BridgeService` に含まれるので replay 済。
- 検証: TBC01 / 02(imaginary) / 03 / 04 / 05 / 06 / 07 / 09 / 10 / 11 / 12 / 13 / 20 / 21 /
  **31（全 4 経路）** を golden 0.2 dB 以内（TBC12・TBC04/09/10/21 は bit 一致）。実 DEM の複合シーン
  TBC31 も含め、BT・単独回折・非合成 mirror 回折とも `fix` と一致。

**残タスク:**

1. ✅ **構造物音を `IMAGINARY_SOURCE_UNDER_BRIDGE` として自動配置（option 1）** — 2026-09-08 実装完了。
   `input/EmissionType` ＋ `PeriodEmission.emissionType` ＋ 任意列 `EMISSION_TYPE`、
   `AttenuationOutputSingleThread.checkIfEmissionIsAcceptable` ゲート、`PathFinder.addBridgeVirtualSources`
   の裏面 IMAGINARY 点復活、`Structural_noise_from_bridge` の emission-type 出力モード（§4.12）。
   残（v2.3）: `Road_Emission_from_Traffic` での ROAD/BRIDGE 同時生成、traffic-flow 入力モードからの
   `EMISSION_TYPE` 生成。

2. ~~**TBC02 golden path 1（ACTUAL、~3 dB）**~~ → **対応不要（v2 が正しい、2026-09-08 計装で判明）。**
   TBC02 path 1（音源がデッキ上、受音点がデッキ下方＝`OTHER` 橋壁を回折）の ~2.6 dB 差は、
   両者で**回折ピボットもスカラー δ（=1.0633）も一致**。全差分は `aDif` 内。**受音点が O→R 平均地盤面より
   下**にあるため、upstream `aDif` は CNOSSOS-EU 2.5.31/2.5.33 の「音源／受音点が平均面より下」分岐
   （`deltaDiffSR := deltaDiffSRPrime`、`deltaGroundOR := aGroundOR`）に入る。`fix` の旧 `aDif` にはこの
   分岐が無く常に平均面上の式を使う。**upstream の方が CNOSSOS 準拠として正しい**ので移植すべきものは無い
   （移植は退行）。`computePtsGround` に `OTHER` エッジを足しても δ・平均面はほぼ動かず差は残る（試行済み・撤回）。
   `fix` 側の先頭地形点除去（§4.13「OTHER」、`4ea9ed6e`）は `fix` 内の 0.2 dB アーティファクト是正として別途有効。

3. **多段スタックの DOWNWARD エッジ — 既知の制限（v2.3 以降に先送り、2026-09-09 調査）**

   当初の見立て（「`PivotPointCalculator` の再帰分岐が未検証なだけ」）は誤りと判明。実際に2本目の橋
   （Bridge3, centerline x=30, deck z=17）を追加した合成シーンを `fix` 上で組んで検証したところ、
   **`PivotPointCalculator` に処理が到達する以前の段階**、`BridgeService.setEffectiveBridgeCutPoint()`
   （橋 cut point のポスト処理）で2本目の橋のエッジが CutProfile から消えることが判明:

   - 音源自身が乗っている／くぐっている橋（`bridgeRelationship` の PK と一致 = `mirrorRelax` 点）だけを
     **無条件に保持**し、それ以外の「よその橋」は `isPointUnderPath`（音源→受音点の直線に対し上か下か）の
     簡易判定を通らないと **問答無用で捨てられる**。今回の Bridge3 のエッジはこの判定で「直線より上」と
     扱われ、`bridgeHeightOn` が更新されないまま次段の `Double.isNaN(bridgeHeightOn)` チェックでも弾かれ、
     CutProfile に一切現れなかった（`getCutPoints()` で確認）。
   - つまり「音源が関係する橋は1本だけ」という前提が `BridgeService` に埋め込まれており、
     `spliceBridgeBottomEdge`／`PivotPointCalculator` の再帰ロジックは事実上デッドコード
     （生成される CutProfile が複数 DOWNWARD エッジを含むケースが無い）。
   - `feat/bridge-v2` はこの `BridgeService` を `fix` からバイト一致で replay しているため、同じ制限を
     そのまま引き継ぐ。

   **対応方針:** `BridgeService.setEffectiveBridgeCutPoint` の「1橋だけ保持」という設計自体の見直しが
   必要で、当初想定より大きい `fix` コア設計への手戻りを伴う。実務上の必要性（3層以上の高架の実例）が
   出るまでは着手せず、**既知の制限として本ドキュメントに記録するに留める**。2層（TBC20/21 相当）は
   音源自身の橋の1エッジのみで完結するため現状で問題なし。

4. **favourable 条件の BT 曲線経路 — 対応不要と結論（2026-09-09 調査）**

   TBC12/13 の favourable が golden と ~0.04–0.05 dB ずれる（`topRoute.delta` が 0.498 vs 0.493 等）。
   EPS 0.2 dB 内なので assert は通るが bit 一致ではない。原因を調査した結果:

   - **SR セグメントレベルの `zsF`/`zrF` 補正式（`ALPHA0`/`deltaZT`/`deltaZS`/`deltaZR`）は
     `feat/bridge-v2`（`CnossosPathBuilder.computeSegment`）と `fix`（`CnossosSegmentComputer`）で
     完全に同一** ── 変数名まで一致。
   - **`DistanceDifferenceCalculator.java`（delta 計算の核）は `fix` から verbatim 移植済み**で同一。
   - 差の出どころは別: `feat/bridge-v2`（= upstream 由来）は favourable 時に `CurvedProfileGenerator`
     でプロファイル上の**全カット点の座標そのものを幾何学的に曲げて**（Harmonoise/CNOSSOS
     Eq.2.5.24-25 の円弧近似、曲率半径 `max(1000, 8*d)` の `d` は**処理中の経路自身の距離**）から
     凸包・回折点判定を行う。`fix` には対応するクラスが無く、曲率補正は経路長への解析的補正として
     のみ適用されているとみられ、プロファイル座標自体は曲げていない。
   - つまり BT 固有のバグではなく、**favourable 条件をどう表現するかという upstream と `fix` の
     一般的な（橋に限らない）実装方針の違い**が、距離の短い BT サブ経路で相対的に顕在化しているだけ。
     TBC02（`aDif` の実装差、upstream の方が CNOSSOS 準拠として正しいと結論・対応不要とした件、
     §4.10 #2）と同じ性質。合わせにいくには橋に関係ない一般の favourable 経路計算そのものに
     手を入れる必要があり、「upstream の既存クラスへの変更は最小限に」という本プロジェクトの
     方針に反する。

   **結論: bit 一致を目指さない。現状（EPS 0.2 dB 内で assert 済み）で対応完了とする。**

#### テストカバレッジ監査（2026-09-09）

`fix` の TBC01〜31 と `feat/bridge-v2` の `BridgeAttenuationGoldenTest` を突き合わせ、
「`fix`に実装がある機能のうち`feat/bridge-v2`側で golden 未検証のもの」を洗い出した。

- ✅ **TBC08 / TBC30 を移植**（`feat/bridge-v2` `f5fde046`）。`fix` の TBC01〜31 は全て
  `feat/bridge-v2` の `CASES` 配列 or 専用テストで golden 検証済みになった
  （TBC08 = `SOURCE_NOT_RELATED_TO_BRIDGE` footprint 外、TBC30 = 実 DEM の壁無し基本シーン、
  TBC31 はこれに壁と2音源を足したもの）。
- ✅ **「BT回折 × 実 ASJ 構造物音スペクトル」の統合テストを新規追加**（`feat/bridge-v2` `72d8a6bb`）。
  従来は `BridgeAttenuationGoldenTest`（幾何・伝搬の正しさ、フラット93dB音源）と
  `BridgeStructuralNoiseTest`（`EMISSION_TYPE` 振り分けの正しさ、障害物なしシーン）が別々に
  検証されており、「構造物音源がBT回折を要する障害物越しに正しいASJスペクトルで伝搬するか」の
  組み合わせが未検証だった。`BridgeStructuralNoiseTest.structuralNoiseDiffractsUnderTheDeckAndOverADownstreamBuilding`
  を追加: デッキ先の建物の有無で `EMISSION_TYPE='BRIDGE'` のレベルが 66.26→59.72 dB
  （~6.5 dB の挿入損失）、受音点でなお有意なレベルが残ることを確認 — 2つ目の障害物が
  実際にモデルへ反映されていること（BT が機能していること）の証拠。jdbc 61→62。
- **残る非対称性（低優先度、未対応）:** `MIRROR_SOURCE` には近傍受音点ケース（TBC05/06、デッキ上/下）が
  あるが `IMAGINARY_SOURCE_UNDER_BRIDGE` には無い（TBC07 遠方のみ + TBC12 BT）。BTスプライスの発火条件は
  両者共通と確認済みなので致命的な穴ではないが、対称性のために追加する価値はある。
  2橋シーン（TBC20/21相当）の `IMAGINARY`/`NOT_RELATED` 版も同様に無いが、
  「2橋シーンでも音源自身の橋1本しか効かない」制限（上記§4.10 #3）により新しい発見は乏しいため見送り。

---

### 4.11 `IMAGINARY_SOURCE_UNDER_BRIDGE` の扱い（2026-09-08 決定）

**判明した `fix` の実際の設計:** `IMAGINARY_SOURCE_UNDER_BRIDGE` は
**「道路音源をデッキ裏面に位置移動したコピー」ではなく、構造物音（デッキ・桁の放射音）の音源**。

`fix` のパイプライン:

| 場所 | 役割 |
|---|---|
| `SceneWithAttenuation.doAddSourceDb` | 橋上ソース行の `EMISSION_TYPE` 列で分岐。`'ROAD'` → `ACTUAL_SOURCE_ON_BRIDGE`（交通音）、`'BRIDGE'` → `IMAGINARY_SOURCE_UNDER_BRIDGE`（構造物音）。コメントにも `IMAGINARY_SOURCE_UNDER_BRIDGE (structural noise)` と明記。 |
| `SourceCollector.addLineSource` | `ACTUAL_SOURCE_ON_BRIDGE` の各サンプル点に対し、デッキ裏面（`(deckHeight − deckThickness) − 0.05`）に `IMAGINARY_SOURCE_UNDER_BRIDGE` の**幾何点**を `sourcePk` コピーで自動生成。 |
| `AttenuationOutputSingleThread.checkIfEmissionIsAcceptable` | emission-type ゲート。`IMAGINARY_SOURCE_UNDER_BRIDGE` は `EmissionType.ROAD` を**拒否**し `EmissionType.BRIDGE`（構造物スペクトル）**だけ**を受ける。`ACTUAL_SOURCE_ON_BRIDGE` は逆に `BRIDGE` を拒否。同じ `sourcePk` に ROAD/BRIDGE 2 つの `SourceEmission` がぶら下がり、デッキレベル点＝交通音・裏面点＝構造物音、に振り分けられる。BRIDGE スペクトル未定義なら裏面点は**無音**。 |

つまり `fix` の橋上音源モデル ＝ `ACTUAL_SOURCE_ON_BRIDGE`（交通音・デッキレベル）
＋ `IMAGINARY_SOURCE_UNDER_BRIDGE`（構造物音・デッキ裏面）の **2 音源**。ASJ RTN-Model の
「橋上音源＝実音源＋構造物音」と一致。`RoadAsj.evaluateBridgeVirtualSource` が構造物スペクトルを算出。

**v2 `8099944b` のバグ:** `SourceCollector` の**幾何点の自動生成だけ**を移植し、
`SourceEmission.EmissionType`（ROAD/BRIDGE）・`EMISSION_TYPE` 列・`checkIfEmissionIsAcceptable`
を移植していない。結果、v2 の裏面 `IMAGINARY_SOURCE_UNDER_BRIDGE` 点は `sourcePk` そのまま
フィルタなしで **道路 LW をそのまま放射** → 道路音の完全なコピーがデッキ裏面に加わる。
`BridgeNoiseMapTest.bridgeDeckLowersTheLevelAtAShieldedReceiver` が赤（without 74.87 → with 75.67）
なのはこれが原因。v0/v1 では緑（虚音源の自動生成が無いため）。

**採用方針（option 2、2026-09-08）:** v2 では emission-type 分離を**移植しない**。
- `PathFinder.addBridgeVirtualSources` の `IMAGINARY_SOURCE_UNDER_BRIDGE` **自動生成を撤去**。
  `MIRROR_SOURCE` 生成（`6c063e0e`）は残す（`checkIfEmissionIsAcceptable` で MIRROR は ROAD を
  受けるので、下部道路の鏡像＝道路 LW で正しい）。
- 構造物音は **v1 の `Structural_noise_from_bridge` → `BRIDGE_STRUCT_LW` の別音源テーブル**として
  ユーザーが明示的に足す方式で統一（v1 の作りと整合）。
- BT 伝搬物理（#4/#5/#6, `959a1a67`）は**そのまま残す**。純粋な伝搬幾何なので音源 LW に依存せず、
  `IMAGINARY_SOURCE_UNDER_BRIDGE` / `MIRROR_SOURCE` 関係を持つ `CutProfile` が来たときだけ発火する。
  当面は `BridgeAttenuationGoldenTest`（`fix` の TBC fixture を直接投入）で検証を担保。
- `8099944b` の他の足場（`SourcePointInfo.bridgeRelationship`、`getProfile` 5 引数オーバーロード、
  `CutPointSource` の flatten アクセサ、`CutPoint`/`CutPointBridgeWall` の JSON 型登録）は BT / golden
  ハーネスが依存するので**残す**。

**未解明の懸念（後で原因が判明する可能性）:** `fix` はなぜ「別テーブル方式」ではなく
「emission-type ゲート ＋ `SourceCollector` 自動生成」を採ったのか。推測される理由:
(a) 構造物音の音源位置をデッキ裏面に厳密に置きたい（別テーブルだと中心線・デッキ高になる）、
(b) 交通量から構造物スペクトルを内部で self-consistent に導出したい（`Road_Emission_from_Traffic`
内で ROAD/BRIDGE を同時生成）、(c) `IMAGINARY_SOURCE_TO_UPPER_RECEIVER` 等の場合分けと配線を
共通化したい。option 2 でこれらのどれかが effectively 損なわれるなら v2.2 で emission-type 分離
（option 1）に切り替える。その判断材料として本節を残す。

---

### 4.12 option 1（emission-type 分離）の v2 移植（2026-09-08 実装完了）

§4.11 の「未解明の懸念」を踏まえ v2.2 として option 1 を移植した。**emission 物理は v1 で移植済み**
（`RoadAsj.evaluateBridgeVirtualSource` / `calculateBridgeVirtualSourceLevelForFrequency`、ASJ 構造物音）
なので、足りない**振り分け配線**のみを実装。

**コミット（`feat/bridge-v2`）:**
- `986d8ad6` fix(pathfinder): 共有橋壁セグメントのコピー（下記「発見したバグ」）
- `e9e4f01e` feat: EMISSION_TYPE 分離（①②③ ＋ `BridgeStructuralNoiseTest`）
- `b16ee1cd` feat(scripts): `Structural_noise_from_bridge` emission-type 出力モード（④、案 B）

#### v2 に既にあるもの（`8099944b` 足場・維持済み）

- `BridgeRelationship.RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE`
- `ElevationConverter`: `IMAGINARY_SOURCE_UNDER_BRIDGE` case ＋ `DEFAULT_SOURCE_HEIGHT_BELOW_BRIDGE = -0.05`
- `PathFinder.addBridgeVirtualSources`（現状 `MIRROR_SOURCE` のみ生成、javadoc に「IMAGINARY はここでは作らない」）
- IMAGINARY 経路の BT 回折（§4.10）
- v1 の `Structural_noise_from_bridge.groovy`（現状 `BRIDGE_STRUCT_LW` 別テーブルに出力、geom=デッキ中心線・z=デッキ高）

#### 移植する 4 点

**① jdbc — emission 行に種別を持たせる**（`e9e4f01e`）
- `input/EmissionType.java` 新規（enum `ROAD` / `BRIDGE` のみ。`fix` の rail 種別は不要）。
  `SceneWithEmission.PeriodEmission` に `emissionType` フィールド追加。旧 2 引数 ctor と
  `addSourceEmission(pk, period, wj)` は `ROAD` 委譲で残す → **既存呼び出し全て `ROAD`、非橋不変**。
- `processEmission` / `processEmissionDEN`（`INPUT_MODE_LW` / `_LW_DEN`）で任意列 `EMISSION_TYPE` を
  `readEmissionType()` で読む（列なし or 空 → `ROAD`）。traffic-flow モードは常に `ROAD`。必須列なし。
- **`fix` との相違（意図的）:** `fix` は列なし → `NULL`（全 relation で通す）。v2 は列なし → `ROAD`。
  `fix` 方式だと「`EMISSION_TYPE` 列の無い生 LW 表 ＋ 橋」で裏面 IMAGINARY 点が `NULL` 行を放射し
  二重計上する潜在バグがある。`ROAD` 既定なら IMAGINARY が拒否するので二重計上しない。enum から
  `NULL` を落とした。

**② jdbc — ゲート**（`e9e4f01e`）
- `AttenuationOutputSingleThread.checkIfEmissionIsAcceptable(CutPointSource, PeriodEmission)`:

  | relation | 受理する emissionType |
  |---|---|
  | `ACTUAL_SOURCE_ON_BRIDGE` / `MIRROR_SOURCE` / `SOURCE_NOT_RELATED_TO_BRIDGE` | `BRIDGE` 以外 |
  | `IMAGINARY_SOURCE_UNDER_BRIDGE` | `BRIDGE` のみ |

  `onNewCutPlane` の emission ループ（`~271`）先頭で `continue`。max-error 枝刈り（`~178`/`~314`、
  既定 OFF）は非ゲート＝予算をわずかに過大評価するだけ（保守的・出力不変）。
  橋関係なし かつ `BRIDGE` 行なしの scene では no-op（`ROAD`≠`BRIDGE` で常に受理）。

**③ pathfinder — デッキ裏面 IMAGINARY 点を復活**（`e9e4f01e`）
- `PathFinder.addBridgeVirtualSources`: `sourceList` を走査し、`isPointWithinBridgeFootprint`
  ＆ `isPointOnBridge`（`classifySourceAgainstBridges` と同じ位置判定）を満たす実音源点ごとに、
  同 `sourcePk`/`li`/`orientation`・`IMAGINARY_SOURCE_UNDER_BRIDGE` 関係・z=`(deckH − deckThk) − 0.05` の
  `SourcePointInfo` を追加。javadoc 更新（「作らない」→「作る、ゲートで無音」）。
- `SourcePointInfo.bridgeRelationship` → `directPath` → `getProfile` 5 引数 → `CutPointSource` に
  override 適用済（既存配線、MIRROR 用に整備されていたものを再利用）。

**発見したバグ（`986d8ad6`、`fix` にも存在）:** `BridgeService.createBridgeCutPoint` が処理済み橋壁の
`LineSegment` を**コピーせず** `CutPointBridgeWall` に渡していた。`CutPointBridgeWall.modifyIntersectionHeight`
は `wall.p0/p1.z` を**その場で書き換える**。処理済み橋壁は全 `getProfile` 呼び出しで共有される長命
オブジェクトなので、ある profile が書いた z（例: imaginary/mirror 音源用の DOWNWARD デッキ下端エッジ
`deckH − thk`）が、同じ壁を再利用する次の profile（例: ACTUAL 橋上音源用の UPWARD 高欄エッジ
`deckH + barrier`）に漏れ、**高欄の遮蔽が無効化**されていた。③で IMAGINARY 点（受音点に近く先に処理
される）を足したことで顕在化。`new LineSegment(wall.getLineSegment())` でコピー。

**④ scripts — `EMISSION_TYPE='BRIDGE'` 行を LW 表に入れる（案 B）**（`b16ee1cd`）
- `Structural_noise_from_bridge.groovy`: 既定は従来どおり `BRIDGE_STRUCT_LW` 別表。
  新オプション `emissionTable` ＋ `tableSources`（`BRIDGE_PK` 列を持つ道路音源表）を渡すと、
  ASJ スペクトルを既存 emission 表に **`EMISSION_TYPE='BRIDGE'` ＋ 各 on-deck 道路音源の `IDSOURCE`**
  で追記。exec() を「(deck, period) ごとのスペクトルを 1 回計算」→「別表 or 追記に分岐」に整理。

#### v1 との共存

`BRIDGE_STRUCT_LW` 別表方式は残る（ただの `SOURCE_NOT_RELATED_TO_BRIDGE` 表）。option 1 と併用不可
（構造物音の二重計上）。スクリプト説明文・javadoc に排他を明記済。

#### テスト（全緑）

- 新規 `BridgeStructuralNoiseTest`（jdbc）: `BOTH` == `ROAD`-only run ＋ `BRIDGE`-only run のエネルギー和
  （±0.3 dB。二重計上・クロスルーティングなし）／ デッキ無しの `BRIDGE` 行は drop（≒無音）。
- 新規 `TestStructuralNoiseFromBridge#emissionTypeModeAppendsBridgeRowsKeyedOnOnDeckRoadSources`（scripts）:
  on-deck 2 音源にのみ `BRIDGE` 行、off-deck 音源には無し、既存 `ROAD` 行不変、スペクトルは低域優勢。
- 回帰: pathfinder 252 / propagation 61 / jdbc 61（+2）緑。`BridgeNoiseMapTest`（`8099944b` で赤だった
  もの）緑。scripts `TestStructuralNoiseFromBridge` 5 緑。

#### 残（v2.3）

- `Road_Emission_from_Traffic.groovy` 内での ROAD/BRIDGE 同時生成（案 A、`fix` 相当の完全自動）。
- `INPUT_MODE_TRAFFIC_FLOW*` からの `EMISSION_TYPE` 生成（現状 LW 表経由のみ）。
- ~~`fix` 側にも `986d8ad6` の共有壁コピーを入れる~~ → ✅ **2026-09-17 実施**（§4.17 参照）。

#### BRIDGE_PK による音源→デッキ高さの引き継ぎ（2026-09-09 実装・`feat/bridge-v2` `848ba0a2`）

`Road_Emission_from_Traffic` → `Noise_level_from_source` の実運用パイプラインを調査したところ、
デッキ上の音源が正しく `ACTUAL_SOURCE_ON_BRIDGE` に分類されない**実運用上の穴**が見つかった:

- `Road_Emission_from_Traffic` は音源の Z を無条件に `ST_UpdateZ(geom, 0.05d)` で 0.05m に強制上書き
  （CNOSSOS道路音源の慣習）。元のROADSテーブルでデッキ高さをZに入れていても消える。
- `Noise_level_from_source` 側の相対→絶対Z変換（`confSourcesZIsAltitude=false`、既定）は
  `ProfileBuilder.offsetCoordinateToAltitudeUsingDigitalElevationModel` = `z + getZGround(coord)`で
  **DEM地表面のみ**。橋を一切考慮しないため、デッキ上の音源のつもりが地表付近に配置される。
- 橋を考慮した絶対高さ変換 `ElevationConverter.calculateAbsoluteElevation` は既に実装済みだったが、
  **本番コードのどこからも呼ばれていなかった**（死んだコード、`grep`で呼び出しゼロを確認）。

**採用方針:** `fix` の `HEIGHT_TYPE`（相対/絶対の行単位フラグ）はそのまま持ち込まない
（upstreamの`confSourcesZIsAltitude`というシーン全体のグローバルフラグは既存のまま維持、
コアクラスへの変更を最小限にする方針に合わせる）。代わりに、v1 の `Structural_noise_from_bridge`
の `tableSources` が既に使っている **`BRIDGE_PK` 列という規約**を本流にも拡張。列は任意・追加的
（無ければ従来通りDEM基準、非橋シーンに回帰なし）:

- `Road_Emission_from_Traffic`: 入力ROADSテーブルに`BRIDGE_PK`列があれば`LW_ROADS`にコピー
  （既存の`IDSOURCE`と同じ扱い）。
- `ProfileBuilder.makeGeometryRelativeZToAbsoluteOnBridge`（新規）: 該当橋のデッキ高さを基準に
  相対Zを解決。`DefaultTableLoader.fetchCellSource`が音源行の`BRIDGE_PK`（非NULL）を見て
  DEM版の代わりにこちらを呼ぶ。
- 新規テスト `TestNoiseLevelFromBridge#bridgePkCarriesTheDeckHeightThroughRoadEmissionFromTraffic`:
  同じ交通量ROADSテーブルを`BRIDGE_PK`ありなしで`Road_Emission_from_Traffic`→`Noise_level_from_source`
  にフル投入し、LEQが11.5dB以上変わることを確認（`BRIDGE_PK`なしだと音源がデッキ footprint 内の
  地表面＝`IMAGINARY_SOURCE_UNDER_BRIDGE`扱いになり低周波寄りの地面効果減衰を受け、`BRIDGE_PK`ありだと
  デッキ高さ＝`ACTUAL_SOURCE_ON_BRIDGE`でその減衰を免れる方が値が大きくなる、という向き）。
  scripts 101→102、他モジュール回帰ゼロ。

---

### 4.13 橋壁の向きと地盤標高プロファイル（2026-09-08 決定・`fix` も是正）

対象は `CutPointBridgeWall` の 3 向き（`WallDirection`）のうち **DOWNWARD**（音源が橋の下）と
**OTHER**（音源が橋の上・受音点が下方）。いずれも「音源側の地盤基準がデッキ（下面 or 上面）なのに
実地形点が余分に差し込まれ、S 側平均地盤面フィットが傾く」という同一クラスの不具合。

#### DOWNWARD（音源が橋の下）

**問題:** `computePtsGround` / `expandCutPointsToElevationProfile`（S→R セグメントの平均地盤面を
フィットするための地盤標高点列を作る処理）が、`CutPointBridgeWall` の **DOWNWARD**（デッキ下面エッジ）に
対してその **天端 z（デッキ下面高さ）を地盤標高点として挿入**していた。

- **旧 v2**（v1 ②改修の専用 `CutPointBridgeWall` 分岐）: `(zGround, z, zGround)` の 3 点
  ＝ デッキ端を「地形上に立つ薄い衝立」として扱う。
- **fix**（`CutPointBridgeWall extends CutPointWall` なので素の `CutPointWall` 分岐に落ちる。
  専用分岐＋`ascendBridge`/`discendBridge` は分岐順で**到達不能な死にコード**）: `BUILDING_EXIT` に対し
  `(z, zGround)` の 2 点 ＝ `BUILDING_EXIT`＝「屋根の上を通ってきて出口で地面に落ちる」の意味論。

**どちらも物理的に不適切。** DOWNWARD エッジはデッキ**下面**、すなわちその点で ray より**上**にある。
地盤標高（ray より下の反射面）ではない。imaginary/mirror 音源はデッキ下を進むので、その区間の地盤は
**実地形のみ**であるべき。天端 z を入れると平均面フィットが歪み、等価音源高・音源鏡像がずれて
`aDif` の `deltaGroundSO` に ~0.5 dB のアーティファクトが出る（TBC31 imaginary で顕在化。CNOSSOS は
under-deck 伝搬を規定しておらず、TBC12/13 の平坦地面では影響が出ないため見逃されていた）。

**採用（`fix`・v2 共通）:** `CutPointBridgeWall.getWallDirection() == DOWNWARD` のとき、地盤標高点列には
**`(x, y, zGround)` の地形点 1 点のみ**を追加し、天端 z は入れない。UPWARD（高欄など ray が上越えする縁）は
従来どおり `(zGround, z, zGround)` の薄スクリーン形。

- `fix`: `expandCutPointsToElevationProfile` の `CutPointWall` 分岐の**直前**に専用 `else if`
  （`pnt instanceof CutPointBridgeWall && getWallDirection()==DOWNWARD && getBridgePk()!=bridgePkOn`）→
  `(x, y, zGround)` 地形点のみ、`lastZ` は更新、`lastGroundZ=NaN`。音源が乗っているデッキ
  （`getBridgePk()==bridgePkOn`）は従来どおり素の `CutPointWall` 分岐へ落とす。
- v2: `computePtsGround` の「音源が乗っていないデッキ」`else` 分岐を `getWallDirection()` で分ける
  — DOWNWARD → 地形点 1 点のみ、UPWARD/OTHER → `(zGround, z, zGround)`。
  旧 `7d17821b`（fix の 2 点順に寄せる中間版）を amend して `84ced451` に置換
  （TBC31 全 4 経路 assert の test 変更はそのまま保持）。
- 影響 golden（`fix`、2026-09-08 再生成）: `TBC01/04/07/09/10/11/12/20/21/30/31_attenuation_output.json`。
  大半は `cfH`/`cfF` が 0.02–1.1 動くのみで最終 `L` は不変。実レベルが動くのは **TBC31 path0
  （IMAGINARY 音源）の +0.5 dB**＝本件で問題にしていた残差そのもの（全帯域で解消側に動く）。
  TBC12/TBC31 は `verticesSoundLevel` の並び順が変わるが同フィールドは非アサート。
  `PathFinderBridgeTest`（cut 点のみ格納・不変）、TC01–TC28（非橋）は不変。

#### OTHER（音源が橋の上・受音点が下方＝`ACTUAL_SOURCE_TO_LOWER_RECEIVER`）

**問題:** `OTHER` エッジは `BridgeService.calculateFirstBridgeCutpoint` が生成し、`intersectionType` を
**設定しない**（＝既定の `THIN_WALL_ENTER_EXIT` のまま）。すると `fix` の `CutPointWall` 分岐で
`isWall` 経路に入り `(zGround, z, zGround)` の 3 点＝**先頭に地形点**が入る。音源はデッキ上なので
音源〜外縁エッジ間の地盤基準はデッキ面であるべきで、この先頭地形点は DOWNWARD と同種の偽点。
（UPWARD/`ACTUAL_SOURCE_ON_BRIDGE` は `intersectionType` が本当に `BUILDING_EXIT` なので
既に `(z, zGround)` ＝先頭地形点なし。fixture 上 UPWARD・OTHER とも relation は必ず
`ACTUAL_SOURCE_ON_BRIDGE`。）

**採用（`fix` 先行、2026-09-08）:** `getWallDirection() == OTHER` のとき UPWARD の `BUILDING_EXIT` と
同じく `(z), (zGround)` を出し、`overArea=false`。外縁エッジは実回折ピボットなので `(z)` スパイクは残す
（消すのは先頭地形点だけ）。

- `fix`: `expandCutPointsToElevationProfile` に `getWallDirection()==OTHER` の専用 `else if` を追加。
- 影響 golden: `TBC02_attenuation_output.json` の path1（ACTUAL 経路）のみ。`cfH` −0.38、
  `aGlobalH`/`L` が全帯域で約 **−0.2 dB**（band0: L 61.26→61.06）。他 TBC・非橋は不変。
- **v2**: v2 の `computePtsGround` は `OTHER` エッジを `cutPointBridgePk == bridgePkOn` 分岐で
  受け `exiting` 以外は何もしない＝外縁エッジが地盤プロファイルから落ちている。`fix` に合わせて
  `(z),(zGround)` を足す試行をしたが、δ も O→R 平均面もほぼ動かず TBC02 path1 の差は残った（撤回）。
  その ~2.6 dB は `aDif` の実装差（upstream は CNOSSOS 2.5.31/2.5.33 の「受音点が平均面より下」分岐、
  `fix` の旧 `aDif` は非対応）で、**upstream が正しい**＝移植不要（§4.10 #2）。
  v2 の `BridgeAttenuationGoldenTest` クラス javadoc に計装結果を記録（`b953226c`）。

---

### 4.14 v0/v1 スコープ再編 — デッキ地盤基準 と BRIDGE_PK を v0 へ降格（方針決定・実施完了、2026-09-11）

**きっかけ:** §4.12 末尾の BRIDGE_PK 対応（`848ba0a2`）と、v1 squash コミット `311f86f1d`
（`CutProfile.computePtsGround` のデッキ上音源地盤基準化）を並べて見直したところ、どちらも
**ASJ 構造物音とは無関係な、CNOSSOS-EU 準拠の位置・地盤基準の正しさの問題**であり、
「v0 = CNOSSOS-EU 準拠の変更のみ」という v0 の方針に照らせば **v1/v2 ではなく v0 に属する**と判断した。

**対象 2 点（どちらも v0 に既存の依存だけで実装可能。`git grep` で確認済み — `Bridge.getDeckHeightAtPoint`
/ `BridgeService.getBridgeByPk` / scripts の `tableBridgePoints` 入力は v0 の時点で揃っている）:**

1. **デッキ上音源の地盤基準化**（現状 v1 squash `311f86f1d` = `CutProfile.computePtsGround`
   の `CutPointSource` 分岐、§本ファイル前段の該当箇所参照）。
   `ACTUAL_SOURCE_ON_BRIDGE` に分類された音源の地盤（S側平均地盤面フィットの基準点）を、
   実地形ではなく**デッキ面**にする。ASJ 構造物音を一切使わない場合でも、デッキ上の音源を
   素の CNOSSOS-EU で扱うだけでこの補正がなければ高さ・地面効果・回折ジオメトリが誤る。
2. **BRIDGE_PK による相対→絶対 Z 解決**（現状 v2 `848ba0a2` =
   `ProfileBuilder.makeGeometryRelativeZToAbsoluteOnBridge` / `offsetCoordinateToBridgeDeck`、
   `DefaultTableLoader.fetchCellSource` の `hasBridgePk` 分岐、`Road_Emission_from_Traffic.groovy`
   の `BRIDGE_PK` 列パススルー）。
   `Road_Emission_from_Traffic` → `Noise_level_from_source` の実運用パイプラインで、相対高さの
   音源を DEM 地表面ではなくデッキ高さ基準で絶対 Z に変換する。これも ASJ 構造物音の有無に関係なく、
   「デッキ上に音源を置く」という v0 の主張そのものを実運用データで成立させるために必要。

**新しいスコープ境界:**

| | 新スコープ |
|---|---|
| **v0** | デッキ＝上越え衝立（既存）＋ **デッキ上音源の地盤基準をデッキ面にする**（①、降格）＋
  **BRIDGE_PK による相対 Z 解決**（②、降格）。「CNOSSOS-EU 準拠の橋梁ジオメトリ・位置補正」全般が
  v0 の役割になる。propagation 本体（`AttenuationCnossos`/`CnossosPathBuilder`）は引き続き無改修。 |
| **v1** | **ASJ 構造物音源の追加のみ**に純化（`emission.road.asj` パッケージ、
  `Structural_noise_from_bridge` script、構造物音固有のテスト）。①②は前提として v0 から継承し、
  v1 側の差分からは消える。 |
| **v2** | 変更なし（デッキ下受音点・ミラー音源・BT 回折・`EMISSION_TYPE` 統合、§4.10/§4.11/§4.12/§4.13）。 |

**実施内容（2026-09-11、隔離 worktree `git cherry-pick` で実施。全ブランチ未 push のローカル専用なので
history 書き換えは安全）:**

1. `feat/bridge`（v0、旧 tip `3fd9f597`）に `311f86f1d`（デッキ地盤基準、①）→
   `8a76235e`（BRIDGE_PK、②）を順に cherry-pick。**無コンフリクト**。新 tip `fd3e1847`。
2. `feat/bridge-v1` を 新 v0 tip の上に**再構築**：①（`311f86f1d`）は v0 に移ったため落とし、
   残る ASJ 関連 3 コミット（`424f471b`/`1feee706`/`c10deb8c`）のみ cherry-pick。**無コンフリクト**。
   新 tip `7d9f3452`。
3. `feat/bridge-v2` を 新 v1 tip の上に**再構築**：②（`8a76235e`）は v0 に移ったため落とし、
   残る BT/`EMISSION_TYPE`/カバレッジ監査 3 コミット（`f33ace0a`/`126a1d4c`/`1293231b`）のみ
   cherry-pick。**無コンフリクト**。新 tip `64cb9a81`。
   **`git diff <旧 feat/bridge-v2> <新 tip> --` は空、tree hash も完全一致**
   （`339e42ef...`）— 再編は純粋な履歴の並べ替えで、最終内容は 1 バイトも変わっていないことを確認済み。
4. 新スタック上でフルクリーン回帰: **emission 66 / pathfinder 252 / propagation 65 / jdbc 62 /
   scripts 103（skip 4）、全緑・旧スタックと同数、回帰ゼロ**。
5. 3 ブランチのポインタ（`feat/bridge` / `feat/bridge-v1` / `feat/bridge-v2`）を新 tip へ移動
   （`git branch -f`）。隔離 worktree・作業用ブランチ（`bridge-v1-new`/`bridge-v2-new`）は削除済み。
6. 上記「ロードマップ（v0 / v1 / v2）」表を新境界に合わせて更新済み（v0 に降格注記、
   v1/v2 の該当行に「再編後は…」の注記）。

> 旧スタックの内容は git reflog（各ブランチの旧 tip: v0 `3fd9f597`、v1 `c10deb8c`、
> v2 `8a76235e`）からいつでも参照可能。

#### 補足調査: 音源高さがデッキ高さより高い場合の地盤基準（2026-09-11）

①のデッキ地盤基準化は「音源の実際の高さ」ではなく「**デッキそのものの計算高さ**」
（`bridge.getDeckHeightAtPoint(c)`）を地盤基準点として使う。そのため音源が
デッキ面よりわずかに高い（車体・排気管など）場合でも、地盤基準からの高さ差として
正しく効いてくる ── これは狙いどおりの挙動。

ただし分類（`classifySourceAgainstBridges` → `Bridge/BridgeQueryHelper.isPointOnBridge`）には
**既定 2.0m の Z 許容差**があり（`Math.abs(point.z - deckAbsoluteHeight) <= tolerance`）、
音源高さがデッキ面から **2.0m を超えて上**にあると `ACTUAL_SOURCE_ON_BRIDGE` に分類されず
（`isPointOnBridge` も `isPointBelowBridge` も false）、`BridgeRelationship` は既定値
`SOURCE_NOT_RELATED_TO_BRIDGE` のまま・`bridgeHeight` は `NaN` のままになる。結果として
`computePtsGround` は①のデッキ地盤基準分岐に入らず、通常の地形地盤基準
（`cut.getzGround()`、＝デッキ下の実地形）にフォールバックする。

**つまり境界の 2.0m を境に地盤基準が「デッキ面」から「実地形」へ不連続にジャンプする**
（デッキ厚が数 m ある橋では、実地形はデッキ面よりさらに数 m 〜 十数 m 下）。実データで
音源がデッキ footprint 内にありながらデッキ面から 2m 超浮いているケースは通常ないため
実害は薄いが、境界条件として認識しておく価値はある。対応要否は未検討（現時点では
「既知の挙動」として記録するのみ、修正の要否は今後の課題）。

---

### 4.15 v1（構造物音）↔ v2（回折パス）の順序入れ替え（実施完了、2026-09-17）

#### 発見: v0/v1 には「橋の下を通る音源」の正しい回折がない

現行 v0/v1（BT 回折より前）を調査したところ、**橋の下を走る道路の音源が、本来ブロック/迂回されるべき
箇所で正しく扱われていない**ことを確認した。

- `BridgeService.createBridgeCutPoint` は DOWNWARD（デッキ下端）交点についても
  `isObstructingAcousticRay` を方向別に正しく判定している（直線経路がデッキ下面より上に出る＝
  本来ブロックされるべき、という判定自体は v0 の時点で実装済み）。
- しかし `WallDirection` / `bottomRoute` / `topRoute` / `spliceBridgeBottomEdge` は
  `noisemodelling-pathfinder`/`noisemodelling-propagation` の実際の経路構築コード
  （`CnossosPathBuilder`/`CnossosPath`/`AttenuationCnossos`）のどこにも参照されていない
  （v0/v1 で `git grep` して確認）。`CutPointBridgeWall` は `CutPointWall` を継承しているため、
  DOWNWARD 側でブロックと判定された点も、通常の建物・壁と同じ「障害物の上を回折する」標準
  CNOSSOS 凸包アルゴリズムにそのまま投入される。
- つまり v0/v1 では、「デッキの下をくぐる」正しい回折（ミラー音源による BT 二重回折）が存在せず、
  本来ブロック/迂回すべき経路が建物と同じ上越え回折式で（物理的に不適切に）計算されてしまう。
  この機構は v2 の BT 回折（§4.10）で初めて導入されている。

#### 提案: v1 と v2 の入れ替え

現行の実装順（v0 → v1: ASJ構造物音 → v2: BT回折/EMISSION_TYPE分離）は**実装のしやすさによる順序**
であり、機能の見通しとしては「まず橋の下/上の音を正しく伝搬させる（回折パス）→ その上にデッキ自体が
音を出す音源モデル（構造物音）を足す」の順の方が自然、という指摘を受けて調査した。

**依存関係の調査結果:** BT 回折関連ファイル
（`CnossosPathBuilder`/`CnossosPath`/`AttenuationCnossos`/`DistanceDifferenceCalculator`/`PointPath`/
`BridgeAttenuationGoldenTest`）と ASJ 構造物音関連ファイル
（`emission.road.asj` パッケージ・`Structural_noise_from_bridge.groovy`）は**完全に素集合**
（コメント 1 箇所を除き相互参照ゼロ）。「構造物音の `EMISSION_TYPE` 分離」コミットのみ両方に依存するが、
これは元の順序でも BT 回折の**後**に来ていたため、入れ替えても BT→EMISSION_TYPE分離の相対順序は不変。
→ **入れ替えは技術的に可能**と判断。

**新しい順序（隔離 worktree で実施・検証済み。ブランチポインタは未移動）:**

| | 新スコープ |
|---|---|
| **新 v1**（回折パス、v0 の直上） | BT 回折（ミラー音源技法＋下面/上面二重回折の合成、`DIFB` 点）＋ TBC04/05/06/08/09/10/21/30 の回折ゴールデンテスト（元「v2.2 カバレッジ監査」コミットから構造物音に無関係な部分を分離）。**上記の v0/v1 ギャップがここで解消される** |
| **新 v2**（構造物音、新 v1 の直上） | ASJ RTN-Model 2023（`emission.road.asj`）＋ `Structural_noise_from_bridge` script（旧 v1 の 3 コミットそのまま）＋ `EMISSION_TYPE` 分離（構造物音をデッキ裏面に配置）＋「構造物音が BT 経路を正しく見つける」統合テスト（元「v2.2 カバレッジ監査」コミットの残り半分） |

**検証:**
- 全コミット cherry-pick で**無コンフリクト**（ファイル集合が素なので当然の結果）。
- 「v2.2 カバレッジ監査」コミット（`BridgeAttenuationGoldenTest` 加筆 ＋ `BridgeStructuralNoiseTest` 加筆
  の 2 種類が混在していた）はテスト対象で機械的に分割：前者は新 v1、後者は新 v2 へ。
- 再編直後（§4.16 のテスト整理コミットを含む前）の新 v2 tip の tree hash は、当時の
  `feat/bridge-v2` と**完全一致**（`339e42ef...`）— 内容は 1 バイトも変わっていない、
  純粋な履歴の並べ替えであることを確認済み（その後 §4.16 のテスト整理を新 v0 に追加したため、
  現在の新 v2 tip はこの時点から更に進んでいる）。

#### 発見: 新 v1 単体では較正済みでない箇所が 1 件あった

新 v1（BT のみ、ASJ 抜き）を単体でフルクリーン回帰したところ、
`TestNoiseLevelFromBridge.bridgeDeckShieldsAGroundReceiver` の 63 Hz 帯で
`loss 3.82 dB outside [3.82, 10.32]`（境界ぎりぎりで strict `>` に抵触）が 1 件失敗した。

原因: この許容範囲の下限（`predicted - 1.5`）は元々、旧 v1 の `c10deb8c`（ASJ script コミット）で
`-1.5 → -2.0` に緩められていた（コミットの注釈は「①デッキ地盤基準化の影響」）。**「各 vN ブランチは
単体で常に全緑であるべき」という前提を守るため**、この較正緩和（`-1.5 → -2.0`）を新 v1 の直後の
コミットとして前倒しした。

> **訂正（2026-09-18、§4.18）:** 当時は「BT が同梱する DOWNWARD エッジ地盤プロファイル是正（§4.13）が
> ズレの原因」と誤って結論したが、後日 v0 単体を改めてフルクリーン回帰したところ **v0 単体でも
> 同じ箇所が同じ量だけ失敗する**ことが判明（v0 には BT は無い）。真の原因は①（デッキ上音源の
> 地盤基準化、§4.14 で v0 へ降格）そのものであり、旧 `c10deb8c` の元々の注釈「①の影響」が最初から
> 正しかった。較正緩和は v0 まで前倒しし直し、詳細は §4.18 を参照。

**最終確認（2026-09-17、§4.16 のテスト整理 4 コミット適用後）:**
- 新 v1 単体: emission 28 / pathfinder 200 / propagation 64 / jdbc 59 / scripts 98（skip 4）、**全緑**。
- 新 v2（最終形）: emission 66 / pathfinder 200 / propagation 65 / jdbc 62 / scripts 103（skip 4）、**全緑**。
- 両者とも隔離 worktree（`/tmp/wt-v1-check` = 新 v1、`/tmp/wt-v1v2-swap` = 新 v2）で
  独立にフルクリーン回帰済み。並列実行時に `NoiseModellingServerHttpTest` がポート 8000 衝突で
  1 件だけ見かけ上失敗したことがあったが、単体再実行で無関係（環境要因）と確認済み。

**実施済み（2026-09-17）:** `feat/bridge-v1` を新 v1 tip へ、`feat/bridge-v2` を新 v2 tip へ
`git branch -f` で移動。`feat/bridge`（v0）は §4.14/§4.16 の時点で既に更新済み。隔離 worktree・
作業用ブランチ（`v1-new-diffraction`/`v2-new-structural`）は削除済み。3 ブランチとも未 push の
ローカル専用のため、外部への影響なし。**この時点の tip はいずれも §4.18 で更に置き換わっている
（旧 v1 tip `5abb1346` → `299a4857`、旧 v2 tip `32417724` → `4672285f`）。**

---

### 4.16 Bridge 系ユニットテストの冗長性整理（4 ファイル完了、2026-09-17）

#### 方針

橋梁機能の実装を進めながら都度追加してきた `noisemodelling-pathfinder` の低レベルユニットテスト
（`Bridge*Test.java`）に、「同じメソッドを境界値違いで何度も検証する」自明な重複が多数見つかった。
**「1テスト1シナリオ（1つの物理的・論理的主張）」を基準に、真に重複しているテストのみ統合する**方針
で整理。個別のアサーションを寄せ集めて1テストの守備範囲を広げるのとは異なり、**異なる主張を1テストに
混ぜない**ことを徹底（失敗時の原因特定・レビュー時の可読性を落とさないため）。

判断基準:
- 同じメソッド・同じ観点を、境界値だけ変えて複数テストに分けているだけ → 統合
- 異なる観点・異なる不変条件を検証している → 分離を維持
- 恒真式（`assertTrue(true, ...)` を全分岐で通す、`list.size() >= 0` など、失敗し得ないアサーション）
  → 統合ではなく**削除**

作業は隔離 worktree で実施し、各ファイル単体でのテスト実行で全緑を確認してから
v0（`feat/bridge`）に個別コミットとして追加、その都度 new-v1/new-v2（§4.15 の再編ブランチ）を
再構築して回帰確認、という手順を反復した。

#### 実施結果（4ファイル、95+42 → 57+28 = 137 → 85 件、-52 件）

| ファイル | 件数 | 主な統合内容 |
|---|---|---|
| `BridgeQueryHelperTest` | 32→16 | `isPointOnBridge`許容差系4件→1、`isPointAboveBridge`/`isPointBelowBridge`系（NaN・可変値違い）各3件→各1、footprint系5件→2、`isRelevantForReflection`系4件→1（**恒真の null 入力テスト1件を削除**）、`getEnvelope2D`+高精度版→1、`updateGeometry`2件→1。**C字型の凹形状テストを削除**（L字型テストと同じ主張の重複） |
| `BridgeTriangulationTest` | 33→26 | 空入力系3件→1（既存のより網羅的なテストに統合）、有効入力系2件→1、`Triangle.contains()`/重み計算の重複区間をより厳密な専用テストへ集約、**恒真の「robustness」テスト1件・`size()>=0`という恒真式のテスト1件を削除**、位置バランス不正の重複1件を削除 |
| `BridgeGeometryBuilderTest` | 30→15 | 幅バリエーション4件→1（パラメータ化）、edge数チェック3形状→1、コンストラクタ/getter/setter3件→1（最も網羅的な1件に統合）、単一点での例外4件→1、Right/Left方向のposition確認2件を、より厳密なオフセット検証テストに統合。**単一橋梁点での例外テストの文字通りの重複1件を削除** |
| `BridgePointManagerTest` | 42→28 | null/空入力ガード3件→1、not-found系3件→1、`interpolateDeckHeight`の「有効な隣接点が足りない」例外系3件→1、`updateGroundHeight`/`getGroundHeightAtPoint`のNaN返却ガード各2件→各1。**既存のfound/not-foundテストに包含される重複2件、より網羅的なテストの部分集合1件、コンストラクタの自明なスモークテスト1件を削除** |

全ファイル、整理後も**アサーションの内容は1つも失っていない**（恒真式・純粋な重複のみ削除）ことを
各ファイル単体実行の全緑で確認済み。v0（`feat/bridge`）に4コミットとして追加し、
§4.15 の new-v1/new-v2 を都度再構築、フルクリーン回帰で全緑を再確認済み。

#### 残作業（未着手）

- `noisemodelling-pathfinder` の残りの小さいファイル: `BridgeFactoryTest`(6)・`BridgePointTest`(4)・
  `BridgeServiceTest`(2)・`BridgeBehaviorTest`(3)・`BridgeProfileBuilderTest`(7) — 件数が少なく
  今回の4ファイルほどの重複密度は見込みにくいが未調査。
- `noisemodelling-emission` の ASJ 系: `RoadAsjParametersTest`(20)・`RoadAsjTest`(15) — 中規模で
  同様のパターンがある可能性あり。物理モデルの数式検証なので、統合するにしても内容を個別に読んで
  から判断する必要がある（優先度は低め）。
- `noisemodelling-propagation`/`noisemodelling-jdbc`/scripts groovy系のテスト（golden test・
  結合テスト）は元々シナリオごとに明確に分かれており、今回のような重複パターンは見込みにくい。

---

### 4.17 `fix` への逆輸入 — 共有橋壁セグメントのコピー漏れ（2026-09-17 実施）

§4.12（`986d8ad6`）で見つけた「`BridgeService.createBridgeCutPoint` が処理済み橋壁の `LineSegment` を
コピーせず渡し、`CutPointBridgeWall.modifyIntersectionHeight` がそれを in-place で書き換える」バグは
`fix` 側の `BridgeService.java` にも**同じ形で存在**することを確認済みだった（§4.12「残（v2.3）」に
「`fix` にも同じ潜在バグ持ち」と記録）。ポート作業がひと段落したので、この既知の未対応項目を解消。

**適用:** `fix` の `createBridgeCutPoint`（v2 と同じ行）を `new LineSegment(wall.getLineSegment())` に
変更（v2 の修正と同一パッチ）。

**副作用（想定通り）:** `fix` 自身の `PathFinderBridgeTest`（golden JSON 方式）で **7 件が失敗**。
原因を確認したところ、**旧ゴールデン値自体がこのバグの影響を受けた誤った値**だった
（例: TBC01 の `_imaginary`（デッキ下面エッジ）は正しくは高さ 9.5m だが、旧フィクスチャは
同じ処理済み壁を再利用する別プロファイル（デッキ上面 11.0m）の書き込みが漏れて焼き込まれた
11.0m を期待していた）。`PathFinderBridgeTest.overwriteTestCase` を一時的に `true` にして
該当 11 ファイルを再生成、目視で「デッキ下面/裏面エッジは薄く・上面/高欄エッジは高く」という
物理的な妥当性を確認してから `false` に戻し、コミット。

**再生成した golden JSON（11 件）:** `TBC01_imaginary` / `TBC07_imaginary` / `TBC10_mirror` /
`TBC11_imaginary` / `TBC12_imaginary` / `TBC13_mirror` / `TBC20_actual` / `TBC20_imaginary` /
`TBC21_mirror` / `TBC30_imaginary` / `TBC31_actual`。

**検証:** `PathFinderBridgeTest` 18/18、`AttenuationComputeOutputCnossosBridgeTest`（propagation の
attenuation 出力 golden）17/17、`fix` フルクリーン回帰 emission 67 / pathfinder 499 /
propagation 98 / jdbc 79 / scripts 103（skip 4）— 全緑、他への回帰なし。

**まだ確認していないこと:** `fix` 側でこのバグが実際の計算結果（ノイズマップ等、単体テスト以外の
出力）にどの程度影響していたかは未調査。今回は単体テストのgolden値のみ是正した。

---

### 4.18 v0 単体の較正漏れ — `bridgeDeckShieldsAGroundReceiver` を v0 でも修正（2026-09-18）

「v0/v1/v2 それぞれで何件テストが増えたか」を正確に数え直す過程で、**v0（`feat/bridge`）を単体で
フルクリーン回帰すると1件失敗する**ことが判明した。`TestNoiseLevelFromBridge.bridgeDeckShieldsAGroundReceiver`
の63Hz帯で、§4.15で見つけたのと同じ境界（`predicted - 1.5`）にわずかに抵触。

**原因の再整理:** §4.15では「新v1単体の較正漏れはBT起因」と記録したが、今回v0単体でも同じ失敗が
再現したことで、**真の原因は§4.14でv0へ降格した①（デッキ上音源の地盤基準化）そのもの**であり、
BTは無関係（v0にBTはまだ無い）と判明。§4.15の当時の記述は誤りだったので訂正する。
（旧v1の`c10deb8c`が元々このマージンを`-1.5→-2.0`に緩めていた際の注釈「デッキ地盤基準化の影響」は、
実は最初から正しかった。）

**対応:** v0（`feat/bridge`）に較正緩和コミットを追加（`4c84fd36`、旧`5abb1346`と同じ`-1.5→-2.0`）。
v1・v2 は旧`5abb1346`が今や重複になるため cherry-pick から外して再構築。ASJ script コミットの
cherry-pick が同じ行でマージされた際に残った重複コメント（旧v1由来の説明文）も削除して統合。

**新しい tip（旧 tip から置き換え）:**

| ブランチ | 旧 tip | 新 tip |
|---|---|---|
| `feat/bridge`（v0） | `0e62e7d0` | `4c84fd36` |
| `feat/bridge-v1` | `5abb1346` | `299a4857` |
| `feat/bridge-v2` | `32417724` | `4672285f` |

**検証:** v0 単体 emission28/pathfinder200/propagation55/jdbc59/scripts98（skip4）=440 全緑。
v1 単体 emission28/pathfinder200/propagation64/jdbc59/scripts98（skip4）=449 全緑（BT追加分のみ、
新たな較正は不要だった＝BT自体はこのマージンに影響しないことも確認）。v2（最終形）
emission66/pathfinder200/propagation65/jdbc62/scripts103（skip4）=496 全緑、§4.16時点と同数
（テストの増減ではなく、既存1件が常に緑になっただけ）。

---

### 4.19 デッキ上音源→デッキ下受音点の直達距離バグ — `fix` と v0/v1/v2 全部に是正（2026-10-01/02）

ユーザーからの指摘「音源がデッキ上、受音点がデッキ下のとき、直達距離は垂直エッジ回折と同じように
cutprofile からは計算できないはず」を発端に調査し、実在のバグを確認した。

**問題:** CNOSSOS の δ = SO + e + OR − distance という式の `distance`（直達距離）は、本来
source-receiver 間の真の3D距離（`dc`）であるべきだが、`CutProfile` を2D展開した座標列から導出される
`d`（`JTSUtility.getNewCoordinateSystem` 由来、全点が source/receiver と同一平面上＝水平共線である
ことが前提）が使われる箇所があった。垂直エッジ回折（DIFV）は非共線になり得るため既に `dc` 使用に
補正済みだったが、**橋デッキの「OTHER」方向エッジ（音源がデッキ上、受音点がデッキ下/脇）のピボットも
DIFV と同じく Fermat 探索（直線交差ではない）で求まるにもかかわらず、点種別が DIFH のまま生成される
ため、この補正から漏れていた**。

実測（`fix` の `TBC02` 実在フィクスチャ）: `d=10.833m`（誤り）に対し `dc=7.574m`（正しい）、
帯域によって最終予測レベルが約0.9〜2.2dB変動。v0 側の新規テスト用ジオメトリでも
`d=41.689m` vs `dc=40.963m` を確認（軽微だが非ゼロの乖離）。

**対応:** 両系列とも、既存の「DIFV なら `dc` を使う」判定に「橋デッキの OTHER 方向エッジが含まれるか」
を OR で追加する最小修正。

- `fix`: `CnossosPathProcessor.setDiffractionPathParametersT` に `hasOffAxisBridgeEdge` 判定を追加。
- v0/v1/v2: `CnossosPathBuilder.computeCnossosPathFromCutProfile` の該当 `difVPointCount` 判定に
  同様の `hasOffAxisBridgeEdge` 判定を追加（`CutPointBridgeWall.getWallDirection() == OTHER` で検出）。

`fix` では `AttenuationComputeOutputCnossosBridgeTest.TBC02` の golden（`TBC02_attenuation_output.json`）
を再生成。v0/v1/v2 では既存の golden フィクスチャはこの非共線シナリオを実際には踏んでおらず、
再生成は不要だった（全緑）。v0 には本バグを固定する新規テスト
（`BridgePropagationTest.sourceOnDeckToReceiverBelowAndBesideUsesTrueDirectDistance`）を追加し、
v1・v2 はそのまま cherry-pick で引き継いだ。

**新しい tip（旧 tip から置き換え）:**

| ブランチ | 旧 tip | 新 tip |
|---|---|---|
| `fix` | `0fb3a6e7` | `399083da` |
| `feat/bridge`（v0） | `4c84fd36` | `2aec58ae` |
| `feat/bridge-v1` | `299a4857` | `ad46b769` |
| `feat/bridge-v2` | `4672285f` | `d3c95f02` |

**検証:** `fix` フルクリーン回帰 emission67/pathfinder499/propagation98/jdbc79/scripts103（skip4）
= 846、他への回帰なし。v0 単体 emission28/pathfinder200/propagation56/jdbc59/scripts98（skip4）
=441（§4.18比+1、新規テスト分）。v1 単体 emission28/pathfinder200/propagation65/jdbc59/scripts98
（skip4）=450（+1）。v2（最終形）emission66/pathfinder200/propagation66/jdbc62/scripts103（skip4）
=497（+1）。いずれも cherry-pick 時のコンフリクトは import 行や隣接テストメソッドの追加位置など
自明なもののみで、ロジック面の衝突はなし。

---

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
