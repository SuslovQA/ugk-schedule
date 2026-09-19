package ru.ugk.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ugk.schedule.domain.ScheduleEntry;
import ru.ugk.schedule.dto.*;
import ru.ugk.schedule.repository.*;

import java.util.List;

@Service
@Transactional
public class ScheduleService {
    private final ScheduleEntryRepository entries;
    private final StudyGroupRepository groups;

    public ScheduleService(ScheduleEntryRepository entries, StudyGroupRepository groups) {
        this.entries = entries;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryResponse> getByGroup(Long groupId) {
        return entries.findByGroupIdOrderByDayOfWeekAscStartTimeAsc(groupId).stream().map(this::toDto).toList();
    }

    public ScheduleEntryResponse create(ScheduleEntryRequest r) {
        return toDto(save(new ScheduleEntry(), r));
    }

    public ScheduleEntryResponse update(Long id, ScheduleEntryRequest r) {
        return toDto(save(entries.findById(id).orElseThrow(), r));
    }

    public void delete(Long id) {
        entries.deleteById(id);
    }

    private ScheduleEntry save(ScheduleEntry e, ScheduleEntryRequest r) {
        e.setGroup(groups.findById(r.groupId()).orElseThrow());
        e.setDayOfWeek(r.dayOfWeek());
        e.setStartTime(r.startTime());
        e.setEndTime(r.endTime());
        e.setSubject(r.subject().trim());
        e.setRoom(blankToNull(r.room()));
        e.setTeacherName(blankToNull(r.teacherName()));
        e.setNote(blankToNull(r.note()));
        return entries.save(e);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ScheduleEntryResponse toDto(ScheduleEntry e) {
        return new ScheduleEntryResponse(e.getId(), e.getGroup().getId(), e.getDayOfWeek(), e.getStartTime(), e.getEndTime(), e.getSubject(), e.getRoom(), e.getTeacherName(), e.getNote());
    }
}
