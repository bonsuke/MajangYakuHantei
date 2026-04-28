package com.mahjong.yakuapi.model;

import java.util.List;

public record JudgeResponse(List<YakuResult> yakuList, int totalHan, ScoreBreakdown score) {
}
