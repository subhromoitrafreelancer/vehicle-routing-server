package com.anansu.powerwashrouting.model;

public enum ServiceType {
    PRESSURE_WASHING("Pressure Washing", 240),
    ROOF_CLEANING("Roof Cleaning", 180),
    WINDOW_CLEANING("Window Cleaning", 100),
    HOUSE_WASHING("House Washing", 240),
    ESTIMATE("In-person Estimate", 60);

    private final String displayName;
    private final int defaultDurationMinutes;

    ServiceType(String displayName, int defaultDurationMinutes) {
        this.displayName = displayName;
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    public String getDisplayName() { return displayName; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
}
