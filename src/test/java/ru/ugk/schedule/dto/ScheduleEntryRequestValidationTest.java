package ru.ugk.schedule.dto;

import jakarta.validation.Validation;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleEntryRequestValidationTest {
    @ParameterizedTest
    @CsvSource({"09:00,08:59,false", "09:00,09:00,true", "09:00,10:35,true", "09:00,,true"})
    void validatesEndTime(String start, String end, boolean valid) {
        var request = new ScheduleEntryRequest(1L, DayOfWeek.MONDAY, LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end), "Предмет", null, null, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request).isEmpty()).isEqualTo(valid);
        }
    }
}
