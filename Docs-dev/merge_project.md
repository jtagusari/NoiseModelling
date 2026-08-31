# fix ブランチを upstream/main へ取り込む方策

> 更新日: 2026-08-31 / 対象: `fix` (= `origin/fix`) → `upstream/main`
> upstream = `https://github.com/Universite-Gustave-Eiffel/NoiseModelling.git`

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

---

## 2. 結論: ブランチ丸ごとのマージ / rebase は行わない

理由:

1. upstream が `fix` と**同じコアクラス（cnossos path, AttenuationOutput, ProfileBuilder, scripts モジュール）を別設計で作り直した**。差分の当て込みでは整合が取れず、事実上の再実装になる。
2. 642 コミットへの段階 rebase は、79 個の巨大かつ抽象度の異なるコミットを毎回衝突解決しながら通すことになり、工数・リスクとも過大。
3. `fix` の変更の多くは upstream に出す性質のものではない（開発メモ、生成物、IDE 設定、個人ビルド調整）。
4. upstream はレビュー時に**小さく焦点の絞れた PR**を求める。79k 行の一括 PR は受理されない。

→ **「機能スライスごとに、現在の `upstream/main` の上で作り直して個別 PR にする」**方針を採用する。

---

## 3. 採用方策: 機能スライス方式

### 3.1 全体フロー

```text
upstream/main (最新)
   └─ integ/upstream-sync         ← ベースライン（upstream/main を追従するだけ）
        ├─ feat/road-asj          ← スライス S1（PR #1）
        ├─ feat/bridge-modelling  ← スライス S2（PR #2）
        └─ ...
fix (現状のまま保存 = アーカイブ。破壊的操作はしない)
```

- `fix` は**触らずアーカイブ**。参照専用。
- スライスごとに `upstream/main` から新ブランチを切り、`fix` から必要な差分だけを移植 → クリーンなコミット → モジュール単位でテスト → PR。

### 3.2 スライス一覧（優先度 = 価値 × upstream 現行コードからの独立度）

| ID | スライス | 独立度 | upstream 送付 | 方針 |
|----|----------|--------|---------------|------|
| **S1** | **ASJ 道路音源モデル** (`noisemodelling-emission/.../road/asj/`) | 高（新規パッケージ・加算的、衝突は `RoadCnossos.java` 3行と `interpLinear`/`RailwayCnossosTest` の軽微なもの） | ◎ 最有力 | 現行 upstream に cherry-pick → テスト移植 → **最初の PR** |
| **S2** | Bridge モデリング (`BridgeAnalyzer`, `ElevationConverter`, `profilebuilder/Bridge*`, bridge テストケース) | 中（概念は独立だが upstream が作り直した `ProfileBuilder`/`CutProfile` に載せ替えが必要） | ○（要相談） | S1 後。upstream の新 `ProfileBuilder` 上で再実装。事前に issue で設計合意 |
| **S3** | per-row `HEIGHT_TYPE`（相対/絶対 Z を列で制御） | 中 | △ 要再設計 | upstream は #1016/#1021 で**グローバルパラメータ版を実装済み**。差分を「列単位指定の拡張」として提案し直すか、不要なら取り下げ |
| **S4** | JDBC 設定クラス分離 (`TableInputSettings` Builder, `ComputationSettings` 等) | 低（upstream の input/output リファクタと正面衝突） | △ | S1〜S3 の後に再評価。単独 PR ではなく設計 issue から。多くは取り下げ想定 |
| **S5** | pathfinder/propagation の Service 分解・`AcousticPath*`/`CnossosPathExt` | 極低（upstream の cnossospath-refactor と競合） | ✕ 差分としては不可 | upstream の新構造に対するゼロからの再設計提案。当面はローカル保持、または設計 issue のみ |
| — | `Docs-dev/**`, 生成 `test_cases/*.json`, `.vscode/`, `.mvn/`, `javadoc-warnings-unique.txt`, `nbactions.xml`, `preparation.sh`, `test_coverage_summary.md` | — | ✕ | upstream には出さない |

### 3.3 各スライスの作業手順（テンプレート）

```bash
# 0. ベースライン更新
git fetch upstream
git switch -c integ/upstream-sync upstream/main   # 初回のみ

# 1. スライス用ブランチ
git switch -c feat/<slice> integ/upstream-sync

# 2. fix から移植（いずれか）
#   a) 対象パッケージが独立なら cherry-pick:
git checkout fix -- noisemodelling-emission/src/main/java/org/noise_planet/noisemodelling/emission/road/asj
git checkout fix -- noisemodelling-emission/src/test/java/org/noise_planet/noisemodelling/emission/road/asj
git checkout fix -- noisemodelling-emission/src/test/resources/.../road/asj/RoadAsj_2023.json
#   b) 既存ファイルへの小変更は手で当てる（git diff fix -- <file> を参照）

# 3. ビルド & テスト（モジュール単位）
mvn -q -pl noisemodelling-emission -am test

# 4. 意味のある単位でコミット（メッセージを書く）
git add -A && git commit -m "feat(emission): add ASJ RTN-Model 2023 road source model"

# 5. origin へ push → upstream に PR
git push origin feat/<slice>
```

### 3.4 スライス S1（ASJ）の移植対象ファイル

追加（そのまま持ち込み可）:

- `noisemodelling-emission/src/main/java/.../emission/road/asj/RoadAsj.java`
- `noisemodelling-emission/src/main/java/.../emission/road/asj/RoadAsjParameters.java`
- `noisemodelling-emission/src/test/java/.../emission/road/asj/RoadAsj{,Formula,Parameters}Test.java`
- `noisemodelling-emission/src/test/resources/.../emission/road/asj/RoadAsj_2023.json`

要確認の既存ファイル差分（`git diff fix -- <file>` で中身を見て手当て）:

- `emission/road/cnossos/RoadCnossos.java`（3 行）
- `emission/utils/interpLinear.java`
- `emission/src/test/java/.../railway/RailwayCnossosTest.java`
- `emission/road/RoadCnossosFormulaTest.java`

> JDBC 側（`RoadEmissionBuilder` 等）での ASJ 呼び出し配線は S1 に含めず、emission モジュール単独で完結させる。DB 連携は別 PR。

---

## 4. 事前にやること

1. **upstream メンテナに issue で相談**（最優先）。
   upstream は cnossos path / attenuation / scripts を現在進行形で作り直している。S1〜S5 の一覧を提示し、
   受理見込み・設計方針・重複作業の有無をすり合わせる。特に S2（Bridge）と S3（HEIGHT_TYPE）。
2. `integ/upstream-sync` ブランチを作成し、以後 `git fetch upstream` で追従。
3. `fix` にタグを打って保全（例: `git tag archive/fix-2026-08-31 fix`）。

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
