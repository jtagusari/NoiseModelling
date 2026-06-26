# upstream/main マージ方策

## 現状把握

### 共通祖先

```text
46c9380a  Merge pull request #820 from Universite-Gustave-Eiffel/pierromond-patch-3
```

### upstream/main との差分規模

| 項目 | 数値 |
|------|------|
| upstream/main が fix より先行するコミット数 | **432** |
| upstream/main にあって fix にないファイル（コアソース） | **~900** |
| fix が upstream/main より先行するコミット数 | **~20** |

### upstream/main にある新規追加（fix にない）

- `installer/` — デスクトップアプリ向けインストーラ
- `docker-compose.yml` / `Dockerfile` — Docker サポート
- `wpsbuilder/` — WPS 構築ツール
- `noisemodelling-scripts/` — 新規モジュール
- `CONTRIBUTING.md`

---

## fix ブランチの変更内容（独自変更）

### A. HEIGHT_TYPE per-row メカニズム

**変更ファイル:**

- `noisemodelling-pathfinder/path/Scene.java` — `HeightType` enum 追加 (`RELATIVE` / `ABSOLUTE`)
- `noisemodelling-pathfinder/SourceCollector.java` — per-row HEIGHT_TYPE に基づいた絶対 Z 変換
- `noisemodelling-propagation/SceneWithAttenuation.java` — ソースの HEIGHT_TYPE 列読み込み
- `noisemodelling-jdbc/input/DefaultTableLoader.java` — 受信点の HEIGHT_TYPE 列読み込み
- `wps_scripts/.../Noise_level_from_traffic.groovy` — HEIGHT_TYPE の入力説明追記
- `wps_scripts/.../Noise_level_from_source.groovy` — HEIGHT_TYPE の入力説明追記

**要点:** Z 値の絶対/相対解釈をテーブルの列（per-row）で制御できるようにした。

### B. makeSourceRelativeZToAbsolute() の削除

**変更ファイル:**

- `noisemodelling-jdbc/NoiseMapByReceiverMaker.java` — `makeSourceRelativeZToAbsolute()` 呼び出しを削除

**要点:** ソースの Z 変換は `SourceCollector` がサンプリング時に行うため、`NoiseMapByReceiverMaker` での事前変換は二重変換になるバグがあった。削除することで修正。

### C. TableInputSettings の整理

**変更ファイル:**

- `noisemodelling-jdbc/TableInputSettings.java` — Builder パターン化、全テーブル設定を一元化
- `noisemodelling-jdbc/GridMapMaker.java` — `TableInputSettings` を保持するように変更

### D. 設定クラスの追加・整理

- `noisemodelling-jdbc/ComputationSettings.java` — 計算設定の独立クラス
- `noisemodelling-jdbc/ReceiverGenerationSettings.java` — 受信点生成設定の独立クラス
- `noisemodelling-jdbc/input/EmissionInputSettings.java` — 排出設定の独立クラス
- `noisemodelling-jdbc/input/EmissionInputSettingsView.java`
- `noisemodelling-jdbc/input/CellProfileLoader.java` — 新規追加

### E. ブリッジ処理の改修

**変更ファイル:**

- `noisemodelling-pathfinder/BridgeAnalyzer.java` — 新規追加
- `noisemodelling-pathfinder/ElevationConverter.java` — 新規追加（DEM との標高変換）
- `noisemodelling-pathfinder/profilebuilder/Bridge*` 各クラス — enter/exit ロジックの実装

---

## コンフリクトが予想される高リスクファイル

両ブランチで独立して変更されているファイル。マージ時に必ず手動解決が必要。

| ファイル | リスク | 理由 |
|----------|--------|------|
| `noisemodelling-jdbc/NoiseMapByReceiverMaker.java` | ★★★ | 核心クラス。fix は `makeSourceRelativeZToAbsolute` 削除、upstream も大幅変更 |
| `noisemodelling-jdbc/GridMapMaker.java` | ★★★ | `TableInputSettings` の持ち方を両側で変更 |
| `noisemodelling-jdbc/input/DefaultTableLoader.java` | ★★★ | HEIGHT_TYPE 読み込み追加 vs upstream の独自変更 |
| `noisemodelling-jdbc/input/SceneWithEmission.java` | ★★★ | 排出設定の構造変更 |
| `noisemodelling-propagation/SceneWithAttenuation.java` | ★★★ | HEIGHT_TYPE 追加 vs upstream の変更 |
| `noisemodelling-pathfinder/path/Scene.java` | ★★★ | `HeightType` enum 追加 vs upstream の大幅変更 |
| `noisemodelling-pathfinder/PathFinder.java` | ★★ | `makeSourceRelativeZToAbsolute` の扱い |
| `wps_scripts/.../Noise_level_from_traffic.groovy` | ★★ | HEIGHT_TYPE 説明追記 vs upstream の変更 |
| `wps_scripts/.../Noise_level_from_source.groovy` | ★★ | 同上 |

---

## 採用する方策: 段階的 rebase（`edit` モードで逐次確認）

upstream は共通祖先から **432 コミット**先行。一括 rebase ではコンフリクト解消が混沌とするため、
50 コミット単位の中間コミットを踏み台にして段階的に rebase する。
各コミット適用後に `edit` モードで停止し、動作への影響を確認してから次へ進む。

---

## 準備

### upstream を最新に更新

```bash
git fetch upstream
```

### fix ブランチの変更内容を事前確認

```bash
# fix が upstream に対して持っているコミット一覧
git log --oneline upstream/main..HEAD

# 特定ファイルの変更履歴
git log -p upstream/main..HEAD -- <ファイルパス>
```

---

## 中間コミット一覧

共通祖先（`46c9380a`）から `upstream/main` までの 432 コミットを 50 件ずつ区切った踏み台：

| ステップ | ハッシュ | 累積コミット数 | コミットメッセージ |
| --- | --- | --- | --- |
| Step 1 | `3d62a2e7` | 50 | forget removing block of code in curved condition |
| Step 2 | `398fb7b3` | 100 | Remove unwanted patch on Building_Grid.groovy |
| Step 3 | `3ffd21db` | 150 | Better detect the non metric projection system |
| Step 4 | `11f80ea5` | 200 | copy tests from plamade |
| Step 5 | `d1bffbce` | 250 | finalize implementation of defaultValue for WPS |
| Step 6 | `52cd0c0a` | 300 | Fix log fetching to use system line separator |
| Step 7 | `92359ed1` | 350 | docs: fix Title underline too short |
| Step 8 | `beb4b7a7` | 400 | Merge pull request #938 |
| Step 9 | `upstream/main` | 432 | 最終（HEAD） |

---

## 各ステップの作業手順

GitLens（VS Code 拡張）を使う方法と、コマンドラインの両方を記載する。

---

### 1. interactive rebase を開始

**GitLens を使う場合（推奨）:**

1. コマンドパレット（`Ctrl+Shift+P`）を開く
2. `GitLens: Rebase Current Branch onto...` を実行
3. 踏み台コミットのハッシュを入力（例：`3d62a2e7`）
4. Interactive Rebase Editor が開く
5. 全コミットのドロップダウンを `pick` → `edit` に変更（一括変更可）
6. `Start Rebase` ボタンで開始

**コマンドラインの場合:**

```bash
# 全コミットの pick を edit に書き換えて rebase 開始（例: Step 1）
GIT_SEQUENCE_EDITOR='sed -i s/^pick/edit/' git rebase -i 3d62a2e7
```

rebase が各コミット適用後に自動停止する。

---

### 2. 各コミットで確認

**GitLens を使う場合:**

| 確認内容 | 操作 |
| --- | --- |
| 適用されたコミットの変更内容 | GitLens コミット履歴パネルで最新コミットをクリック |
| ファイルの変更前後を比較 | ファイルを右クリック → `Open Changes` |
| 行ごとの変更由来 | ファイルを開くと左端に blame が表示 |
| コンフリクト発生時の差分 | Source Control パネルでコンフリクトファイルを選択 |
| ブランチ全体の位置関係 | `GitLens: Show Commit Graph`（`Ctrl+Shift+G` → G） |

**コマンドラインの場合:**

```bash
# 適用されたコミットの変更内容
git show HEAD

# 前コミットとの差分（動作影響確認）
git diff HEAD~1

# コンフリクト発生時：upstream 側 vs fix 側の差分
git diff HEAD REBASE_HEAD -- <ファイルパス>
```

---

### 3. 問題なければ次のコミットへ

**GitLens を使う場合:** コマンドパレット → `Git: Continue Rebase`

**コマンドラインの場合:**

```bash
git rebase --continue
```

修正が必要な場合は編集 → `git add`（または Source Control でステージ）→ continue。

---

### 4. ステップ完了後に次の踏み台へ

1 ステップ（50 コミット分）が終わったら同じ手順で次の踏み台に rebase する。

コマンドラインでまとめて実行する場合：

```bash
GIT_SEQUENCE_EDITOR='sed -i s/^pick/edit/' git rebase -i 398fb7b3   # Step 2
GIT_SEQUENCE_EDITOR='sed -i s/^pick/edit/' git rebase -i 3ffd21db   # Step 3
# ... 繰り返し ...
GIT_SEQUENCE_EDITOR='sed -i s/^pick/edit/' git rebase -i upstream/main  # 最終
```

---

### 5. 途中で止めたい場合

**GitLens を使う場合:** コマンドパレット → `Git: Abort Rebase`

**コマンドラインの場合:**

```bash
git rebase --abort   # そのステップをキャンセル（前の踏み台に戻る）
```

---

## rebase 完了後

```bash
# テスト
mvn test -pl noisemodelling-pathfinder
mvn test -pl noisemodelling-jdbc
mvn test -pl wps_scripts

# origin へ force push（履歴が書き換わるため必須）
git push origin fix --force-with-lease
```

---

## 高リスクファイルの対処方針

コンフリクトまたは動作影響が予想されるファイルで確認すべき点：

| ファイル | 確認ポイント |
| --- | --- |
| `NoiseMapByReceiverMaker.java` | `makeSourceRelativeZToAbsolute()` の呼び出しが **ない**こと |
| `DefaultTableLoader.java` | `fetchCellReceiver()` が `HEIGHT_TYPE` 列を per-row で読んでいること |
| `SceneWithAttenuation.java` | `doAddSourceDb()` が `HEIGHT_TYPE` 列を per-row で読んでいること |
| `SourceCollector.java` | RELATIVE ソースの Z 変換が `calculateAbsoluteElevation()` を呼んでいること |
| `path/Scene.java` | `HeightType` enum（`RELATIVE` / `ABSOLUTE`）が存在すること |

---

## 移植時の注意点

### makeSourceRelativeZToAbsolute() の扱い

upstream の `NoiseMapByReceiverMaker` にこの呼び出しが残っている場合、fix の知見（二重変換バグ）を反映して削除する。ただし upstream 側で既に修正されている可能性もあるため、先に upstream の該当箇所を確認すること。

```bash
git show upstream/main:noisemodelling-jdbc/src/main/java/org/noise_planet/noisemodelling/jdbc/NoiseMapByReceiverMaker.java \
  | grep -n "makeSourceRelativeZToAbsolute"
```

### isReceiverHasAbsoluteZCoordinates / isSourceHasAbsoluteZCoordinates の扱い

fix ブランチではこれらのフラグは dead code になっている（HEIGHT_TYPE per-row が代替）。upstream 側でこれらを積極的に使用している場合は互換性に注意して対処する。

### TableInputSettings の互換性

upstream では `TableInputSettings` の構造が異なる可能性がある。Builder パターンの移植前に upstream 版の設計を確認する。

```bash
git show upstream/main:noisemodelling-jdbc/src/main/java/org/noise_planet/noisemodelling/jdbc/TableInputSettings.java | head -60
```

---

## 参考: 差分確認コマンド集

```bash
# upstream にのみ存在するファイル（upstream の新規追加）
git diff --name-only --diff-filter=A upstream/main HEAD

# fix にのみ存在するファイル（fix の独自追加）
git diff --name-only --diff-filter=A HEAD upstream/main

# 両方で変更されているファイル（コンフリクト候補）
git diff --name-only upstream/main...HEAD | \
  while read f; do git diff --name-only HEAD..upstream/main | grep -qx "$f" && echo "$f"; done

# upstream 版の特定ファイルを確認
git show upstream/main:<ファイルパス>

# fix と upstream の差分（ファイル単位）
git diff upstream/main HEAD -- <ファイルパス>
```
