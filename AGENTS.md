# AGENTS.md

## Purpose

このリポジトリは、Lawnchair fork をベースにした Android パーソナルランチャー `Morrowa` を開発するための作業場所である。
この文書は、将来このリポジトリを扱うエージェント向けの共通前提を定義する。

## Project Summary

- プロダクトは Android ホームランチャー
- 中核価値は ToDo、Habit、Alarm をホーム画面へ統合すること
- クラウド同期は行わず、完全ローカルで完結させる
- 目的は「アプリを開く場所」ではなく「今日を動かす場所」を作ること
- ベース戦略は完全自作ではなく Lawnchair fork

## Current Phase

現在は要件定義フェーズ。
まだベースコードの clone / fork は未配置で、ワークスペースには設計文書のみが存在する前提で扱うこと。

## Non-Negotiable Decisions

- 完全自作ランチャー路線には戻さない
- MVP ではクラウド、共有、複数ユーザー対応を入れない
- MVP では外部ウィジェットよりランチャー内部パネル UI を優先する
- 実装の主戦場はランチャー基盤の再発明ではなく、Task / Habit / Alarm の統合体験
- ベースランチャーの基本動作を壊す変更は慎重に扱う

## Product Principles

- 美しさと実用性を両立する
- 情報量より導線の気持ちよさを優先する
- 毎日見る画面として圧迫感を避ける
- ユーザーは単一人物であり、汎用 SaaS 的な設計を持ち込まない
- ホームでの即時理解と即時操作を最優先する

## Engineering Principles

- まず既存の Lawnchair 構造を理解してから変更する
- Morrowa 独自機能は、可能な限り独立したパッケージや層にまとめる
- upstream 由来コードへの広範囲な直接改変は避け、理由がある場合のみ行う
- 変更時は、何が Lawnchair 由来で何が Morrowa 独自かが追跡しやすい状態を保つ
- ローカルファーストの制約を前提にデータ設計する
- 過剰な抽象化や早すぎる汎用化を避ける

## Expected MVP Components

- Today Panel
- Habit Panel
- Next Alarm Panel
- Task local storage
- Habit local storage
- Alarm / reminder linkage
- Complete / snooze / skip action flow

## Suggested Domain Model

初期設計では、以下のエンティティを前提に検討する。

- Task
- Habit
- AlarmLink
- CompletionRecord

Task と Habit は UI では別でも、将来的な統合を見据えて近い構造を維持する。

## How To Work In This Repo

### When the Lawnchair codebase is not present yet

- 設計文書の更新を優先する
- 要件、MVP、画面構成、データ設計を具体化する
- 実装前提の断定は減らし、確認事項を明示する

### When the Lawnchair codebase is present

- まずビルドと起動確認を行う
- ホーム、ドロワー、設定、ウィジェット、通知導線の責務分割を把握する
- Morrowa 独自機能の差し込みポイントを明確にしてから編集する
- 先に大規模な見た目変更をせず、まず Task / Habit / Alarm の基本導線を通す

## Change Priority

実装優先順位は次の通り。

1. ホームとして成立すること
2. Task / Habit / Alarm のローカルデータが成立すること
3. ホーム上で即時操作できること
4. 操作結果がホームへ即時反映されること
5. デザインを磨くこと

## Avoid

- クラウド前提の提案
- マルチユーザー前提の提案
- MVP で不要な設定項目の追加
- ベースランチャー全体を書き換える雑なリファクタ
- 「まず全部作り直す」系の判断

## Definition of Done

変更は、以下を満たして初めて完了とみなす。

- このプロジェクトの要件と矛盾しない
- ランチャーとしての基本動作を不用意に壊していない
- Morrowa 独自価値に直接つながる
- ローカル完結方針を守っている
- 次の担当者が意図を追える程度に文書またはコード構造が整理されている
