package com.mahjong.yakuapi.controller;

import com.mahjong.yakuapi.model.JudgeRequest;
import com.mahjong.yakuapi.model.JudgeResponse;
import com.mahjong.yakuapi.service.YakuJudgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class YakuJudgeController {

    private final YakuJudgeService yakuJudgeService;

    public YakuJudgeController(YakuJudgeService yakuJudgeService) {
        this.yakuJudgeService = yakuJudgeService;
    }

    @PostMapping("/judge")
    public JudgeResponse judge(@Valid @RequestBody JudgeRequest request) {
        return yakuJudgeService.judge(request.tiles(), request.context(), request.marks());
    }
}
