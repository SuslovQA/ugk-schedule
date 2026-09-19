package ru.ugk.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MaxLaunchDataService {
    private final MiniAppLaunchDataValidator validator;

    public MaxLaunchDataService(@Value("${app.max.token:}") String token, ObjectMapper mapper) {
        validator = new MiniAppLaunchDataValidator(token, mapper);
    }

    public String userId(String data) {
        return validator.userId(data);
    }
}
