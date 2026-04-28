# 麻雀役判定プロトタイプ

`React + Vite` のフロントエンドから、`Spring Boot` の判定APIを呼び出すプロトタイプです。

## 構成

- `frontend`: 牌選択UI（GIF）、ドラ表示牌選択（ポップアップ）、判定ボタン、役/翻/符/点の表示
- `backend`: `/api/judge` エンドポイント、和了形分解、役判定、ドラ判定、符/点計算

## 起動方法

### 1) バックエンド

```bash
cd backend
mvn spring-boot:run
```

`http://localhost:8080` で起動します。

### 2) フロントエンド

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173` で起動します。

## 画像（牌GIF）

フロントは `public` を静的配信するので、牌画像はここに置きます。

- 配置先: `frontend/public/tiles/`
- 通常牌: `1m.gif ... 9m.gif`, `1p.gif ... 9p.gif`, `1s.gif ... 9s.gif`, `1z.gif ... 7z.gif`
- 赤牌: `5mred.gif`, `5pred.gif`, `5sred.gif`

## 画面の使い方

- **手牌選択**: 牌一覧からクリックして14枚選択
- **手牌の理牌**: 「理牌」ボタンで並び替え（萬→筒→索→字）
- **手牌の削除**: 選択中の牌をクリック
- **手牌マーク（牌の下のボタン）**: クリックで以下をループ
  - 空白 → `鳴` → `ツ`（ツモ牌）→ `ロ`（ロン牌）→ 空白
  - `ツ`/`ロ` は同時に1枚だけ（後から選んだ方が優先）
  - `鳴` が1つでもあると、設定の `立直/一発/天和/地和` は自動でOFF＆操作不可
- **ドラ表示牌 / 裏ドラ表示牌**: 右の「選択」ボタンからポップアップで選択（複数可、クリックで削除）

## API

- `POST /api/judge`
- リクエスト例:

```json
{
  "tiles": ["1m","2m","3m","4m","5m","6m","1p","2p","3p","7s","8s","9s","2z","2z"],
  "marks": ["","","","","","","","","","RON","","","",""],
  "context": {
    "riichi": false,
    "ippatsu": false,
    "rinshan": false,
    "chankan": false,
    "haitei": false,
    "houtei": false,
    "tenhou": false,
    "chiihou": false,
    "seatWind": "S",
    "roundWind": "E",
    "doraIndicators": [],
    "uraDoraIndicators": []
  }
}
```

- レスポンス例:

```json
{
  "yakuList": [
    { "name": "平和", "han": 1 }
  ],
  "totalHan": 1,
  "score": {
    "han": 1,
    "fu": 30,
    "dealer": false,
    "tsumo": false,
    "ronPoints": 1000,
    "tsumoPointsNonDealer": 0,
    "tsumoPointsDealer": 0
  }
}
```

## 判定内容（現状）

### 手牌役（例）

- 平和、断么九、一盃口、二盃口
- 一気通貫、三色同順、三色同刻
- 対々和、三暗刻、七対子
- 混全帯么九、純全帯么九、混老頭、小三元、混一色、清一色
- 役牌

### 役満（例）

- 国士無双、大三元、小四喜、大四喜、字一色、清老頭、緑一色、九蓮宝燈、四暗刻、天和、地和

### 状況役

- 立直、一発、門前清自摸和、嶺上開花、槍槓、海底撈月、河底撈魚

### ドラ

- ドラ（表示牌→次牌）
- 裏ドラ（立直時のみ加算）
- 赤ドラ（`*red` 牌を手牌に含む枚数分）

### 符/点

- `score` に `han/fu/親子/ロン/ツモ` の点数を返します
- 待ち形の符（嵌張/辺張/単騎など）は、現状は「アガリ牌指定（ツ/ロ）」を用いて平和判定に利用しています
- 副露の開閉（暗刻/明刻）など、詳細な符計算は今後拡張余地があります

## 表示順（役リスト）

役リストの表示順はバックエンドで決めており、現状は **翻数の降順**です。
必要なら `backend/src/main/java/com/mahjong/yakuapi/service/YakuJudgeService.java` の `finalizeResult(...)` を編集して優先順テーブルに差し替えてください。
