package com.twa.flights.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class AppInfoService {

    private static final String UNKNOWN = "unknown";

    private final BuildProperties buildProperties;

    public AppInfoService(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties.getIfAvailable();
    }

    public String getAppName() {
        return buildProperties != null ? buildProperties.getName() : UNKNOWN;
    }

    public String getVersion() {
        return buildProperties != null ? buildProperties.getVersion() : UNKNOWN;
    }
}
