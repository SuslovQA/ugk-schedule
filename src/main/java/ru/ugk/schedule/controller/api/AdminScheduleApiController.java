package ru.ugk.schedule.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ugk.schedule.dto.*;
import ru.ugk.schedule.service.ScheduleService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/schedule")
public class AdminScheduleApiController {
    private static final org.slf4j.Logger audit=org.slf4j.LoggerFactory.getLogger("ADMIN_AUDIT");
    private final ScheduleService service;

    public AdminScheduleApiController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScheduleEntryResponse> list(@RequestParam Long groupId) {
        return service.getByGroup(groupId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleEntryResponse create(@Valid @RequestBody ScheduleEntryRequest request, java.security.Principal actor) {
        var result=service.create(request);
        audit.info("actor={} action=schedule.create id={} group={}", actor.getName(), result.id(), request.groupId());
        return result;
    }

    @PutMapping("/{id}")
    public ScheduleEntryResponse update(@PathVariable Long id, @Valid @RequestBody ScheduleEntryRequest request, java.security.Principal actor) {
        var result=service.update(id, request);
        audit.info("actor={} action=schedule.update id={} group={}", actor.getName(), id, request.groupId());
        return result;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, java.security.Principal actor) {
        service.delete(id);
        audit.info("actor={} action=schedule.delete id={}", actor.getName(), id);
    }
}
