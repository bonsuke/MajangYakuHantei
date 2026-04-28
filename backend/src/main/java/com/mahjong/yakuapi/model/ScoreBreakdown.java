package com.mahjong.yakuapi.model;

public record ScoreBreakdown(
        int han,
        int fu,
        boolean dealer,
        boolean tsumo,
        int ronPoints,
        int tsumoPointsNonDealer,
        int tsumoPointsDealer
) {
}

