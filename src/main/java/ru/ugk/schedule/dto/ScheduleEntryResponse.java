package ru.ugk.schedule.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleEntryResponse(Long id, Long groupId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                    String subject, String room, String teacherName, String note) {
}
