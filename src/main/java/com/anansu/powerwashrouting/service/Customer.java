package com.anansu.powerwashrouting.service;


import com.anansu.powerwashrouting.model.ServiceType;
import com.anansu.powerwashrouting.service.RouteOptimizationService.Location;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@PlanningEntity
public class Customer {

    private Long id;
    private Location location;
    private ServiceType serviceType;
    private int serviceTimeMinutes;
    private int requiredCrewSize;
    private int priority;
    private LocalDateTime earliestStartTime;
    private LocalDateTime latestStartTime;
    private LocalDateTime preferredTime;
    private BigDecimal quoteValue;

    // Planning variables
    private OptimizationVehicle vehicle;
    private Customer previousCustomer;

    public Customer() {}

    public Customer(Long id, Location location, ServiceType serviceType) {
        this.id = id;
        this.location = location;
        this.serviceType = serviceType;
    }

    @PlanningVariable(valueRangeProviderRefs = {"vehicleRange"})
    public OptimizationVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(OptimizationVehicle vehicle) {
        this.vehicle = vehicle;
        // Maintain bidirectional relationship
        if (vehicle != null && !vehicle.getCustomers().contains(this)) {
            vehicle.getCustomers().add(this);
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public int getServiceTimeMinutes() { return serviceTimeMinutes; }
    public void setServiceTimeMinutes(int serviceTimeMinutes) { this.serviceTimeMinutes = serviceTimeMinutes; }

    public int getRequiredCrewSize() { return requiredCrewSize; }
    public void setRequiredCrewSize(int requiredCrewSize) { this.requiredCrewSize = requiredCrewSize; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public LocalDateTime getEarliestStartTime() { return earliestStartTime; }
    public void setEarliestStartTime(LocalDateTime earliestStartTime) {
        this.earliestStartTime = earliestStartTime;
    }

    public LocalDateTime getLatestStartTime() { return latestStartTime; }
    public void setLatestStartTime(LocalDateTime latestStartTime) {
        this.latestStartTime = latestStartTime;
    }

    public LocalDateTime getPreferredTime() { return preferredTime; }
    public void setPreferredTime(LocalDateTime preferredTime) { this.preferredTime = preferredTime; }

    public BigDecimal getQuoteValue() { return quoteValue; }
    public void setQuoteValue(BigDecimal quoteValue) { this.quoteValue = quoteValue; }

    public void setTimeWindow(LocalDateTime earliest, LocalDateTime latest) {
        this.earliestStartTime = earliest;
        this.latestStartTime = latest;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", serviceType=" + serviceType +
                ", vehicle=" + (vehicle != null ? vehicle.getId() : "null") +
                '}';
    }

    public Customer getPreviousCustomer() {
        return previousCustomer;
    }

    public void setPreviousCustomer(Customer previousCustomer) {
        this.previousCustomer = previousCustomer;
    }
}
