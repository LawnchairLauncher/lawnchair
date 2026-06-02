# Morrowa 仕様書 v0.1

作成日: 2026-06-02  
位置付け: ここまでの会話で決定した内容を統合した、Morrowa の初期仕様書。旧 MossGrid の思想を継承しつつ、Lawnchair fork ベースの Android ホームランチャーとして再設計する。

> 注記: この仕様書は、現時点で参照可能な会話内容を正本としてまとめたもの。旧 `MossGrid_要件定義書.md` は検索では見つからなかったため、直接引用ではなく、会話で共有された要点を継承資産として扱う。

---

## 1. プロジェクト概要

Morrowa は、旧アプリ MossGrid の思想を継承しながら、Android のホームランチャーとして再構築するアプリである。ToDo、習慣、アラーム、外部ウィジェット配置を統合し、スマートフォンのホーム画面そのものを生活管理の中枢にする。

### 1.1 基本方針

| 項目 | 内容 |
|---|---|
| アプリ名 | Morrowa |
| 母体思想 | 旧 MossGrid |
| 実装方針 | Lawnchair を fork して構築 |
| 保存方式 | 完全ローカル保存 |
| クラウド同期 | MVP では採用しない |
| 将来公開 | Play ストア公開を視野 |
| 最初の最適化対象 | Galaxy S22 Ultra |
| 端末環境 | Android 16 / One UI 8.0 |
| 重視点 | 軽さ、変更容易性、美しさ、行動導線 |

### 1.2 コンセプト

- ToDo、習慣、アラームを統合した、自分専用の美しいホームランチャーを作る。
- UI は壁紙に溶け込ませるより、独立した美しい道具として立たせる。
- 生活を支配するのではなく、生活の手綱を静かに手元へ戻す。
- 入力負荷、画面の重さ、後戻りしづらい実装を避ける。
- まず自分の端末に最適化し、将来的には誰でも使える形へ広げる。

---

## 2. 旧 MossGrid からの継承、変更、破棄

### 2.1 継承するもの

| 項目 | 新 Morrowa での扱い |
|---|---|
| ToDo / Habit の基本思想 | 継承する |
| Room 前提のローカルDB設計 | 継承する |
| `sort_order` | 手動並び替えの基盤として継承する |
| ゴミ箱思想 | 継承する |
| `is_deleted` / `deleted_at` | 継承する |
| 30日後完全削除 | 継承する |
| 習慣アーカイブ | 履歴を残すため継承する |
| 4:00 境界 | 習慣の habit day 判定として継承する |
| GitHub 草風カレンダー | 習慣履歴表示として継承する |
| `habit_rule_history` | 正式採用する |

### 2.2 変更するもの

| 旧 MossGrid | Morrowa |
|---|---|
| 単体 ToDo / 習慣アプリ | ホームランチャーへ統合 |
| ToDo に日付概念なし | ToDo は任意の予定日を持てる |
| ToDo と習慣の混在画面 | 習慣画面と ToDo 画面を分離 |
| 通知不要 | ToDo / 習慣アラーム、未達習慣通知を導入 |
| inline 追加案 | 画面別 FAB を基本にする |
| 内蔵カレンダー案 | 外部ウィジェットに任せる |
| 透明タッチ領域案 | 透明アイコン機能に変更 |
| ホーム復帰は常に Home 案 | 最後にいたページへ戻る |

### 2.3 捨てるもの

| 項目 | 捨てる理由 |
|---|---|
| クラウド同期 | MVP の軽さと自分用最適化を優先 |
| タグ | 入力と設計が重くなる |
| サブタスク | MVP には不要 |
| スヌーズ | 操作思想と合わない |
| ToDo 完了状態 | 不要になったら削除する運用にする |
| 内蔵カレンダー | 外部カレンダーウィジェットに任せる |
| Google カレンダー連携 | MVP では不要 |
| 透明巨大タッチ領域 | 透明アイコン機能へ置換 |
| ページインジケータ / タブ | 横スワイプのみの思想にする |

---

## 3. ランチャー全体構成

### 3.1 ページ構成

Morrowa は 4つの論理ページを持つ。横スワイプで循環し、左右どちらにも無限に移動できる。

| 順番 | ページ名 | 役割 |
|---:|---|---|
| 1 | Home | 通常ランチャーに近いホーム画面 |
| 2 | Habit | 習慣管理画面 |
| 3 | ToDo | ToDo 管理画面 |
| 4 | Widget Blank | 外部カレンダーウィジェットなどを置く空白ページ |

循環順:

```text
Home → Habit → ToDo → Widget Blank → Home
```

逆方向:

```text
Home → Widget Blank → ToDo → Habit → Home
```

### 3.2 ページ移動仕様

| 項目 | 仕様 |
|---|---|
| 移動方法 | 横スワイプのみ |
| ループ | 有効 |
| 左右端 | 存在しない |
| ページインジケータ | 表示しない |
| タブ | 表示しない |
| 画面端タップ移動 | 採用しない |
| ホーム復帰 | 最後にいたページへ戻る |
| ロック解除後 | 最後にいたページへ戻る |
| アプリから戻る | 最後にいたページへ戻る |

### 3.3 最後にいたページの保存

`last_page_type` を保存し、ホーム表示時に復元する。頻繁なDB書き込みを避けるため、MVP では DataStore または SharedPreferences 相当の軽量保存を推奨する。

候補:

```text
launcher_state
- last_page_type
- updated_at
```

`last_page_type`:

```text
HOME
HABIT
TODO
WIDGET_BLANK
```

---

## 4. Home 画面仕様

### 4.1 基本方針

Home は通常ランチャーに近い配置画面とする。アプリアイコン、フォルダアイコンを置ける。旧案の透明領域は廃止し、代わりにアイコン単位で透明化できる機能を提供する。

| 項目 | 仕様 |
|---|---|
| アプリアイコン配置 | 可能 |
| フォルダ配置 | 可能 |
| 検索バー | 置かない |
| 透明巨大領域 | 採用しない |
| アイコン透明化 | 採用する |
| ホーム削除 | 長押しメニューから実行 |

### 4.2 アイコン透明化

アプリアイコンまたはフォルダアイコンを透明化できる。透明化されたアイコンは見えなくなるが、タップ領域は残る。

| 対象 | 透明化 |
|---|---|
| アプリアイコン | 可能 |
| フォルダアイコン | 可能 |
| ウィジェット | 対象外 |
| Habit / ToDo の項目 | 対象外 |

透明化時の扱い:

| 要素 | 仕様 |
|---|---|
| アイコン画像 | 非表示 |
| ラベル | 非表示 |
| タップ領域 | 残す |
| 長押し操作 | 残す |
| アクセシビリティ名 | 残す |
| 配置情報 | 維持する |

### 4.3 長押しメニュー

アプリまたはフォルダを長押しすると、上部に小さなメニューを表示する。

通常アイコンの場合:

```text
透明化
ホームから削除
```

透明化済みアイコンの場合:

```text
透明解除
ホームから削除
```

| メニュー項目 | 動作 |
|---|---|
| 透明化 | アイコン画像とラベルを非表示にする |
| 透明解除 | アイコン画像とラベルを再表示する |
| ホームから削除 | ホーム配置から削除する。アプリ自体はアンインストールしない |

### 4.4 ドック仕様

最下部にアプリ領域を持つドックを置く。

| 項目 | 仕様 |
|---|---|
| 位置 | 最下部 |
| 枠数 | 3つを理想とする |
| 標準ドックが3枠の場合 | 残す |
| 標準ドックが5枠の場合 | 残す |
| 標準ドックがそれ以外の場合 | 3枠化を検討 |
| 検索バー一体型の場合 | 検索バーは削除または非表示を検討 |

### 4.5 Home 画面の破棄済み仕様

以下は破棄済みである。

- 右側透明領域
- 左側透明領域
- 上側透明領域
- 透明領域タップでフォルダ展開
- 単独アイコンを置かない方針
- フォルダ専用 Home 方針

---

## 5. Habit 画面仕様

### 5.1 基本構成

Habit 画面は習慣管理専用画面である。旧 MossGrid の思想を最も強く継承する画面として扱う。

上から順に配置する。

1. 日付 / habit day 表示
2. GitHub 草風カレンダー
3. 今日対象の習慣一覧
4. 習慣追加 FAB

### 5.2 習慣仕様

| 項目 | 仕様 |
|---|---|
| 習慣タイプ | チェック式のみ |
| 日付境界 | 4:00 |
| チェック | 1日1回 |
| 並び順 | 完全手動 |
| 一覧表示 | 手動順 |
| アーカイブ | 履歴を残して通常一覧から消す |
| 削除 | ゴミ箱へ移動し、30日後に完全削除 |
| 草カレンダー | その日の達成習慣数で濃淡表示 |

### 5.3 頻度ルール

| ルール | 内容 |
|---|---|
| 毎日 | 毎 habit day 対象 |
| 毎週 | 曜日指定 |
| 毎月 | 毎月指定日 |

### 5.4 habit day 判定

習慣だけ 4:00 境界を使う。

| 実時刻 | habit_day |
|---|---|
| 2026-06-02 03:59 | 2026-06-01 |
| 2026-06-02 04:00 | 2026-06-02 |
| 2026-06-02 23:59 | 2026-06-02 |

### 5.5 `habit_rule_history` 正式採用

`habit_rule_history` は正式採用する。習慣ルールが途中で変わっても、過去の履歴を現在のルールで再解釈しないためである。

例:

- 1月は毎日
- 2月から月水金
- 3月から毎月1日

この場合でも、草カレンダーや履歴判定は当時のルールに基づいて扱う。

---

## 6. ToDo 画面仕様

### 6.1 基本構成

ToDo 画面はタスク管理専用画面である。習慣とは分離し、ToDo だけに集中する。

上から順に配置する。

1. 今日の日付
2. ToDo 一覧
3. ToDo 追加 FAB
4. 必要に応じてゴミ箱入口またはメニュー

### 6.2 ToDo 基本仕様

| 項目 | 仕様 |
|---|---|
| 予定日 | 任意 |
| 日付境界 | 0:00 |
| 完了状態 | 持たない |
| 表示条件 | 削除されていない ToDo |
| 並び順 | 完全手動 |
| 2ページ相当の一覧条件 | 予定日で絞らず、手動並び上位を表示 |
| サブタスク | なし |
| タグ | なし |
| アラーム | 1件につき最大1つ |
| 削除 | 不要になったら削除する |
| ゴミ箱 | 30日保持して完全削除 |

### 6.3 予定日の扱い

予定日は任意である。ToDo 一覧の表示条件を支配しない。

| ToDo 種別 | 扱い |
|---|---|
| 予定日なし | 通常タスク |
| 予定日あり | 日付チップを表示 |
| 今日の ToDo | 視覚的に少し強調する候補 |
| 未来の ToDo | 手動順に従って表示 |
| 過去日の ToDo | 期限切れ扱いにしない |

重要な原則:

```text
予定日は判断材料であり、並び順の支配者ではない。
```

### 6.4 削除操作

ToDo は完了状態を持たない。不要になったら削除する。削除はゴミ箱へ移動し、30日後に完全削除する。

候補としては以下を推奨する。

| 操作 | 仕様 |
|---|---|
| スワイプ | ゴミ箱へ移動 |
| Undo | 直後だけ復元 |
| 長押し | 編集、詳細、削除などの補助メニュー |
| ゴミ箱 | 30日後に完全削除 |

---

## 7. Widget Blank 画面仕様

### 7.1 基本方針

Widget Blank は、内蔵カレンダー画面の代わりに存在する空白ページである。主用途は外部カレンダーウィジェット置き場だが、通常ランチャーのように自由に配置できる。

| 項目 | 仕様 |
|---|---|
| 初期状態 | 何も置かない |
| 主用途 | 外部カレンダーウィジェット配置 |
| 外部ウィジェット | 配置可能 |
| アプリアイコン | 配置可能 |
| フォルダ | 配置可能 |
| ショートカット | 配置候補 |
| 内蔵カレンダー | 作らない |
| Google カレンダー連携 | MVP では作らない |

### 7.2 カレンダー方針

Morrowa は内蔵カレンダーを持たない。カレンダーを置くべき場所には、Widget Blank を用意し、外部ウィジェットに任せる。

| 項目 | 扱い |
|---|---|
| 独自カレンダーUI | 不採用 |
| ローカル予定表DB | 不採用 |
| Google カレンダー同期 | 不採用 |
| 外部カレンダーウィジェット | 採用 |
| ToDo の予定日 | 維持 |

---

## 8. アラーム / 通知仕様

### 8.1 基本方針

Morrowa は ToDo と習慣に紐づくアラームを持つ。さらに、今日できていない習慣数を知らせる未達習慣通知を持つ。

| 種別 | 仕様 |
|---|---|
| ToDo アラーム | ToDo 1件につき最大1つ。設定は任意 |
| 習慣アラーム | 習慣 1件につき最大1つ。設定は任意 |
| 習慣アラームの日付 | 習慣ルールを参照して計算 |
| 未達習慣通知 | 今日できていない習慣数を通知 |
| スヌーズ | なし |
| アラーム操作 | 停止のみ |
| 停止後の状態 | ToDo / 習慣は未完了のまま |
| 再起動後 | DB から再設定する |

### 8.2 未達習慣通知

未達習慣通知は、個別に完全無効化するアプリ内設定は想定しない。不要なら端末側で通知を切る思想とする。ただし通知時刻は設定画面で変更可能にする。

デフォルトは1日3回。

| 回 | 初期値 |
|---:|---|
| 1 | 午後 |
| 2 | 夜 |
| 3 | 深夜 |

暫定値:

```text
13:00
20:00
01:00
```

通知タップ時の遷移:

| 通知 | タップ時 |
|---|---|
| 未達習慣通知 | ランチャーを開く。最後にいたページ復元との関係は要調整 |

### 8.3 Android 権限上の注意

アラームと通知は Android 側の権限制約を受ける。MVP 設計時に以下を確認する。

| 項目 | 注意点 |
|---|---|
| 正確なアラーム | `SCHEDULE_EXACT_ALARM` または `USE_EXACT_ALARM` の検討が必要 |
| Android 14 以降 | `SCHEDULE_EXACT_ALARM` は多くの新規インストールで既定不許可になりうる |
| 通知 | Android 13 以降は `POST_NOTIFICATIONS` 実行時権限が必要 |
| 全画面に近い表示 | full-screen intent と `USE_FULL_SCREEN_INTENT` の制約確認が必要 |
| Play ストア公開 | アラーム、全画面通知の用途説明を明確にする必要がある |

参考:

- Android Developers, Schedule alarms: https://developer.android.com/develop/background-work/services/alarms
- Android 14 exact alarm changes: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Notification runtime permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android 10 fullscreen intent behavior changes: https://developer.android.com/about/versions/10/behavior-changes-10
- Android 14 fullscreen intent summary: https://developer.android.com/about/versions/14/summary

---

## 9. データ設計

### 9.1 基本方針

Morrowa は完全ローカル保存とする。Room を前提に、ToDo、Habit、アラーム、ランチャー状態を管理する。

将来同期用の重いカラムは持たない。ただし、将来の移行やバックアップを妨げない最低限の時刻カラムは持つ。

持つ:

- `id`
- `created_at`
- `updated_at`
- `deleted_at`
- JSON 側の `schema_version`

持たない:

- `server_id`
- `sync_state`
- `dirty_flag`
- `remote_updated_at`

### 9.2 `todos`

```text
todos
- id
- title
- memo nullable
- scheduled_date nullable
- sort_order
- is_deleted
- deleted_at nullable
- created_at
- updated_at
```

### 9.3 `habits`

```text
habits
- id
- name
- memo nullable
- sort_order
- is_archived
- is_deleted
- deleted_at nullable
- created_at
- updated_at
```

### 9.4 `habit_rule_history`

```text
habit_rule_history
- id
- habit_id
- rule_type
- weekdays_mask nullable
- monthdays_json nullable
- effective_from_habit_day
- created_at
```

`rule_type`:

```text
DAILY
WEEKLY
MONTHLY
```

### 9.5 `habit_completions`

```text
habit_completions
- habit_id
- habit_day
- done
- checked_at
```

候補主キー:

```text
PRIMARY KEY (habit_id, habit_day)
```

### 9.6 `alarm_rules`

```text
alarm_rules
- id
- owner_type
- owner_id
- enabled
- time_of_day
- sound_type nullable
- created_at
- updated_at
```

`owner_type`:

```text
TODO
HABIT
```

### 9.7 `alarm_schedules`

```text
alarm_schedules
- id
- alarm_rule_id
- next_fire_at
- scheduled_system_id nullable
- status
- created_at
- updated_at
```

`status` 候補:

```text
SCHEDULED
FIRED
CANCELED
FAILED
```

### 9.8 `habit_reminder_settings`

```text
habit_reminder_settings
- id
- times_json
- created_at
- updated_at
```

アプリ内で完全無効化を持たない思想なら `enabled` は持たない。

### 9.9 ランチャー配置

Lawnchair 既存DBを優先して使う。必要なら拡張カラムとして `is_transparent` を追加する。

概念モデル:

```text
launcher_items
- id
- page_id
- item_type
- package_name nullable
- class_name nullable
- folder_id nullable
- widget_id nullable
- cell_x
- cell_y
- span_x
- span_y
- is_transparent
- created_at
- updated_at
```

`item_type`:

```text
APP
SHORTCUT
WIDGET
FOLDER
```

### 9.10 ランチャーページ

4ページ固定なら定数管理でよい。将来ページ追加を考えるなら `launcher_pages` を持つ。

```text
launcher_pages
- id
- page_type
- page_order
- is_enabled
- created_at
- updated_at
```

`page_type`:

```text
HOME
HABIT
TODO
WIDGET_BLANK
```

---

## 10. JSON エクスポート / インポート仕様

### 10.1 基本方針

MVP から手動エクスポートとインポートを持つ。形式は JSON。主目的は機種変更である。

| 項目 | 仕様 |
|---|---|
| エクスポート | 手動 |
| インポート | MVP から対応 |
| 形式 | JSON |
| MVP のインポート方式 | 全置換のみ |
| マージ | 将来対応 |
| 目的 | 機種変更、ローカルバックアップ |

### 10.2 全置換インポート

MVP では、インポート時に既存データを全て置き換える。

対象:

- ToDo
- Habit
- habit rule history
- habit completions
- alarm rules
- alarm schedules のうち復元可能なもの
- habit reminder settings
- launcher state
- 必要に応じて launcher items

注意:

- インポート前に確認ダイアログを出す。
- 既存データは失われる。
- Android の system alarm id は端末依存のため、インポート後に再スケジュールする。

### 10.3 JSON メタ情報

```json
{
  "app": "Morrowa",
  "schema_version": 1,
  "exported_at": "2026-06-02T00:00:00+09:00",
  "device_note": "optional",
  "data": {}
}
```

---

## 11. MVP 範囲定義

### 11.1 MVP に入れる

| 領域 | 内容 |
|---|---|
| Lawnchair fork ベース | 採用 |
| 4論理ページ | Home / Habit / ToDo / Widget Blank |
| 無限ループ横スワイプ | 採用 |
| 最後にいたページへ戻る | 採用 |
| Home | 通常ランチャー + 透明アイコン |
| ドック | 最下部3枠理想、標準3または5なら維持 |
| Widget Blank | 外部ウィジェット、アプリ、フォルダ配置 |
| Habit | 追加、編集、手動並び、アーカイブ、削除 |
| Habit ルール | 毎日、毎週、毎月 |
| habit_rule_history | 正式採用 |
| Habit completions | 1日1回チェック |
| 草カレンダー | 達成習慣数の濃淡表示 |
| ToDo | 追加、編集、手動並び、削除 |
| ToDo 予定日 | 任意 |
| ToDo 完了状態 | 持たない |
| ToDo / Habit アラーム | 採用 |
| 未達習慣通知 | 採用 |
| JSON エクスポート | 採用 |
| JSON インポート | 全置換のみ採用 |
| ゴミ箱 | 30日後完全削除 |

### 11.2 MVP から外す

| 領域 | 理由 |
|---|---|
| クラウド同期 | 重い |
| Google カレンダー連携 | 外部ウィジェットに任せる |
| 内蔵カレンダー | 不採用 |
| カレンダーイベントDB | 不要 |
| タグ | 不要 |
| サブタスク | 不要 |
| スヌーズ | 不要 |
| JSON マージインポート | 将来対応 |
| ダイナミックカラー | 将来対応 |
| 高度な統計画面 | MVP では草カレンダーで十分 |
| ページインジケータ | 不要 |
| タブ UI | 不要 |

---

## 12. 未確定事項

### 12.1 透明アイコン周り

| 未確定 | 選択肢 |
|---|---|
| 透明化済みアイコンを編集モード中だけ薄く見せるか | 見せる / 見せない / 長押し時のみ見せる |
| ドック上アイコンも透明化できるか | できる / できない / 将来対応 |
| ホームから削除時に Undo を出すか | 出す / 出さない |

### 12.2 Home / Widget Blank の配置DB

| 未確定 | 内容 |
|---|---|
| Lawnchair 既存DBを拡張するか | fork 元のDB構造確認後に決める |
| 独自DBに `launcher_items` を持つか | 重複管理を避けたいので慎重に判断 |

### 12.3 アラーム仕様

| 未確定 | 内容 |
|---|---|
| exact alarm 権限方針 | `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` / 代替案の選定 |
| 全画面表示 | どこまで強い表示にするか |
| アラーム音 | システム音 / 独自音 / 端末設定依存 |
| 端末再起動後 | 自動再設定の実装詳細 |

### 12.4 習慣通知

| 未確定 | 内容 |
|---|---|
| 未達習慣通知タップ時 | Habit画面に行くか、最後のページ復元を優先するか |
| デフォルト時刻 | 13:00 / 20:00 / 01:00 で確定するか |

### 12.5 ToDo 削除操作

| 未確定 | 内容 |
|---|---|
| 削除主導線 | スワイプ + Undo を正式採用するか |
| 長押しメニュー | 編集、削除、アラーム設定などをどう並べるか |

---

## 13. 次に作るべき設計書

この仕様書を親文書として、次に以下を分割して作る。

1. 全体設計書
2. 画面仕様書
3. データ設計書
4. 通知 / アラーム仕様書
5. ランチャー独自仕様書
6. MVP 範囲定義書
7. JSON バックアップ仕様書

---

## 14. 設計メモ

Morrowa は、単にタスクを表示するアプリではない。ホーム画面という毎日何十回も触れる場所に、行動の入口と生活の軌道を置くアプリである。

そのため、機能追加よりも、操作の軽さと視界の静けさを優先する。予定日は ToDo を縛る鎖ではなく、判断の小さな札として扱う。習慣は 4:00 境界で生活実感に寄せ、履歴は `habit_rule_history` で過去の地層として守る。

Widget Blank は空白ではなく、外部ウィジェットを迎えるための余白である。Home は完全なミニマリズムではなく、透明化できる通常ランチャーとして扱う。見えないアイコンは消えたのではなく、そこに置かれた静かな扉である。
