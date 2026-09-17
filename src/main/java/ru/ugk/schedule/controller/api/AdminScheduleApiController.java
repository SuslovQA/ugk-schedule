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
    private final ScheduleService service;
    public AdminScheduleApiController(ScheduleService service){this.service=service;}
    @GetMapping public List<ScheduleEntryResponse> list(@RequestParam Long groupId){ return service.getByGroup(groupId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ScheduleEntryResponse create(@Valid @RequestBody ScheduleEntryRequest request){ return service.create(request); }
    @PutMapping("/{id}") public ScheduleEntryResponse update(@PathVariable Long id,@Valid @RequestBody ScheduleEntryRequest request){ return service.update(id,request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){ service.delete(id); }
}
