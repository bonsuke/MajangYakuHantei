package com.mahjong.yakuapi.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JudgeRequest(
        @NotEmpty
        @Size(min = 14, max = 14, message = "Tiles must contain exactly 14 elements.")
        List<String> tiles,
        GameContext context,
        @NotNull
        @Size(min = 14, max = 14, message = "Marks must contain exactly 14 elements.")
        List<String> marks
) {
}
