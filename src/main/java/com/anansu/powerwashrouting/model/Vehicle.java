package com.anansu.powerwashrouting.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    private VehicleType type;

    @Column(nullable = false)
    private boolean available = true;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private Set<ServiceType> capabilities;

    private LocalDateTime maintenanceScheduled;
    private Double fuelEfficiency; // miles per gallon
    private Integer maxCrewSize = 3;

    // Constructors, getters, setters
    public Vehicle() {}

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Set<ServiceType> getCapabilities() { return capabilities; }
    public void setCapabilities(Set<ServiceType> capabilities) { this.capabilities = capabilities; }

    public LocalDateTime getMaintenanceScheduled() { return maintenanceScheduled; }
    public void setMaintenanceScheduled(LocalDateTime maintenanceScheduled) {
        this.maintenanceScheduled = maintenanceScheduled;
    }

    public Double getFuelEfficiency() { return fuelEfficiency; }
    public void setFuelEfficiency(Double fuelEfficiency) { this.fuelEfficiency = fuelEfficiency; }

    public Integer getMaxCrewSize() { return maxCrewSize; }
    public void setMaxCrewSize(Integer maxCrewSize) { this.maxCrewSize = maxCrewSize; }
}
