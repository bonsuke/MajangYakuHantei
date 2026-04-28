package com.mahjong.yakuapi.service;

import com.mahjong.yakuapi.model.GameContext;
import com.mahjong.yakuapi.model.JudgeResponse;
import com.mahjong.yakuapi.model.ScoreBreakdown;
import com.mahjong.yakuapi.model.YakuResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class YakuJudgeService {
    private static final String RED_SUFFIX = "red";
    private static final Set<Integer> TERMINALS_AND_HONORS = Set.of(
            0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33
    );

    public JudgeResponse judge(List<String> inputTiles) {
        List<String> normalizedTiles = new ArrayList<>();
        int redDoraCount = 0;
        for (String tile : inputTiles) {
            String normalized = normalizeTileCode(tile);
            normalizedTiles.add(normalized);
            if (isRedTile(tile)) {
                redDoraCount++;
            }
        }
        GameContext ctx = defaultContext();
        return judgeWithContext(normalizedTiles, redDoraCount, ctx, -1);
    }

    public JudgeResponse judge(List<String> inputTiles, GameContext context) {
        return judge(inputTiles, context, null);
    }

    public JudgeResponse judge(List<String> inputTiles, GameContext context, List<String> marks) {
        List<String> normalizedTiles = new ArrayList<>();
        int redDoraCount = 0;
        for (String tile : inputTiles) {
            String normalized = normalizeTileCode(tile);
            normalizedTiles.add(normalized);
            if (isRedTile(tile)) {
                redDoraCount++;
            }
        }
        int agariTileIndex = extractAgariTileIndex(normalizedTiles, marks);
        GameContext base = context == null ? defaultContext() : context;
        GameContext ctx = applyMarksToContext(base, marks);
        return judgeWithContext(normalizedTiles, redDoraCount, ctx, agariTileIndex);
    }

    private int extractAgariTileIndex(List<String> normalizedTiles, List<String> marks) {
        if (marks == null || normalizedTiles == null) return -1;
        int n = Math.min(marks.size(), normalizedTiles.size());
        for (int i = 0; i < n; i++) {
            String m = marks.get(i);
            if ("TSUMO".equalsIgnoreCase(m) || "RON".equalsIgnoreCase(m)) {
                return toIndex(normalizedTiles.get(i));
            }
        }
        return -1;
    }

    private GameContext applyMarksToContext(GameContext base, List<String> marks) {
        if (marks == null || marks.isEmpty()) return base;
        boolean hasCall = marks.stream().anyMatch(m -> "CALL".equalsIgnoreCase(m));
        boolean hasTsumo = marks.stream().anyMatch(m -> "TSUMO".equalsIgnoreCase(m));
        boolean hasRon = marks.stream().anyMatch(m -> "RON".equalsIgnoreCase(m));

        boolean menzen = base.menzen() && !hasCall;
        boolean tsumo = hasTsumo ? true : (hasRon ? false : base.tsumo());

        return new GameContext(
                menzen,
                tsumo,
                base.riichi(),
                base.ippatsu(),
                base.rinshan(),
                base.chankan(),
                base.haitei(),
                base.houtei(),
                base.tenhou(),
                base.chiihou(),
                base.seatWind(),
                base.roundWind(),
                base.doraIndicators(),
                base.uraDoraIndicators()
        );
    }

    private JudgeResponse judgeWithContext(List<String> normalizedTiles, int redDoraCount, GameContext context, int agariTileIndex) {
        List<Integer> tileIndices = normalizedTiles.stream().map(this::toIndex).toList();
        int[] counts = new int[34];
        for (Integer idx : tileIndices) {
            if (idx < 0 || idx >= 34) {
                return new JudgeResponse(List.of(new YakuResult("不正な牌コード", 0)), 0, null);
            }
            counts[idx]++;
            if (counts[idx] > 4) {
                return new JudgeResponse(List.of(new YakuResult("同一牌が5枚以上です", 0)), 0, null);
            }
        }

        List<YakuResult> best = new ArrayList<>();
        ScoreBreakdown bestScore = null;
        int bestPoints = -1;
        int bestHan = 0;

        if (isKokushi(counts)) {
            List<YakuResult> yaku = new ArrayList<>();
            yaku.add(new YakuResult("国士無双", 13));
            addSituationYaku(yaku, context);
            addDora(yaku, counts, redDoraCount, context);
            return finalizeResult(yaku, null, counts, context, agariTileIndex);
        }

        if (isChiitoitsu(counts)) {
            List<YakuResult> yaku = new ArrayList<>();
            yaku.add(new YakuResult("七対子", 2));
            if (isTanyao(counts)) yaku.add(new YakuResult("断么九", 1));
            if (isHonroutou(counts)) yaku.add(new YakuResult("混老頭", 2));
            if (isHonitsu(counts)) yaku.add(new YakuResult("混一色", context.menzen() ? 3 : 2));
            if (isChinitsu(counts)) yaku.add(new YakuResult("清一色", context.menzen() ? 6 : 5));
            addSituationYaku(yaku, context);
            addYakumanByTileSet(yaku, counts, context);
            addDora(yaku, counts, redDoraCount, context);
            JudgeResponse resp = finalizeResult(yaku, null, counts, context, agariTileIndex);
            best = resp.yakuList();
            bestHan = resp.totalHan();
            bestScore = resp.score();
            bestPoints = Math.max(bestPoints, pointsKey(resp.score()));
        }

        List<HandPattern> patterns = buildPatterns(counts);
        for (HandPattern p : patterns) {
            List<YakuResult> yaku = evaluatePattern(p, counts, context, agariTileIndex);
            addSituationYaku(yaku, context);
            addYakumanByPattern(yaku, p, context);
            addYakumanByTileSet(yaku, counts, context);
            addDora(yaku, counts, redDoraCount, context);
            JudgeResponse resp = finalizeResult(yaku, p, counts, context, agariTileIndex);
            int key = pointsKey(resp.score());
            if (key > bestPoints || (key == bestPoints && resp.totalHan() > bestHan)) {
                best = resp.yakuList();
                bestHan = resp.totalHan();
                bestScore = resp.score();
                bestPoints = key;
            }
        }

        if (best.isEmpty()) {
            return new JudgeResponse(List.of(new YakuResult("役なし", 0)), 0, null);
        }
        return new JudgeResponse(best, bestHan, bestScore);
    }

    private GameContext defaultContext() {
        return new GameContext(false, false, false, false, false, false, false, false,
                false, false, "E", "E", List.of(), List.of());
    }

    private boolean isRedTile(String tile) {
        return tile != null && tile.endsWith(RED_SUFFIX);
    }

    private String normalizeTileCode(String tile) {
        if (tile == null) {
            return "";
        }
        return isRedTile(tile) ? tile.substring(0, tile.length() - RED_SUFFIX.length()) : tile;
    }

    private int toIndex(String tile) {
        if (tile == null || tile.length() != 2) return -1;
        int num = tile.charAt(0) - '1';
        char suit = tile.charAt(1);
        if (num < 0 || num > 8) return -1;
        return switch (suit) {
            case 'm' -> num;
            case 'p' -> 9 + num;
            case 's' -> 18 + num;
            case 'z' -> num > 6 ? -1 : 27 + num;
            default -> -1;
        };
    }

    private boolean isTanyao(int[] counts) {
        for (int i : TERMINALS_AND_HONORS) {
            if (counts[i] > 0) return false;
        }
        return true;
    }

    private boolean isChiitoitsu(int[] counts) {
        int pairCount = 0;
        for (int c : counts) {
            if (c == 2) pairCount++;
            else if (c != 0) return false;
        }
        return pairCount == 7;
    }

    private boolean isKokushi(int[] counts) {
        for (int i : TERMINALS_AND_HONORS) {
            if (counts[i] == 0) return false;
        }
        for (int i = 0; i < 34; i++) {
            if (!TERMINALS_AND_HONORS.contains(i) && counts[i] > 0) return false;
        }
        return Arrays.stream(counts).anyMatch(c -> c >= 2);
    }

    private List<HandPattern> buildPatterns(int[] counts) {
        List<HandPattern> result = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            if (counts[i] >= 2) {
                int[] work = Arrays.copyOf(counts, 34);
                work[i] -= 2;
                List<Meld> melds = new ArrayList<>();
                if (extractMelds(work, melds)) {
                    result.add(new HandPattern(i, new ArrayList<>(melds)));
                }
            }
        }
        return result;
    }

    private boolean extractMelds(int[] counts, List<Meld> melds) {
        int first = -1;
        for (int i = 0; i < 34; i++) {
            if (counts[i] > 0) {
                first = i;
                break;
            }
        }
        if (first == -1) return melds.size() == 4;

        if (counts[first] >= 3) {
            counts[first] -= 3;
            melds.add(new Meld("KOUTSU", first));
            if (extractMelds(counts, melds)) return true;
            melds.remove(melds.size() - 1);
            counts[first] += 3;
        }

        if (isSuited(first) && numberOf(first) <= 7 &&
                counts[first + 1] > 0 && counts[first + 2] > 0 &&
                sameSuit(first, first + 1) && sameSuit(first, first + 2)) {
            counts[first]--;
            counts[first + 1]--;
            counts[first + 2]--;
            melds.add(new Meld("SHUNTSU", first));
            if (extractMelds(counts, melds)) return true;
            melds.remove(melds.size() - 1);
            counts[first]++;
            counts[first + 1]++;
            counts[first + 2]++;
        }
        return false;
    }

    private List<YakuResult> evaluatePattern(HandPattern p, int[] allCounts, GameContext context, int agariTileIndex) {
        List<YakuResult> yaku = new ArrayList<>();
        boolean allTriplets = p.melds.stream().allMatch(m -> m.type.equals("KOUTSU"));
        boolean allSequences = p.melds.stream().allMatch(m -> m.type.equals("SHUNTSU"));

        if (isTanyao(allCounts)) yaku.add(new YakuResult("断么九", 1));
        if (allTriplets) yaku.add(new YakuResult("対々和", 2));
        if (isIipeikou(p)) yaku.add(new YakuResult("一盃口", 1));
        if (isRyanpeikou(p)) yaku.add(new YakuResult("二盃口", 3));
        if (isPinfu(p, context, agariTileIndex)) yaku.add(new YakuResult("平和", 1));
        if (isIttsuu(p)) yaku.add(new YakuResult("一気通貫", context.menzen() ? 2 : 1));
        if (isSanshokuDoujun(p)) yaku.add(new YakuResult("三色同順", context.menzen() ? 2 : 1));
        if (isSanshokuDoukou(p)) yaku.add(new YakuResult("三色同刻", 2));
        if (isChanta(p)) yaku.add(new YakuResult("混全帯么九", context.menzen() ? 2 : 1));
        if (isJunchan(p)) yaku.add(new YakuResult("純全帯么九", context.menzen() ? 3 : 2));
        if (isHonroutou(allCounts)) yaku.add(new YakuResult("混老頭", 2));
        if (isShousangen(allCounts)) yaku.add(new YakuResult("小三元", 2));
        if (isHonitsu(allCounts)) yaku.add(new YakuResult("混一色", context.menzen() ? 3 : 2));
        if (isChinitsu(allCounts)) yaku.add(new YakuResult("清一色", context.menzen() ? 6 : 5));
        if (isSanankou(p)) yaku.add(new YakuResult("三暗刻", 2));
        if (allSequences && yaku.stream().noneMatch(v -> v.name().equals("平和"))) {
            // no-op helper to keep p used with allSequences branch
        }

        addYakuhai(yaku, allCounts, context);
        return yaku;
    }

    private void addSituationYaku(List<YakuResult> yaku, GameContext context) {
        if (context.menzen() && context.tsumo()) yaku.add(new YakuResult("門前清自摸和", 1));
        if (context.menzen() && context.riichi()) yaku.add(new YakuResult("立直", 1));
        if (context.menzen() && context.riichi() && context.ippatsu()) yaku.add(new YakuResult("一発", 1));
        if (context.rinshan()) yaku.add(new YakuResult("嶺上開花", 1));
        if (context.chankan()) yaku.add(new YakuResult("槍槓", 1));
        if (context.haitei()) yaku.add(new YakuResult("海底撈月", 1));
        if (context.houtei()) yaku.add(new YakuResult("河底撈魚", 1));
    }

    private void addDora(List<YakuResult> yaku, int[] counts, int redDoraCount, GameContext context) {
        int dora = 0;
        for (String indicator : safeList(context.doraIndicators())) {
            int next = nextDoraIndex(toIndex(indicator));
            if (next >= 0) dora += counts[next];
        }
        if (context.menzen() && context.riichi()) {
            for (String indicator : safeList(context.uraDoraIndicators())) {
                int next = nextDoraIndex(toIndex(indicator));
                if (next >= 0) dora += counts[next];
            }
        }
        if (dora > 0) yaku.add(new YakuResult("ドラ", dora));
        if (redDoraCount > 0) yaku.add(new YakuResult("赤ドラ", redDoraCount));
    }

    private List<String> safeList(List<String> src) {
        return src == null ? Collections.emptyList() : src;
    }

    private int nextDoraIndex(int indicator) {
        if (indicator < 0) return -1;
        if (indicator <= 26) {
            int suitBase = (indicator / 9) * 9;
            int n = indicator % 9;
            return suitBase + ((n + 1) % 9);
        }
        return switch (indicator) {
            case 27 -> 28;
            case 28 -> 29;
            case 29 -> 30;
            case 30 -> 27;
            case 31 -> 32;
            case 32 -> 33;
            case 33 -> 31;
            default -> -1;
        };
    }

    private void addYakumanByPattern(List<YakuResult> yaku, HandPattern p, GameContext context) {
        if (p.melds.stream().allMatch(m -> m.type.equals("KOUTSU"))) {
            yaku.add(new YakuResult("四暗刻", 13));
        }
        if (isChuurenLike(p)) {
            yaku.add(new YakuResult("九蓮宝燈", 13));
        }
        if (context.tenhou()) yaku.add(new YakuResult("天和", 13));
        if (context.chiihou()) yaku.add(new YakuResult("地和", 13));
    }

    private void addYakumanByTileSet(List<YakuResult> yaku, int[] counts, GameContext context) {
        if (isDaisangen(counts)) yaku.add(new YakuResult("大三元", 13));
        if (isShousuushi(counts)) yaku.add(new YakuResult("小四喜", 13));
        if (isDaisuushi(counts)) yaku.add(new YakuResult("大四喜", 13));
        if (isTsuuiisou(counts)) yaku.add(new YakuResult("字一色", 13));
        if (isChinroutou(counts)) yaku.add(new YakuResult("清老頭", 13));
        if (isRyuuiisou(counts)) yaku.add(new YakuResult("緑一色", 13));
        if (context.tenhou()) yaku.add(new YakuResult("天和", 13));
        if (context.chiihou()) yaku.add(new YakuResult("地和", 13));
    }

    private void addYakuhai(List<YakuResult> yaku, int[] counts, GameContext context) {
        int han = 0;
        for (int i = 31; i <= 33; i++) {
            if (counts[i] >= 3) han++;
        }
        int seat = windToIndex(context.seatWind());
        int round = windToIndex(context.roundWind());
        if (seat >= 27 && counts[seat] >= 3) han++;
        if (round >= 27 && counts[round] >= 3) han++;
        if (han > 0) yaku.add(new YakuResult("役牌", han));
    }

    private int windToIndex(String w) {
        if (w == null) return -1;
        return switch (w.toUpperCase()) {
            case "E" -> 27;
            case "S" -> 28;
            case "W" -> 29;
            case "N" -> 30;
            default -> -1;
        };
    }

    private boolean isIipeikou(HandPattern p) {
        Map<Integer, Integer> seqFreq = new HashMap<>();
        for (Meld m : p.melds) {
            if (m.type.equals("SHUNTSU")) seqFreq.merge(m.base, 1, Integer::sum);
        }
        return seqFreq.values().stream().anyMatch(v -> v >= 2);
    }

    private boolean isRyanpeikou(HandPattern p) {
        Map<Integer, Integer> seqFreq = new HashMap<>();
        for (Meld m : p.melds) {
            if (m.type.equals("SHUNTSU")) seqFreq.merge(m.base, 1, Integer::sum);
        }
        return seqFreq.values().stream().filter(v -> v >= 2).count() >= 2;
    }

    private boolean isPinfu(HandPattern p, GameContext context, int agariTileIndex) {
        if (!context.menzen()) return false;
        if (agariTileIndex < 0) return false; // ツモ/ロン牌が指定されていないなら平和にしない
        if (p.melds.stream().anyMatch(m -> m.type.equals("KOUTSU"))) return false;
        int pair = p.pairIndex;
        if (pair >= 31) return false;
        int sw = windToIndex(context.seatWind());
        int rw = windToIndex(context.roundWind());
        if (pair == sw || pair == rw) return false;

        // 両面待ち判定: アガリ牌が順子の端で、かつ辺張/嵌張/単騎ではないこと
        if (agariTileIndex == pair) return false; // 単騎
        for (Meld m : p.melds) {
            if (!m.type.equals("SHUNTSU")) continue;
            int a = m.base;
            int b = m.base + 1;
            int c = m.base + 2;
            if (agariTileIndex != a && agariTileIndex != b && agariTileIndex != c) continue;
            if (agariTileIndex == b) return false; // 嵌張
            int startNum = numberOf(a); // 1..7
            if (agariTileIndex == c && startNum == 1) return false; // 1-2-3 の3待ち(辺張)
            if (agariTileIndex == a && startNum == 7) return false; // 7-8-9 の7待ち(辺張)
            return true; // 両面
        }
        return false;
    }

    private boolean isIttsuu(HandPattern p) {
        for (int suitBase : List.of(0, 9, 18)) {
            Set<Integer> starts = p.melds.stream()
                    .filter(m -> m.type.equals("SHUNTSU"))
                    .map(m -> m.base - suitBase)
                    .collect(Collectors.toSet());
            if (starts.contains(0) && starts.contains(3) && starts.contains(6)) return true;
        }
        return false;
    }

    private boolean isSanshokuDoujun(HandPattern p) {
        for (int n = 0; n <= 6; n++) {
            final int x = n;
            boolean m = p.melds.stream().anyMatch(v -> v.type.equals("SHUNTSU") && v.base == x);
            boolean pin = p.melds.stream().anyMatch(v -> v.type.equals("SHUNTSU") && v.base == 9 + x);
            boolean s = p.melds.stream().anyMatch(v -> v.type.equals("SHUNTSU") && v.base == 18 + x);
            if (m && pin && s) return true;
        }
        return false;
    }

    private boolean isSanshokuDoukou(HandPattern p) {
        for (int n = 0; n < 9; n++) {
            final int x = n;
            boolean m = p.melds.stream().anyMatch(v -> v.type.equals("KOUTSU") && v.base == x);
            boolean pin = p.melds.stream().anyMatch(v -> v.type.equals("KOUTSU") && v.base == 9 + x);
            boolean s = p.melds.stream().anyMatch(v -> v.type.equals("KOUTSU") && v.base == 18 + x);
            if (m && pin && s) return true;
        }
        return false;
    }

    private boolean isChanta(HandPattern p) {
        if (!TERMINALS_AND_HONORS.contains(p.pairIndex)) return false;
        return p.melds.stream().allMatch(this::meldHasTerminalOrHonor);
    }

    private boolean isJunchan(HandPattern p) {
        if (p.pairIndex >= 27 || (!isTerminal(p.pairIndex))) return false;
        return p.melds.stream().allMatch(m -> meldHasTerminalAndNoHonor(m));
    }

    private boolean meldHasTerminalOrHonor(Meld m) {
        if (m.type.equals("KOUTSU")) return TERMINALS_AND_HONORS.contains(m.base);
        int a = m.base;
        int c = m.base + 2;
        return isTerminal(a) || isTerminal(c);
    }

    private boolean meldHasTerminalAndNoHonor(Meld m) {
        if (m.base >= 27) return false;
        if (m.type.equals("KOUTSU")) return isTerminal(m.base);
        int a = numberOf(m.base);
        int c = a + 2;
        return a == 1 || c == 9;
    }

    private boolean isSanankou(HandPattern p) {
        return p.melds.stream().filter(m -> m.type.equals("KOUTSU")).count() >= 3;
    }

    private boolean isHonroutou(int[] counts) {
        for (int i = 0; i < 34; i++) {
            if (counts[i] > 0 && !TERMINALS_AND_HONORS.contains(i)) return false;
        }
        return true;
    }

    private boolean isShousangen(int[] counts) {
        int dragonTriplets = 0;
        int dragonPair = 0;
        for (int i = 31; i <= 33; i++) {
            if (counts[i] >= 3) dragonTriplets++;
            if (counts[i] == 2) dragonPair++;
        }
        return dragonTriplets == 2 && dragonPair == 1;
    }

    private boolean isHonitsu(int[] counts) {
        Set<Integer> suits = new HashSet<>();
        boolean hasHonor = false;
        for (int i = 0; i < 34; i++) {
            if (counts[i] == 0) continue;
            if (i >= 27) hasHonor = true;
            else suits.add(i / 9);
        }
        return suits.size() == 1 && hasHonor;
    }

    private boolean isChinitsu(int[] counts) {
        Set<Integer> suits = new HashSet<>();
        for (int i = 0; i < 34; i++) {
            if (counts[i] == 0) continue;
            if (i >= 27) return false;
            suits.add(i / 9);
        }
        return suits.size() == 1;
    }

    private boolean isDaisangen(int[] counts) {
        return counts[31] >= 3 && counts[32] >= 3 && counts[33] >= 3;
    }

    private boolean isShousuushi(int[] counts) {
        int triplets = 0;
        int pair = 0;
        for (int i = 27; i <= 30; i++) {
            if (counts[i] >= 3) triplets++;
            if (counts[i] == 2) pair++;
        }
        return triplets == 3 && pair == 1;
    }

    private boolean isDaisuushi(int[] counts) {
        for (int i = 27; i <= 30; i++) {
            if (counts[i] < 3) return false;
        }
        return true;
    }

    private boolean isTsuuiisou(int[] counts) {
        for (int i = 0; i < 27; i++) {
            if (counts[i] > 0) return false;
        }
        return true;
    }

    private boolean isChinroutou(int[] counts) {
        for (int i = 0; i < 34; i++) {
            if (counts[i] == 0) continue;
            if (i >= 27 || !isTerminal(i)) return false;
        }
        return true;
    }

    private boolean isRyuuiisou(int[] counts) {
        Set<Integer> green = Set.of(19, 20, 21, 23, 25, 32);
        for (int i = 0; i < 34; i++) {
            if (counts[i] > 0 && !green.contains(i)) return false;
        }
        return true;
    }

    private boolean isChuurenLike(HandPattern p) {
        Set<Integer> suits = p.melds.stream().map(m -> m.base).filter(i -> i < 27).map(i -> i / 9).collect(Collectors.toSet());
        suits.add(p.pairIndex < 27 ? p.pairIndex / 9 : 9);
        return suits.size() == 1 && !suits.contains(9);
    }

    private boolean isSuited(int idx) {
        return idx < 27;
    }

    private int numberOf(int idx) {
        return (idx % 9) + 1;
    }

    private boolean sameSuit(int a, int b) {
        return a / 9 == b / 9;
    }

    private boolean isTerminal(int idx) {
        if (idx >= 27) return true;
        int n = numberOf(idx);
        return n == 1 || n == 9;
    }

    private JudgeResponse finalizeResult(List<YakuResult> yaku, HandPattern pattern, int[] counts, GameContext context, int agariTileIndex) {
        Map<String, Integer> merged = new HashMap<>();
        for (YakuResult y : yaku) {
            merged.merge(y.name(), y.han(), Integer::sum);
        }
        List<YakuResult> sorted = merged.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue).reversed())
                .map(e -> new YakuResult(e.getKey(), e.getValue()))
                .toList();
        int totalHan = sorted.stream().mapToInt(YakuResult::han).sum();
        ScoreBreakdown score = computeScore(totalHan, pattern, counts, context, agariTileIndex);
        return new JudgeResponse(sorted, totalHan, score);
    }

    private int pointsKey(ScoreBreakdown score) {
        if (score == null) return 0;
        if (!score.tsumo()) return score.ronPoints();
        // pick the max payment; dealer payment is usually higher
        return Math.max(score.tsumoPointsDealer(), score.tsumoPointsNonDealer());
    }

    private ScoreBreakdown computeScore(int han, HandPattern pattern, int[] counts, GameContext context, int agariTileIndex) {
        if (han <= 0) return null;
        boolean dealer = context.seatWind() != null
                && context.roundWind() != null
                && context.seatWind().equalsIgnoreCase(context.roundWind());
        boolean tsumo = context.tsumo();

        int fu = computeFu(han, pattern, counts, context, agariTileIndex);
        int basePoints = computeBasePoints(han, fu);

        int ronPoints = ceil100(basePoints * (dealer ? 6 : 4));
        int tsumoNonDealer = dealer ? ceil100(basePoints * 2) : ceil100(basePoints);
        int tsumoDealer = dealer ? ceil100(basePoints * 2) : ceil100(basePoints * 2);

        return new ScoreBreakdown(han, fu, dealer, tsumo, ronPoints, tsumoNonDealer, tsumoDealer);
    }

    private int computeFu(int han, HandPattern pattern, int[] counts, GameContext context, int agariTileIndex) {
        // NOTE: without agari tile & wait type, we omit wait fu.
        // This covers base fu + menzen ron/tsumo + meld fu + pair fu.
        if (pattern == null) {
            // Chiitoitsu is fixed 25 fu; yakuman treated as 0 (ignored in base points calc)
            if (isChiitoitsu(counts)) return 25;
            return 30;
        }

        boolean pinfu = context.menzen() && pattern.melds.stream().allMatch(m -> m.type.equals("SHUNTSU")) && isPinfu(pattern, context, agariTileIndex);
        if (pinfu && context.tsumo()) return 20;
        if (pinfu && !context.tsumo()) return 30;

        int fu = 20;
        if (context.tsumo()) fu += 2;
        if (context.menzen() && !context.tsumo()) fu += 10;

        // pair fu
        int pair = pattern.pairIndex;
        if (pair >= 31 && pair <= 33) fu += 2; // dragons
        int sw = windToIndex(context.seatWind());
        int rw = windToIndex(context.roundWind());
        if (pair == sw) fu += 2;
        if (pair == rw) fu += 2;

        // meld fu (treat all triplets as open for now; closed/open requires calls info)
        for (Meld m : pattern.melds) {
            if (!m.type.equals("KOUTSU")) continue;
            boolean yao = TERMINALS_AND_HONORS.contains(m.base);
            // open triplet: 2/4 fu ; closed triplet: 4/8 fu. Without data, assume 2/4.
            fu += yao ? 4 : 2;
        }

        return roundUpFu(fu);
    }

    private int roundUpFu(int fu) {
        int r = ((fu + 9) / 10) * 10;
        return Math.max(r, 20);
    }

    private int computeBasePoints(int han, int fu) {
        if (han >= 13) return 8000; // yakuman (single)
        if (han >= 11) return 6000; // sanbaiman
        if (han >= 8) return 4000;  // baiman
        if (han >= 6) return 3000;  // haneman
        if (han == 5) return 2000;  // mangan
        if (han == 4 && fu >= 40) return 2000; // mangan
        if (han == 3 && fu >= 70) return 2000; // mangan

        double base = fu * Math.pow(2, han + 2);
        return (int) Math.floor(base);
    }

    private int ceil100(int points) {
        return ((points + 99) / 100) * 100;
    }

    private static class HandPattern {
        final int pairIndex;
        final List<Meld> melds;

        HandPattern(int pairIndex, List<Meld> melds) {
            this.pairIndex = pairIndex;
            this.melds = melds;
        }
    }

    private static class Meld {
        final String type;
        final int base;

        Meld(String type, int base) {
            this.type = type;
            this.base = base;
        }
    }
}
