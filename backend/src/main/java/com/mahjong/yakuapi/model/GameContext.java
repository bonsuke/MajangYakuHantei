package com.mahjong.yakuapi.model;

import java.util.List;

public record GameContext(
        boolean menzen,
        boolean tsumo,
        boolean riichi,
        boolean ippatsu,
        boolean rinshan,
        boolean chankan,
        boolean haitei,
        boolean houtei,
        boolean tenhou,
        boolean chiihou,
        String seatWind,
        String roundWind,
        List<String> doraIndicators,
        List<String> uraDoraIndicators
) {
}
