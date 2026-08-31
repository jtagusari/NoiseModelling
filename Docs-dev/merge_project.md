# fix ブランチを upstream/main へ取り込む方策

> 更新日: 2026-08-31 / 対象: `fix` (= `origin/fix`) → `upstream/main`
> upstream = `https://github.com/Universite-Gustave-Eiffel/NoiseModelling.git`
> ファイル単位の仕分け表 → [fix_diff_inventory.md](fix_diff_inventory.md)

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

**したがって Bridge に先行して出せるのは実質:**

1. **#956 の最小修正**（唯一の未解決の正しさギャップ）。
   `fix` の*知見*（`Noise_level_from_traffic` 経由だと音源 Z が DEM+0.05 に固定されず二重加算されうる。
   `Road_Emission_from_Traffic` は固定している）は有効。ただし *解決策* は `fix` のコード（per-row
   `HEIGHT_TYPE` + `Scene` 作り直し）ではなく、upstream の `ZIsAltitude` フラグに沿った surgical fix
   （traffic 経路も同じ扱いに揃える）として出す。→ ブランチ `fix/issue-956`。
2. （任意）F1/F3 の**振る舞い不変の局所リファクタ**（`SourcePointInfo` / `ReceiverPointInfo` の値オブジェクト化、
   `SourceCollector` 抽出など）。ただし upstream の「構造維持 + perf パッチ」方針では *それ自体が改善* とは
   見なされにくく、B0 Discussion の合意が前提。単発では出さない。

### 3.6 直近の進め方

1. F6 ASJ（`feat/road-asj`、実施済み）— push は Bridge 設計 issue と同時（「呼び出し側は Bridge」）。
2. **#956 の最小修正**を `fix/issue-956` で実装 → 単独 PR（`fix` 由来と明記せず、通常のバグ修正として）。
3. **Bridge + ASJ の設計 issue** を起票（トラック A 前提。「現行構造の上に薄く載せる」方針を提示）。
4. issue の反応を見て Bridge 本体（F5）の最小実装に着手。F7/F8（JDBC 配線・scripts）はその後。

### 3.7 upstream に出さないもの

`Docs-dev/**`, 生成 `test_cases/*.json`（68 個）, `.vscode/`, `.mvn/`, `javadoc-warnings-unique.txt`,
`nbactions.xml`, `preparation.sh`, `test_coverage_summary.md`, `noisemodelling-tutorial-01`（upstream で削除済み）。

### 3.8 F6（ASJ）の実施状況

**ブランチ:** `feat/road-asj`（`integ/upstream-sync` = `upstream/main` 起点、コミット `5a645a53` 1 本）

移植ファイル（6 / +1,076 行、**既存ファイル変更ゼロ**）:

- `emission/road/asj/RoadAsj.java`, `RoadAsjParameters.java`
- `emission/src/main/resources/.../road/asj/RoadAsj_2023.json`
- `emission/src/test/java/.../road/asj/RoadAsj{,Formula,Parameters}Test.java`（計 38 テスト）

検証: `JAVA_HOME=jdk-25 mvn -pl noisemodelling-emission test` → **66 passed / 0 failed**。

`RoadAsj` の javadoc は "bridge virtual source emission" と明記。実体は F5（Bridge）の音源側であり、
**単独 PR にするか B5 に束ねるかは B0 Discussion の結果で決める**。JDBC 配線（F7）は含めない。
push・PR は Discussion 後。

---

## 4. 次のアクション

### 完了済み

1. ~~`fix` にタグを打って保全~~ → ✅ `archive/fix-2026-08-31`
2. ~~`integ/upstream-sync` ブランチを作成~~ → ✅ 作成済み（`upstream/main` を追跡）
3. **JDK 25** → ✅ `C:\Program Files\Java\jdk-25.0.4.1` 導入済み。
   現行 `upstream/main` は `pom.xml` で `<source>25</source><target>25</target>`。
   ビルド/テスト時は `JAVA_HOME` を jdk-25 に向ける（例: `JAVA_HOME="C:\Program Files\Java\jdk-25.0.4.1" mvn ...`）。
   `.m2` に無い新依存（jackson 2.21 / commons-codec 1.19 / h2gis 2.2.6-SNAPSHOT 等）はオンラインで取得が必要。
4. ~~F6 ASJ を `feat/road-asj` に移植・テスト~~ → ✅ 66 passed（3.8 参照。push は保留）
5. ~~F1〜F4 の先行単独 PR 候補を調査~~ → ✅ 強い候補なし（3.5。正しさ系は upstream が既に別 PR で修正済み）
6. ~~インベントリの「要確認」項目を精査~~ → ✅ 単独 PR 級なし（[fix_diff_inventory.md](fix_diff_inventory.md) の X 表。
   最も近いのは CI.yml の deploy ガードだが価値小・upstream CI と乖離）

### これから（トラック A。3.6 参照）

1. **#956 の最小修正**を `fix/issue-956` で実装（`Scene` 作り直しを伴わない surgical fix。
   upstream の `ZIsAltitude` フラグに沿って traffic 経路も揃える）→ 単独 PR。
2. **Bridge + ASJ の設計 issue** を起票（トラック A 前提。「現行 `ProfileBuilder`/`CutProfile`/`PathFinder`/`Scene`
   の上に薄く載せる」方針を提示。関連 #909）。
3. issue の反応を見て Bridge 本体（F5）の最小実装 → その後 F7（JDBC 配線）→ F8（scripts）。

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
