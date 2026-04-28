import { useEffect, useMemo, useState } from 'react'
import './App.css'

const BASE_TILES = [
  { code: '1m', label: '一萬' }, { code: '2m', label: '二萬' }, { code: '3m', label: '三萬' },
  { code: '4m', label: '四萬' }, { code: '5m', label: '五萬' }, { code: '6m', label: '六萬' },
  { code: '7m', label: '七萬' }, { code: '8m', label: '八萬' }, { code: '9m', label: '九萬' },
  { code: '1p', label: '一筒' }, { code: '2p', label: '二筒' }, { code: '3p', label: '三筒' },
  { code: '4p', label: '四筒' }, { code: '5p', label: '五筒' }, { code: '6p', label: '六筒' },
  { code: '7p', label: '七筒' }, { code: '8p', label: '八筒' }, { code: '9p', label: '九筒' },
  { code: '1s', label: '一索' }, { code: '2s', label: '二索' }, { code: '3s', label: '三索' },
  { code: '4s', label: '四索' }, { code: '5s', label: '五索' }, { code: '6s', label: '六索' },
  { code: '7s', label: '七索' }, { code: '8s', label: '八索' }, { code: '9s', label: '九索' },
  { code: '1z', label: '東' }, { code: '2z', label: '南' }, { code: '3z', label: '西' }, { code: '4z', label: '北' },
  { code: '5z', label: '白' }, { code: '6z', label: '發' }, { code: '7z', label: '中' }
]

const RED_TILE_BASES = new Set(['5m', '5p', '5s'])
const RED_SUFFIX = 'red'
const SUIT_ORDER = { m: 0, p: 1, s: 2, z: 3 }

const toBaseCode = (code) => code.endsWith(RED_SUFFIX) ? code.slice(0, -RED_SUFFIX.length) : code
const isRedCode = (code) => code.endsWith(RED_SUFFIX)
const getTileSortKey = (code) => {
  const baseCode = toBaseCode(code)
  const num = Number(baseCode[0])
  const suit = baseCode[1]
  return [SUIT_ORDER[suit] ?? 9, num, isRedCode(code) ? 1 : 0]
}

const TILE_CATALOG = BASE_TILES.flatMap((tile) => {
  const normal = { ...tile, imagePath: `/tiles/${tile.code}.gif` }
  if (!RED_TILE_BASES.has(tile.code)) {
    return [normal]
  }
  const redCode = `${tile.code}${RED_SUFFIX}`
  return [
    normal,
    { code: redCode, label: `${tile.label}(赤)`, imagePath: `/tiles/${redCode}.gif` }
  ]
})

const tileByCode = Object.fromEntries(TILE_CATALOG.map((tile) => [tile.code, tile]))

const YAKUMAN_NAMES = new Set([
  '国士無双',
  '四暗刻',
  '九蓮宝燈',
  '天和',
  '地和',
  '大三元',
  '小四喜',
  '大四喜',
  '字一色',
  '清老頭',
  '緑一色'
])

const getRankLabel = (totalHan, yakuList) => {
  const hasYakuman = Array.isArray(yakuList) && yakuList.some((y) => YAKUMAN_NAMES.has(y.name))
  if (totalHan >= 13) return hasYakuman ? '役満' : '数え役満'
  if (totalHan >= 11) return '三倍満'
  if (totalHan >= 8) return '倍満'
  if (totalHan >= 6) return '跳満'
  if (totalHan >= 4) return '満貫'
  return null
}

const MARKS = ['', 'CALL', 'TSUMO', 'RON']
const MARK_LABEL = {
  '': '　',
  CALL: '鳴',
  TSUMO: 'ツ',
  RON: 'ロ'
}

function App() {
  const [selectedTiles, setSelectedTiles] = useState([])
  const [tileMarks, setTileMarks] = useState([])
  const [result, setResult] = useState(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [context, setContext] = useState({
    menzen: true,
    tsumo: false,
    riichi: false,
    ippatsu: false,
    rinshan: false,
    chankan: false,
    haitei: false,
    houtei: false,
    tenhou: false,
    chiihou: false,
    seatWind: 'E',
    roundWind: 'E',
    doraIndicators: [],
    uraDoraIndicators: []
  })
  const indicatorCatalog = BASE_TILES.map((tile) => ({ ...tile, imagePath: `/tiles/${tile.code}.gif` }))
  const [pickerTarget, setPickerTarget] = useState(null)
  const hasCall = useMemo(() => tileMarks.some((m) => m === 'CALL'), [tileMarks])

  useEffect(() => {
    if (!hasCall) return
    setContext((prev) => ({
      ...prev,
      riichi: false,
      ippatsu: false,
      tenhou: false,
      chiihou: false
    }))
  }, [hasCall])

  const tileCountMap = useMemo(() => {
    const counts = {}
    for (const tile of selectedTiles) {
      const baseCode = toBaseCode(tile)
      counts[baseCode] = (counts[baseCode] ?? 0) + 1
    }
    return counts
  }, [selectedTiles])

  const addTile = (code) => {
    if (selectedTiles.length >= 14) return
    if ((tileCountMap[code] ?? 0) >= 4) return
    setSelectedTiles((prev) => [...prev, code])
    setTileMarks((prev) => [...prev, ''])
  }

  const removeTile = (index) => {
    setSelectedTiles((prev) => prev.filter((_, i) => i !== index))
    setTileMarks((prev) => prev.filter((_, i) => i !== index))
  }

  const clearTiles = () => {
    setSelectedTiles([])
    setTileMarks([])
    setResult(null)
    setError('')
  }

  const cycleMark = (index) => {
    setTileMarks((prev) => {
      const next = [...prev]
      const cur = next[index] ?? ''
      const nextIdx = (MARKS.indexOf(cur) + 1) % MARKS.length
      const newVal = MARKS[nextIdx]
      // TSUMO/RON は同時に1枚だけに制限
      if (newVal === 'TSUMO' || newVal === 'RON') {
        for (let i = 0; i < next.length; i++) {
          if (i !== index && (next[i] === 'TSUMO' || next[i] === 'RON')) next[i] = ''
        }
      }
      next[index] = newVal
      return next
    })
  }

  const riihaiTiles = () => {
    setSelectedTiles((prev) => [...prev].sort((a, b) => {
      const ka = getTileSortKey(a)
      const kb = getTileSortKey(b)
      if (ka[0] !== kb[0]) return ka[0] - kb[0]
      if (ka[1] !== kb[1]) return ka[1] - kb[1]
      return ka[2] - kb[2]
    }))
    setTileMarks((prev) => {
      const zipped = selectedTiles.map((t, i) => ({ t, m: prev[i] ?? '' }))
      zipped.sort((a, b) => {
        const ka = getTileSortKey(a.t)
        const kb = getTileSortKey(b.t)
        if (ka[0] !== kb[0]) return ka[0] - kb[0]
        if (ka[1] !== kb[1]) return ka[1] - kb[1]
        return ka[2] - kb[2]
      })
      return zipped.map((x) => x.m)
    })
  }

  const judgeHand = async () => {
    if (selectedTiles.length !== 14) {
      setError('14枚ちょうど選択してください。')
      return
    }
    setIsLoading(true)
    setError('')
    try {
      const response = await fetch('http://localhost:8080/api/judge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tiles: selectedTiles,
          context,
          marks: tileMarks.length === 14 ? tileMarks : Array.from({ length: 14 }, () => '')
        })
      })
      if (!response.ok) {
        throw new Error(`status=${response.status}`)
      }
      const data = await response.json()
      setResult(data)
    } catch (e) {
      setError(`判定APIの呼び出しに失敗しました (${String(e)})`)
      setResult(null)
    } finally {
      setIsLoading(false)
    }
  }

  const addIndicator = (key, code) => {
    setContext((prev) => ({ ...prev, [key]: [...prev[key], code] }))
    setPickerTarget(null)
  }

  const removeIndicatorAt = (key, index) => {
    setContext((prev) => ({ ...prev, [key]: prev[key].filter((_, i) => i !== index) }))
  }

  const pickerSections = useMemo(() => ([
    { key: 'm', cols: 9, tiles: indicatorCatalog.slice(0, 9) },
    { key: 'p', cols: 9, tiles: indicatorCatalog.slice(9, 18) },
    { key: 's', cols: 9, tiles: indicatorCatalog.slice(18, 27) },
    { key: 'z', cols: 7, tiles: indicatorCatalog.slice(27, 34) }
  ]), [indicatorCatalog])

  return (
    <main className="container">
      <header>
        <h1>麻雀 役判定プロトタイプ</h1>
        <p>牌を14枚選択して「判定する」を押してください。</p>
      </header>
      <div className="layout">
        <div className="main-column">
          <section className="panel">
            <h2>選択中の手牌 ({selectedTiles.length}/14)</h2>
            <div className="selected-tiles">
              {selectedTiles.map((code, index) => (
                <div key={`${code}-${index}`} className="selected-tile-wrap">
                  <button className="tile selected" onClick={() => removeTile(index)} type="button">
                    <img src={tileByCode[code]?.imagePath} alt={tileByCode[code]?.label ?? code} className="tile-image" />
                  </button>
                  <button type="button" className={`mark-btn ${tileMarks[index] || 'empty'}`} onClick={() => cycleMark(index)}>
                    {MARK_LABEL[tileMarks[index] ?? ''] ?? '　'}
                  </button>
                </div>
              ))}
              {Array.from({ length: Math.max(0, 14 - selectedTiles.length) }).map((_, i) => (
                <div key={`empty-${i}`} className="selected-tile-wrap">
                  <div className="tile placeholder" aria-hidden="true"></div>
                  <div className="mark-btn placeholder" aria-hidden="true"></div>
                </div>
              ))}
            </div>
            <div className="controls">
              <button type="button" onClick={judgeHand} disabled={isLoading}>役判定</button>
              <button type="button" className="secondary" onClick={riihaiTiles} disabled={selectedTiles.length <= 1}>理牌</button>
              <button type="button" className="secondary" onClick={clearTiles}>クリア</button>
            </div>
            {error && <p className="error">{error}</p>}
          </section>

          <section className="panel">
            <h2>牌一覧</h2>
            <div className="tile-grid">
              {TILE_CATALOG.map((tile) => (
                <button
                  key={tile.code}
                  className="tile"
                  type="button"
                  onClick={() => addTile(tile.code)}
                  disabled={selectedTiles.length >= 14 || (tileCountMap[toBaseCode(tile.code)] ?? 0) >= 4}
                >
                  <img src={tile.imagePath} alt={tile.label} className="tile-image" />
                </button>
              ))}
            </div>
          </section>

          <section className="panel result">
            <h2>判定結果</h2>
            {!result && <p className="empty-message">まだ判定していません。</p>}
            {result && (
              <>
                <p className="han">
                  合計: <strong>{result.totalHan}</strong> 翻
                  {getRankLabel(result.totalHan, result.yakuList) && (
                    <span className="rank-badge">{getRankLabel(result.totalHan, result.yakuList)}</span>
                  )}
                </p>
                {result.score && (
                  <div className="score-line">
                    <span><strong>{result.score.fu}</strong> 符</span>
                    {!result.score.tsumo ? (
                      <span>ロン: <strong>{result.score.ronPoints}</strong> 点</span>
                    ) : (
                      <span>
                        ツモ:
                        {result.score.dealer ? (
                          <> <strong>{result.score.tsumoPointsDealer}</strong> オール</>
                        ) : (
                          <> <strong>{result.score.tsumoPointsNonDealer}</strong> / <strong>{result.score.tsumoPointsDealer}</strong></>
                        )}
                      </span>
                    )}
                  </div>
                )}
                {result.yakuList.length === 0 ? (
                  <p>役なし</p>
                ) : (
                  <ul>
                    {result.yakuList.map((yaku) => (
                      <li key={yaku.name}>
                        <span>{yaku.name}</span>
                        <strong>{yaku.han} 翻</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </section>
        </div>

        <aside className="panel settings-panel">
          <h2>判定設定</h2>
          <div className="settings-list">
            {[
              ['riichi', '立直'],
              ['ippatsu', '一発'],
              ['rinshan', '嶺上開花'],
              ['chankan', '槍槓'],
              ['haitei', '海底'],
              ['houtei', '河底'],
              ['tenhou', '天和'],
              ['chiihou', '地和']
            ].map(([key, label]) => (
              <label key={key} className="check-row">
                <input
                  type="checkbox"
                  disabled={hasCall && (key === 'riichi' || key === 'ippatsu' || key === 'tenhou' || key === 'chiihou')}
                  checked={context[key]}
                  onChange={(e) => setContext((prev) => ({ ...prev, [key]: e.target.checked }))}
                />
                <span>{label}</span>
              </label>
            ))}

            <div className="wind-row">
              <label className="field">
                <span className="field-title">
                  場風
                  <span className="dealer-badge">
                    {context.roundWind === context.seatWind ? '親' : '子'}
                  </span>
                </span>
                <select value={context.roundWind} onChange={(e) => setContext((p) => ({ ...p, roundWind: e.target.value }))}>
                  <option value="E">東</option><option value="S">南</option><option value="W">西</option><option value="N">北</option>
                </select>
              </label>
              <label className="field">
                <span>自風</span>
                <select value={context.seatWind} onChange={(e) => setContext((p) => ({ ...p, seatWind: e.target.value }))}>
                  <option value="E">東</option><option value="S">南</option><option value="W">西</option><option value="N">北</option>
                </select>
              </label>
            </div>
            <label className="field">
              <span className="field-title">ドラ表示牌 <button type="button" className="open-picker-btn" onClick={() => setPickerTarget('doraIndicators')}>選択</button></span>
              <div className="indicator-selected">
                {context.doraIndicators.length === 0 && <span className="empty-message">未選択</span>}
                {context.doraIndicators.map((code, index) => (
                  <button key={`dora-${code}-${index}`} type="button" className="mini-tile" onClick={() => removeIndicatorAt('doraIndicators', index)}>
                    <img src={`/tiles/${code}.gif`} alt={code} className="tile-image mini-image" />
                  </button>
                ))}
              </div>
            </label>
            <label className="field">
              <span className="field-title">裏ドラ表示牌 <button type="button" className="open-picker-btn" onClick={() => setPickerTarget('uraDoraIndicators')}>選択</button></span>
              <div className="indicator-selected">
                {context.uraDoraIndicators.length === 0 && <span className="empty-message">未選択</span>}
                {context.uraDoraIndicators.map((code, index) => (
                  <button key={`uradora-${code}-${index}`} type="button" className="mini-tile" onClick={() => removeIndicatorAt('uraDoraIndicators', index)}>
                    <img src={`/tiles/${code}.gif`} alt={code} className="tile-image mini-image" />
                  </button>
                ))}
              </div>
            </label>
          </div>
        </aside>
      </div>
      {pickerTarget && (
        <div className="picker-overlay" onClick={() => setPickerTarget(null)}>
          <div className="picker-modal" onClick={(e) => e.stopPropagation()}>
            <div className="picker-header">
              <strong>{pickerTarget === 'doraIndicators' ? 'ドラ表示牌を選択' : '裏ドラ表示牌を選択'}</strong>
              <button type="button" className="close-picker-btn" onClick={() => setPickerTarget(null)}>閉じる</button>
            </div>
            <div className="picker-sections">
              {pickerSections.map((section) => (
                <div key={section.key} className="picker-section">
                  <div className="indicator-grid popup-grid" style={{ gridTemplateColumns: `repeat(${section.cols}, 32px)` }}>
                    {section.tiles.map((tile) => (
                      <button key={`pick-${pickerTarget}-${tile.code}`} type="button" className="mini-tile" onClick={() => addIndicator(pickerTarget, tile.code)}>
                        <img src={tile.imagePath} alt={tile.label} className="tile-image mini-image" />
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </main>
  )
}

export default App
