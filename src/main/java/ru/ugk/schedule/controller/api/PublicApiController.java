package ru.ugk.schedule.controller.api;

import org.springframework.web.bind.annotation.*;
import ru.ugk.schedule.service.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicApiController {
    private final CatalogService catalog; private final ScheduleService schedules;
    public PublicApiController(CatalogService catalog,ScheduleService schedules){this.catalog=catalog;this.schedules=schedules;}
    @GetMapping("/levels") public List<Map<String,Object>> levels(){ return catalog.activeLevels().stream().map(x -> Map.<String,Object>of("id",x.getId(),"name",x.getName(),"maxCourses",x.getMaxCourses())).toList(); }
    @GetMapping("/levels/{id}/courses") public List<Map<String,Object>> courses(@PathVariable Long id){ return catalog.activeCourses(id).stream().map(x -> Map.<String,Object>of("id",x.getId(),"number",x.getNumber(),"name",x.getName())).toList(); }
    @GetMapping("/courses/{id}/groups") public List<Map<String,Object>> groups(@PathVariable Long id){ return catalog.activeGroups(id).stream().map(x -> Map.<String,Object>of("id",x.getId(),"name",x.getName())).toList(); }
    @GetMapping("/groups/{id}/schedule") public Object schedule(@PathVariable Long id){ return schedules.getByGroup(id); }
}
