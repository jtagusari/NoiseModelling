# Bridge v1 — core 改修の要否分析（2026-09-01）

`fix` のフル橋梁計算を upstream に入れるにあたり、pathfinder / propagation コアに
どこまで手が入るか、「既存結果を変えない・性能を落とさない」をどう示すかの調査メモ。
[merge_project.md](merge_project.md) §3.7 / [bridge_upstream_proposal.md](bridge_upstream_proposal.md) の補足。

---

## 1. 発端 — route A / B と `perf_*` PR

Bridge を「新しい障害物タイプ」として既存流儀で組み込むと `ProfileBuilder`(~1825行) /
`CutProfile` / `Scene` / `CutPoint*` に手が入る。この monolith に **seam（拡張点）が無い**
ことが摩擦の原因。

- **route A** = 現行構造のまま薄い hook（v0 PR がこれ）
- **route B** = 障害物タイプ別処理を共通インターフェース（`ObstacleLayer` 的なもの）の
  裏に出す behaviour-preserving 抽出。`ProfileBuilder` は登録レイヤを回すだけになる。

### `perf_*` PR の実体（sachabaclet フォーク、2026-09-01 確認）

`git remote add sachabaclet https://github.com/sachabaclet/NoiseModelling.git` で確認。

| ブランチ | 状態 | 触る場所 | 中身 | 規模 |
|---|---|---|---|---|
| perf_cut_profile_hull | **merged** | CutProfile 凸包 | (main 済) | — |
| perf_attenuation_matrix_product | merged | propagation | — | — |
| perf_ground_effect_constants | merged | — | — | — |
| perf_profile_intersections | open (7/12) | `ProfileBuilder.addGroundBuildingCutPts` | 候補ごとの `fullLine.intersection()` を `RobustLineIntersector` 1 個の使い回しに／`LinkedList`→`ArrayList` | +6/−2 |
| perf_prepared_ground | open (7/14) | `ProfileBuilder` + `GroundAbsorption` | `PreparedGeometry` キャッシュ | +7/−2 |
| remove_unused_wall_index | open (7/14) | `ProfileBuilder` | 死んでる `wallTree` STRtree 削除 | −3 |
| perf_vedge_hull | open (7/10) | `PathFinder` + `BuildingIntersectionPathVisitor` | 側方回折の凸包探索 | +133/−34 |
| perf_mirror_receiver_index | open (7/10) | `PathFinder` + `MirrorReceiversCompute` | 反射受音点インデックス | +49/−15 |
| perf_receiver_batches | open (7/10) | `PathFinder` | 受音点バッチ化 | +4/−2 |
| perf_shared_path_geometry | open (7/10) | `AttenuationCnossos` + `CnossosPathBuilder` | fav/homo でパス幾何共有 | +95/−35 |
| perf_maxerror_pruning | open (8/11) | `AttenuationOutputSingleThread` (jdbc) | 電力バジェット枝刈り | +19/−3 |

**結論:**
- open な perf_* はどれも**構造を変えない外科的マイクロ最適化**。seam を作るものは無い。
- `ProfileBuilder` に触る open PR は 3 本、各 ≤7 行。`getProfile()` のタイプ別カット点
  シーケンスや `isFreeField()` — seam を彫る場所 — には触れていない。
- → route B の seam は perf_* と**きれいにリベースできる**。「衝突するから順序調整必須」
  という当初の見立ては誇張だった。
- upstream は perf PR を 3 本 merge 済（`perf_cut_profile_hull` 含む）＝この領域の perf
  改修に前向き。ただし open 8 本は約 2 か月動きなし。

## 2. 「性能が落ちない」— シグナル vs 証拠

`perf_profile_intersections` が「候補ごとの intersector 確保」を潰しているという事実は:

- **シグナル**: maintainer は `getProfile()` 内ループの確保コストを実測して手を入れる。
  → seam で仮想ディスパッチ＋リスト反復を入れたら**その観点で精査される**。
  「テスト緑」では足りず「ベンチ不変」が合格ライン。
- **証拠ではない**: monolith vs seam の計測は 1 つも無い。仮想ディスパッチのコストは
  ゼロ（4種程度の安定リスト → JIT devirtualize）かもしれないし数 % かもしれない。
  perf_* が潰しているのは確保と冗長計算で、うまく作った seam はそれを増やさない
  （レイヤは `finishFeeding` で 1 回だけ生成）。

JVM で性能直感は当てにならない（インライン閾値・devirtualization・エスケープ解析は
コード読みでは予測困難）。信頼できるのは JMH 的な代表入力での実測のみ。
「monolith の方が JIT に優しい」も**仮説であって証拠ではない**。

## 3. フル橋梁計算が core に及ぼす範囲

`fix` の実装を確認した結果、共有パス（全シーンで走る）に橋分岐が入るのは:

### pathfinder

| 箇所 | 内容 | 非橋での状態 |
|---|---|---|
| `ProfileBuilder.getProfile()` | 音源分類 + `addBridgeCutPts` | v0 で完全に `if(bridgeCount>0)` ガード済 |
| `CutProfile` 標高/Z-ground 計算 | `ascendBridge`/`discendBridge`/`checkAboveRoof`（`instanceof CutPointBridgeWall` 分岐）| リストに `CutPointBridgeWall` 不在 → 死コード。ただし `fix` では F2 で周辺コードごと書き換え済 → upstream 版へ hand-splice が必要 |

### propagation — ここが v0 と本質的に違う

**橋の回折 = 再帰的コンポジット。** ある経路がデッキ**本体**を下から回り込んで上へ抜ける
とき（上端 `DIFH` と下端 `DIFB` の両方を持つ）:

`CnossosPathProcessor.setDiffractionPathParametersBT()`:
1. 下端を除いた `CutProfile` で `CnossosPathBuilder.buildCnossosPath()` を**再入呼び出し**
   → `deltaH` を得る
2. `deltaB` と比較して支配的な縁を決定
3. S→B と B→R（または S→T）の部分経路を、**縁をフィルタした `CutProfile`** で再入ビルド
   （`newCutProfileWithoutBottomEdge` / `newCutProfileOnlyWithBottomEdge` /
   `newCutProfileAfterBottomEdge` / `newCutProfileBeforeTopEdge`）
4. 子として `cnossosPathBottomRoute` / `cnossosPathTopRoute` にぶら下げる

`AttenuationCnossosExt.aBoundary()`（483–491 行）:
```java
if (diffPtsT.size() >= 1 && diffPtsB.size() >= 1) {
    double[] aBoundaryT = aBoundary(path.getCnossosPathTopRoute(),  ...);   // 再帰
    double[] aBoundaryB = aBoundary(path.getCnossosPathBottomRoute(), ...); // 再帰
    for (int i...) aBoundarySum[i] = aBoundaryT[i] + aBoundaryB[i];         // バンドごとに和
    return aBoundarySum;
}
```

`CnossosPathExt`（88 行、`extends CnossosPath`）が持つのは実質この 2 フィールドだけ:
```java
private CnossosPathExt cnossosPathBottomRoute = null;
private CnossosPathExt cnossosPathTopRoute = null;
```

→ 「既存の `aBoundary` に `if` を足して通常ロジックにフォールスルー」にはならない。
橋ケースは境界計算を**再帰和で置き換える**。

### `AttenuationCnossosExt` / `CnossosPathExt`(~3000行) は不要

型名（`CnossosPathExt`→`CnossosPath`）と引数名（`attenuationParameters`→`data`）を
正規化して `fix:AttenuationCnossosExt` と `fix:AttenuationCnossos` を diff すると、
差分はほぼ全部 rename ＋整形。`bridge`/`imaginary`/`mirror` を含むメソッドは無い。
この型フォークは `fix` が `Path`/`CnossosPath` 層を作り直した（PR #991 と衝突する領域）
副産物。upstream で `CnossosPath` を維持するなら不要。

### upstream に載せるのに必要な加算（すべて橋発火時のみ、非橋では不活性）

| クラス | 追加 | 規模 |
|---|---|---|
| `CnossosPath` | nullable な `bottomRoute`/`topRoute` フィールド 2 個 | 小 |
| `CnossosPathProcessor`/`Builder` | BT 分解 + `CutProfile` 縁フィルタ 4 種 + 再入ビルド | ~140 行 |
| `CutProfile` | `CutPointBridgeWall.WallDirection`（UP/DOWN）+ フィルタコピー | 中 |
| **`AttenuationCnossos.aBoundary()`** | 再帰和の分岐 | **~15 行、CNOSSOS コア内** |

既約の新規物理は合計 **~150 行**。ただし **`AttenuationCnossos` 本体（TC01–TC28 が
pin しているクラス）に届く**のが v0（propagation 無改修）との決定的な違い。

## 4. 「結果が変わらない・性能が落ちない」の示し方

### 結果不変（非橋シーン）

1. **構造的保証**: 全橋分岐を `if (hasBridgeIntersection)` / `instanceof CutPointBridgeWall`
   / `getCnossosPathBottomRoute() != null` の明示ガードで差し込む。`fix` のように周辺を
   restructure しない（F1/F2 を持ち込まない）。→ 非橋挙動は構成上保存、レビューで局所確認可。
2. **golden 差分 harness（本命の証拠）**: `04793d59` と橋ブランチの両方で
   - 既存の全チュートリアル資産 ＋ TutoBridge-橋なし ＋ 合成シーン 2〜3 個
     （小/中/大・密な建物＋DEM＋地面ゾーン＋反射＋両回折モード）
   - `NoiseMapByReceiverMaker` を回し `RECEIVERS_LEVEL` 全体（受音点 × 全 1/3 oct ＋
     LAEQ/LEQ）を CSV ダンプ
   - **完全一致**（|Δ| < 1e-9 dB、FP 再結合のみ許容）をアサート

   既存テスト（±0.1〜1 dB 許容の assert）より強い。全受音点 × 全バンドを見る。
3. **既存スイート不変**: ~460 テスト（TC01–TC28 含む）が期待値・許容値そのままで緑。
   必要条件、十分ではない（2 が本体）。
4. **橋自体の golden**: `fix` の TBC01–TBC31（`_actual`/`_imaginary`/`_mirror`/
   `_not_related`、pathfinder profile ＋ propagation attenuation-output 両方）を橋機能の
   回帰 fixture として移植。

propagation が `AttenuationCnossos` コアに届くようになったため、2 と 3 は
「あると良い」から**必須ゲート**に格上げ。

### 性能不変

ベンチ基盤ゼロ（JMH なし、タイミングテストなし）。追加する:

| Bench | 対象 | 方法 |
|---|---|---|
| 1 | `ProfileBuilder.getProfile()` | 大きな**橋なし**シーンを `@Setup` で 1 回ロード、~1000 の source→receiver ペア（固定・シャッフル）。`AverageTime`、3 fork、warmup 5×2s、measure 10×2s |
| 2 | `CnossosPathBuilder.computeCnossosPathsFromCutProfile()` | 同シーンの固定 cut profile 群 |
| 3 | end-to-end 壁時計 | フル `NoiseMapByReceiverMaker`、M 回捨てて K 回計測、中央値 ＋ MAD |

**プロトコル**: 同一マシン・同一 JDK・静音・電源接続、A/B/A/B… インターリーブ、各 ≥5 rep、
日を変えて複数回。回帰＝run 間ばらつきを超える一貫した方向シフト（単発の遅さではない）。
ノート PC で ±2〜3% は誤差。Bench 1/2 が `bridgeCount==0` でシフト → ガード漏れ →
JFR/async-profiler で局所化 → ガード修正 → 再計測。橋ありシーンは別途計測
（追加コスト＝機能の対価、回帰ではない）。

## 5. 方針

- **`fix` の F1（service 分解）も F2（`CutProfile` 書き換え）も、`*Ext` 型フォークも
  持ち込まない。** upstream の `ProfileBuilder`/`CutProfile`/`CnossosPath`/`AttenuationCnossos`
  構造を維持し、橋分岐だけをガード付きで splice。
- seam / route B の議論はいったん棚上げ（restructure しないのでディスパッチのベンチ論争が
  不要）。必要になったら別途、実測付きで upstream に提案。
- 配送: v0 PR（遮蔽・凸包経由・propagation 無改修）→ v1 PR（`CutProfile` 橋標高 splice +
  `CnossosPath` 子経路 + `CnossosPathProcessor` BT 分解 + `AttenuationCnossos.aBoundary`
  再帰分岐 + TBC golden）。v1 PR に parity harness と JMH 結果を添付。
- v0 PR 本文に「v1 で CNOSSOS コアに ~150 行の橋ガード分岐が必要になる」旨を明記済
  （[bridge_pr_body.md](bridge_pr_body.md) "What v1 will need from the core"）。
