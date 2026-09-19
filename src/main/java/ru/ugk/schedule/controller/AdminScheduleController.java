package ru.ugk.schedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.ugk.schedule.service.CatalogService;

@Controller
@RequestMapping("/admin/schedule")
public class AdminScheduleController {
    private final CatalogService catalog;

    public AdminScheduleController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Long groupId, Model model) {
        model.addAttribute("levels", catalog.activeLevels());
        model.addAttribute("selectedGroupId", groupId);
        return "admin/schedule";
    }
}
