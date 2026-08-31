# `fix` 最終差分インベントリ

`git diff 46c9380a..fix`（共通祖先 → `fix` HEAD）の全 **376 ファイル / +79,387 / −6,400** を、
[merge_project.md](merge_project.md) の F1〜F8 バケツ + 周辺(P) + 対象外(X) に仕分けたもの。
目的は「Track A（Bridge を upstream 現行構造に薄く載せる）で、何をそのまま持ってきて、何を捨て/書き直すか」を確定すること。

- 生成日: 2026-08-31
- 共通祖先: `46c9380a`（2025-07-30）
- `feat/road-asj` に F6 は移植済み（本表では ✅済 と表記）

---

## バケツ定義

| 記号 | 意味 |
|------|------|
| **F1** | `ProfileBuilder` のサービス分解 + 基盤クラス（`*Service`, `FrequencyConfig`, `ProfileUtils` …） |
| **F2** | `CutProfile` の作り直し（API 非互換） |
| **F3** | `PathFinder` / cnossos path 生成の分解（`ReceiverProcessor`, `AcousticPath*`, `PivotPoint*` …） |
| **F4** | `Scene` の作り直し + `HeightType`（相対/絶対 Z を列で制御） |
| **F5** | Bridge 本体（`Bridge*`, `BridgeService`, `ElevationConverter`, `*Ext`, TBC テストケース） |
| **F6** | ASJ 排出モデル（`emission/road/asj/`） |
| **F7** | JDBC 配線・設定クラス分離（`TableInputSettings`, `*Settings`, `RoadEmissionBuilder`, output/* …） |
| **F8** | scripts（`wps_scripts` → upstream では `noisemodelling-scripts`） |
| **P** | 周辺・独立した小変更（カプセル化、utility、コメント整理） |
| **X** | upstream 対象外（開発メモ、生成物、IDE 設定、個人ビルド調整） |

## 移植可否（replay 判定）

| 記号 | 意味 |
|------|------|
| ✅ | `git checkout fix -- <path>` でそのまま置ける（新規追加 or upstream 未変更） |
| ⚠️ | upstream も同ファイルを変更。ハンク単位で手当てが必要 |
| ✂️ | upstream と非互換な書き換え。ファイル移植は不可 → 再実装 |
| 🚫 | 移植しない |
| ✅済 | 移植完了（`feat/road-asj`） |

---

## サマリ（モジュール × バケツ）

| モジュール | F1 | F2 | F3 | F4 | F5 | F6 | F7 | F8 | P | X |
|-----------|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| pathfinder/main | 25 | 3 | 22 | 6 | 15 | – | – | – | 10 | 1 |
| propagation/main | – | – | 20 | 2 | 3 | – | 1 | – | 1 | – |
| jdbc/main | – | – | – | 2 | – | – | 30 | – | 2 | – |
| emission/main | – | – | – | – | – | 2 | – | – | 7 | – |
| wps_scripts/main | – | – | – | 2 | – | – | – | 13 | – | – |
| test（全モジュール） | ~18 | 3 | ~12 | ~4 | ~28 | 3✅済 | ~14 | 1 | ~10 | 2 |
| resources（test_cases） | – | – | 28 (TC) | – | 43 (TBC) | 1 (json) | – | – | – | – |
| Docs-dev ほか | – | – | – | – | – | – | – | – | – | ~45 |

**バケツ別の全体判定:**

| バケツ | Track A での扱い |
|--------|------------------|
| F1 | ✂️ **移植しない**。upstream の `ProfileBuilder` は構造ほぼ不変（1672→1658）。サービス分解は差分にならず、B0 合意が無い限り出さない |
| F2 | ✂️ **移植しない**（最難関）。`CutProfile` は両側が別方向に大改修、API 非互換 |
| F3 | ✂️ **移植しない**。upstream の cnossospath-refactor（#991）と正面衝突 |
| F4 | 分割: `HeightType` は #956 の**最小修正**として ⚠️ 手当てで再実装。`SceneBuilder` 等は ✂️ |
| F5 | 🔨 新規ファイル単体は ✅ コピー可だが、`ProfileBuilder`/`CutProfile`/`Scene` への **hook が F1/F2/F4 前提**。Track A では hook を upstream 構造向けに書き直す |
| F6 | ✅済（`feat/road-asj`） |
| F7 | ✂️ 大半が upstream の input/output refactor と競合。`ComputationSettings` 等の設定分離は Bridge 後に再評価 |
| F8 | 🔨 `noisemodelling-scripts` へ手作業移植（モジュール名・パス変更） |
| P | 個別判定（下表）。小粒で単独 PR 価値は低いが replay は容易 |
| X | 🚫 |

---

## pathfinder/src/main（137 ファイル中 main java 68）

### F1 — ProfileBuilder サービス分解 + 基盤（✂️ 移植しない）

| ファイル | 種別 | 行 | upstream競合 |
|----------|------|---:|:---:|
| `profilebuilder/ProfileBuilder.java` | M | 905/1119 | ⚠️ ✂️ |
| `profilebuilder/TopographyService.java` | A | 992 | – |
| `profilebuilder/BuildingService.java` | A | 384 | – |
| `profilebuilder/WallService.java` | A | 230 | – |
| `profilebuilder/GroundService.java` | A | 242 | – |
| `profilebuilder/ProcessedWallService.java` | A | 160 | – |
| `profilebuilder/FrequencyConfig.java` | A | 263 | – |
| `profilebuilder/ProfileUtils.java` | A | 114 | – |
| `profilebuilder/ClearableService.java` `FrequencyInitializable.java` `GeometryFactoryProvider.java` `ElevationComputable.java` `ProcessedFacetsExportable.java` `RayWallIntersection.java` `ResultSetConstructor.java` `LoggingUnmodifiableList.java` | A | 各小 | – |
| `profilebuilder/Building.java` `BuildingIntersectionPathVisitor.java` `Wall.java` `GroundAbsorption.java` `WallAbsorption.java` `Obstruction.java` `CutPoint.java` | M | – | ⚠️ |
| `path/MirrorReceiversCompute.java` | M | – | ⚠️（`.p0`→`getP0()` のみ）|
| `profilebuilder/ProfileBuilderDecorator.java` | D | – | ⚠️（upstream は保持）✂️ |
| `utils/UniqueKeyGenerator.java` `utils/geometry/RTreeUtils.java` | A | – | – |

### F2 — CutProfile 作り直し（✂️ 移植しない）

| ファイル | 種別 | 行 | |
|----------|------|---:|:---:|
| `profilebuilder/CutProfile.java` | M | 471/105 | ⚠️ ✂️ 最難関 |
| `profilebuilder/CutPointGroundEffect.java` | M | – | ⚠️ |
| `profilebuilder/CutPointWall.java` | M | – | ⚠️（F5 とも関連）|

### F3 — PathFinder 分解（✂️ 移植しない）

| ファイル | 種別 | 行 | |
|----------|------|---:|:---:|
| `PathFinder.java` | M | 116/1004 | ⚠️ ✂️（1142→254 に解体）|
| `ReceiverProcessor.java` | A | 275 | – |
| `DiffractionPathBuilder.java` | A | 456 | – |
| `ReflectionPathProcessor.java` | A | 269 | – |
| `SourceCollector.java` | A | 278 | –（相対→絶対 Z も担当。F4 と関連）|
| `SourcePointInfo.java` `ReceiverPointInfo.java` | A | 177 / 小 | –（値オブジェクト。単体なら P 寄り）|
| `PathExecutionManager.java` `ReceiverBatchScheduler.java` `ReflectionPathBuilder.java` `DirectAndDiffractionEvaluator.java` `GeometryUtils.java` | A | 158/…/137 | – |
| `CutPlaneVisitor.java` `DefaultCutPlaneVisitor.java` `ThreadPathFinder.java` `ThreadPool.java` `CutPointReflection.java` | M | – | ⚠️（一部）|

### F4 — Scene 作り直し + HeightType

| ファイル | 種別 | 行 | 扱い |
|----------|------|---:|------|
| `path/Scene.java` | M | 565/56 | ✂️（181→689）。**HeightType の考え方のみ** #956 最小修正へ |
| `path/SceneBuilder.java` | A | 159 | ✂️ |
| `profilebuilder/CutPointSource.java` | M | 226/19 | ✂️（heightType, bridgeRelationship フィールド追加）|
| `profilebuilder/CutPointReceiver.java` | M | – | ✂️ |

### F5 — Bridge 本体（新規ファイルは ✅ コピー可、hook は書き直し）

| ファイル | 種別 | 行 | |
|----------|------|---:|:---:|
| `profilebuilder/BridgePoint.java` | A | 758 | ✅ |
| `profilebuilder/BridgeService.java` | A | 741 | ✅ |
| `profilebuilder/Bridge.java` | A | 685 | ✅ |
| `profilebuilder/BridgePointManager.java` | A | 446 | ✅ |
| `profilebuilder/BridgeTriangulation.java` | A | 440 | ✅ |
| `profilebuilder/BridgeGeometryBuilder.java` | A | 334 | ✅ |
| `profilebuilder/BridgeQueryHelper.java` | A | 296 | ✅ |
| `profilebuilder/CutPointBridgeWall.java` | A | 193 | ✅ |
| `BridgeAnalyzer.java` | A | 102 | ✅ |
| `ElevationConverter.java` | A | 240 | ✅（`Bridge` を import。F4 とも関連）|
| `LineStringSplitter.java` | A | 201 | ✅（#880 にも関連）|
| `path/BridgeRelationship.java` | A | 小 | ✅ |
| `profilebuilder/BridgePoint*` の hook（`ProfileBuilder`, `CutProfile`, `Scene` 内）| — | — | ✂️ **F1/F2/F4 前提。upstream 構造向けに書き直し** |

> Bridge クラス群の相互依存は浅い（実測）: `BridgeService`→`BridgeRelationship`(F5)+`RTreeUtils`(F1、小)、
> `BridgePoint`→`Scene`（upstream にも存在）、`Bridge.java`/`BridgeGeometryBuilder`/`BridgeAnalyzer` は fix 新規クラスに非依存。
> → サービス足場一式を持ち込まなくても、`RTreeUtils` + `BridgeRelationship` の随伴だけで Bridge クラスは移植できる見込み。
> Track A の作業は主に「`ProfileBuilder`/`CutProfile`/`Scene` から `BridgeService` を呼ぶ hook」を upstream 構造向けに書くこと。

### P — pathfinder 周辺（個別 replay 可）

`utils/documents/GeoJSONDocument.java` `utils/documents/KMLDocument.java`(⚠️) `utils/geometry/CoordinateMixin.java`
`utils/geometry/JTSUtility.java` `utils/profiler/RootProgressVisitor.java` `delaunay/LayerTinfour.java`(⚠️) —
いずれも小。単独 PR の価値は要吟味。

### X

`Docs-dev/pathfinder_data_class.md`（1 行）等。

---

## propagation/src/main（82 ファイル中 main java 26）

### F3 — cnossos path 生成の分解（✂️ 移植しない）

`cnossos/CnossosPathProcessor.java`(A 751) `cnossos/AcousticPath.java`(A 540) `cnossos/PivotPointCalculator.java`(A 375)
`cnossos/ReflectionPointValidator.java`(A 202) `cnossos/DistanceDifferenceCalculator.java`(A 195)
`cnossos/CnossosSegmentComputer.java`(A 154) `cnossos/AcousticPathConfiguration.java`(A 123)
`cnossos/AcousticPathGeometryCalculator.java`(A 107) `cnossos/SegmentBuildResult.java` `PathCreationResult.java`
`PathSegment.java` `PathSegmentBuilder.java` `PivotPoint.java`（A 小）
+ M: `cnossos/CnossosPath.java` `CnossosPathBuilder.java` `PointPath.java` `SegmentPath.java` `AttenuationCnossos.java`
（すべて ⚠️）+ `cnossos/Path.java`（M 108/13、**upstream は削除** → ✂️）
+ `AttenuationVisitor.java` `AttenuationComputeOutput.java`（M ⚠️）

### F5 — Bridge 拡張（✅ 新規のみ）

`cnossos/AttenuationCnossosExt.java`(A 1108) `cnossos/CnossosPathExt.java`(A) — ✅（ただし呼び出しは F3 前提）

### F4

`SceneWithAttenuation.java`（M 199/33、HeightType 読み込み、⚠️）→ #956 最小修正へ吸収 or ✂️

### P

`AttenuationParameters.java`（M ⚠️） `ReceiverNoiseLevel.java`（M）

---

## jdbc/src/main（64 ファイル中 main java 30）

### F7 — 設定クラス分離・配線（✂️ 大半 upstream と競合）

新規: `TableInputSettings.java`(213) `CalculationIOSettings.java`(276) `ComputationSettings.java`
`ReceiverGenerationSettings.java`(141) `RoadEmissionBuilder.java`(434) `SourceEmissionBuilder.java`(261)
`input/CellProfileLoader.java`(434) `input/EmissionInputSettings.java`(148) `input/PropagationSettings.java`(138)
`input/SourceDataCache.java`(170) `input/CellSceneContext.java` `input/EmissionInputSettingsView.java`
`input/LoaderInitContext.java` `input/SceneDatabaseInputSettingsView.java` `input/SourceEmission.java` `input/TableLoader.java`

変更（すべて ⚠️）: `GridMapMaker.java`(96/226) `NoiseMapByReceiverMaker.java`(163/120)
`EmissionTableGenerator.java`(207/194) `DelaunayReceiversMaker.java` `input/SceneWithEmission.java`(159/108)
`output/AttenuationOutputSingleThread.java`(164/108) `output/AttenuationOutputMultiThread.java`
`output/NoiseMapWriter.java` `output/DefaultCutPlaneProcessing.java` `output/ResultsCache.java`

削除（upstream は変更 → ✂️）: `NoiseMapDatabaseParameters.java` `input/SceneDatabaseInputSettings.java`

### F4

`input/DefaultTableLoader.java`（M 231/436、HeightType 読み込み + 設定、⚠️）→ #956 最小修正で該当箇所のみ
`NoiseMapByReceiverMaker.java` の `makeSourceRelativeZToAbsolute()` 削除 → **upstream は `ZIsAltitude` フラグで別実装済み**。取り下げ

### P

`utils/AscReaderDriver.java`（M ⚠️） `utils/IsoSurface.java`（M ⚠️）

---

## emission/src/main（15 ファイル）

| ファイル | バケツ | 扱い |
|----------|--------|------|
| `road/asj/RoadAsj.java` `road/asj/RoadAsjParameters.java` | F6 | ✅済 |
| `road/cnossos/RoadCnossos.java` | P | 🚫（コメント文言のみ）|
| `utils/interpLinear.java` | P | 🚫（コメントのみ）|
| `railway/nmpb/RailwayNMPB.java`(31 行) `railway/cnossos/RailwayCnossos.java` `railway/cnossos/RailWayCnossosParameters.java` `directivity/DiscreteDirectivitySphere.java` `directivity/cnossos/RailwayCnossosDirectivitySphere.java` | P | 🚫（code-quality 由来の軽微整理。⚠️ 一部 upstream も変更）|

---

## wps_scripts/src/main（15 ファイル）→ F8

すべて `noisemodelling-scripts/src/main/groovy/.../scripts/**` へ**手作業移植**（モジュール名・パッケージ変更）。
`NoiseModelling/Noise_level_from_source.groovy` `Noise_level_from_traffic.groovy` の HEIGHT_TYPE 入力説明は F4 に連動。
`runner/Main.java` は upstream に対応物なし（要確認）。

---

## テスト（86 追加 / 20 変更 / 1 削除）

| グループ | バケツ | 例 | 扱い |
|----------|--------|-----|------|
| ASJ | F6 | `emission/road/asj/RoadAsj*Test`（3）| ✅済 |
| Service 単体 | F1 | `BuildingServiceTest` `WallServiceTest` `GroundServiceTest` `TopographyService*Test`(×3) `ProcessedWallServiceTest` `FrequencyConfigTest` `ProfileUtilsTest` `ProfileBuilderFrequencyTest` … | ✂️ F1 と運命共同 |
| CutProfile | F2 | `CutProfileTest` `CutPointTest` `CutPointReceiverTest` | ✂️ |
| PathFinder 分解 | F3 | `ReceiverProcessorTest` `DiffractionPathBuilderTest` `DirectAndDiffractionEvaluatorTest` `PathExecutionManagerTest` `ReflectionPathBuilderTest` `SourceCollectorTest` `GeometryUtilsTest` `LineStringSplitterTest` `CnossosSegmentComputerTest` | ✂️ |
| Scene | F4 | `path/SceneTest` `SceneWithAttenuationTest` | ✂️ / 一部再利用 |
| Bridge | F5 | `PathFinderBridgeTest` `profilebuilder/Bridge*Test`（8）`AttenuationComputeOutputCnossosBridgeTest` `jdbc/BridgeTrafficTest` `jdbc/BridgePointMappingTest` `wps/TestNoiseLevelFromBridgeTraffic` | 🔨 F5 実装後に移植 |
| JDBC 配線 | F7 | `SourceIdentificationTest` `ReceiverIdentificationTest` `*WithDbTest` `SceneWithEmissionWithDbTest` `AttenuationOutputSingleThreadTest` `DefaultTableLoaderFetchCellSourceTest` `DelaunayReceiversMakerTest` | ✂️ / 再評価 |
| fetchCell リネーム | F7 | `fetchCellBuildingTest` `fetchCellGroundTest` `fetchCellTerrainTest`（旧 `FetchCell*Test`）+ `DefaultTableLoaderFetchCellSourceTest` `input/FetchCellSourceTest` | ✂️ F7 前提。**クラス名が小文字始まり = Java 規約違反。移植する場合も PascalCase に戻す** |
| TC 回帰フィクスチャ | F3 | `TC01–TC28_attenuation_output.json`（28）+ `AttenuationComputeOutputCnossosTCTest` | ✂️ fix の path 実装依存 |
| デバッグ残骸 | X | `utils/TC11DebugTest.java` `utils/profiler/ProfilerThread*Test` | 🚫 |
| その他 upstream 系の充実 | P | `GeoJsonToH2GISTest` `SpecialCasesTest` `FetchAtmosphericSettingsTest` `MakeParallelLinesTest`(M) `RegressionTest`(M) | 個別判定 |

---

## resources / test_cases

| グループ | 数 | バケツ | 扱い |
|----------|---:|--------|------|
| `pathfinder/test_cases/TBC*.json`（`*_actual` `*_imaginary` `*_mirror` `*_not_related`）| 43 | F5 | 🔨 Bridge テストと一緒に |
| `propagation/test_cases/TC*_attenuation_output.json` | 28 | F3 | ✂️ fix の path 実装依存。upstream には再生成が必要 |
| `propagation/test_cases/TBC*_attenuation_output.json` | 15 | F5 | 🔨 |
| `emission/road/asj/RoadAsj_2023.json` | 1 | F6 | ✅済 |
| `TutoBridge/*.geojson`（buildings, roads, dem, receivers, bridgepoints）| 複数 | F5 | 🔨 チュートリアル資産 |

---

## 対象外（X）— upstream に出さない ＋「要確認」項目の精査結果（2026-08-31）

| パス | 判定 | 精査メモ |
|------|------|----------|
| `Docs-dev/**`（33、うち `img/*` バイナリ多数、+30,942 行）| 🚫 X | 個人開発メモ |
| `.vscode/**`（3）`.mvn/jvm.config` `nbactions.xml` `preparation.sh` | 🚫 X | IDE / ローカルビルド設定 |
| `javadoc-warnings-unique.txt` `test_coverage_summary.md` | 🚫 X | 作業メモ |
| `noisemodelling-tutorial-01/**`（3）| 🚫 X | upstream で**モジュールごと削除済み** |
| `.gitignore`(+27) | 🚫 X | 個人向け ignore（`tests/*` `.gradle/*` `calc_test/*` 等）。**`*.png` を無視しているのが危険**（`Docs/` に正規の PNG あり）。末尾改行欠落 |
| `pom.xml`(12/2) | 🚫 X（一部 P）| javadoc を `doclint none` + `failOnError/Warnings false` に緩める = **CI 品質ゲートの弱体化**。upstream 不可。`junit-jupiter-params` 依存追加は P（`@ParameterizedTest` を使う PR の時だけ随伴。upstream 既存の可能性あり）|
| `.github/workflows/CI.yml`(8/3) | ⚠️ P（弱い候補）| deploy step の `if:` を `env.MAVEN_USERNAME != null` → `env.X != ''` の明示チェックに変更。**GH Actions では未設定 secret は `null` でなく `''` なので `!= null` は常に真** → fork でも deploy step が走る、という実在のバグ修正。ただし upstream の CI.yml は乖離（`working-directory: ./wps_scripts` 等）。単独 PR にするなら upstream 現行 CI へ載せ替え。優先度低 |
| `README.md`(+1) | 🚫 X | `Docs-dev/build-setup-guide.md`（fix 専用ファイル）へのリンク追加。upstream に無いファイル参照 |
| `Docs/Data_Assimilation_Tutorial.rst`(4/2) | 🚫 X（F8 依存）| `sourcesTableName` → `sourceTableName`。fix の WPS パラメータ改名に連動。**upstream のスクリプトが未改名なら誤り**。F8 移植時に upstream スクリプトと照合 |
| `Docs/scripts/postgis_nm.java`(50/…) | ✂️ F7 依存 | fix の Builder API（`PropagationSettings.Builder` `TableInputSettings.Builder` `NoiseMapByReceiverMaker.Builder`）へ全面書き換え。typo `TableIInputSettings` あり。F7 が無いとコンパイル不能 |
| `wps_scripts/.../runner/Main.java`(6/1) | 🚫 X（P-low）| unused import 削除 + `@SuppressWarnings("unchecked")` + 明示ジェネリクスキャスト + shadow ローカル改名。**振る舞い不変の code-quality のみ**。単独 PR の価値ほぼ無し。F8 移植時に upstream 版へ取り込む程度 |

**精査の結論: 「要確認」だった項目に、Bridge に先行して出せる単独 PR 級のものは無い。**
最も近いのは CI.yml の deploy ガード（実在バグだが upstream CI との乖離が大きく、価値も小）。
`runner/Main.java` と `pom.xml` の `junit-jupiter-params` は F8 / 必要時に随伴させる程度。

---

## この表からの結論

- **そのまま replay できるのは F6（✅済）と F5 の新規ファイル群だけ**。ただし F5 は `ProfileBuilder`/`CutProfile`/`Scene`
  への hook が F1/F2/F4 前提なので、Track A では hook を upstream 構造向けに**書き直す**必要がある。
- **F1・F2・F3・F7 はファイル移植不可**（upstream が同じ領域を別設計で改修済み）。これらを upstream に入れるには
  B0 Discussion での合意 → 再実装（＝トラック B）が必要。Track A では**持ち込まない**。
- **単独先行 PR にできるものは無い**。#956 は調査済み（コード修正不要 = `confSourcesZIsAltitude` で解決済、
  `Noise_level_from_traffic` は deprecated）。docs のみの差だが本質的でなく PR 見送り。
- 次アクション: Bridge+ASJ 設計 issue 起票 → F5 の最小実装。
