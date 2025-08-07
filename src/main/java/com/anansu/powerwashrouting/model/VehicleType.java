package com.anansu.powerwashrouting.model;

public enum VehicleType {
    TRUCK("Standard Cleaning Truck"),
    PAVER_VAN("Specialized Paver Van");

    private final String description;

    VehicleType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
