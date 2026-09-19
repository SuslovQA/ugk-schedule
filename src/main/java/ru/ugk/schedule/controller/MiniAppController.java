package ru.ugk.schedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MiniAppController {
    @GetMapping("/miniapp/schedule")
    public String miniapp() {
        return "miniapp/schedule";
    }
}
