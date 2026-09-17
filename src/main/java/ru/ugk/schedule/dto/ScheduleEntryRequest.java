package ru.ugk.schedule.dto;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record ScheduleEntryRequest(
        @NotNull Long groupId,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        @NotBlank @Size(max=255) String subject,
        @Size(max=120) String room,
        @Size(max=180) String teacherName,
        @Size(max=500) String note
) {}
