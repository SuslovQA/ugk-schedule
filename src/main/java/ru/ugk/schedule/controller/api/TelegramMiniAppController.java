package ru.ugk.schedule.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ugk.schedule.domain.MessengerType;
import ru.ugk.schedule.service.TelegramLaunchDataService;
import ru.ugk.schedule.service.UserPreferenceService;

@RestController
@RequestMapping("/api/public/telegram")
public class TelegramMiniAppController {
    private final TelegramLaunchDataService launchData;
    private final UserPreferenceService preferences;

    public TelegramMiniAppController(TelegramLaunchDataService launchData, UserPreferenceService preferences) {
        this.launchData = launchData;
        this.preferences = preferences;
    }

    public record LaunchRequest(String initData) {}
    public record GroupResponse(Long groupId) {}

    @PostMapping("/group")
    public ResponseEntity<GroupResponse> group(@RequestBody LaunchRequest request) {
        String userId = launchData.userId(request.initData());
        Long groupId = preferences.selectedGroupId(MessengerType.TELEGRAM, userId).orElse(null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new GroupResponse(groupId));
    }
}
