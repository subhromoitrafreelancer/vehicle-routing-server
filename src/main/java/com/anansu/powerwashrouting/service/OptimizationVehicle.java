package com.anansu.powerwashrouting.service;

import com.anansu.powerwashrouting.model.ServiceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OptimizationVehicle {

    private Long id;
    private Integer capacity;
    private Set<ServiceType> capabilities;
    private Double fuelEfficiency;
    private List<Customer> customers = new ArrayList<>();

    public OptimizationVehicle() {}

    public OptimizationVehicle(Long id, Integer capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Set<ServiceType> getCapabilities() { return capabilities; }
    public void setCapabilities(Set<ServiceType> capabilities) { this.capabilities = capabilities; }

    public Double getFuelEfficiency() { return fuelEfficiency; }
    public void setFuelEfficiency(Double fuelEfficiency) { this.fuelEfficiency = fuelEfficiency; }

    public List<Customer> getCustomers() { return customers; }
    public void setCustomers(List<Customer> customers) { this.customers = customers; }

    public int getTotalDemand() {
        return customers.stream().mapToInt(Customer::getRequiredCrewSize).sum();
    }

    public int getTotalServiceTime() {
        return customers.stream().mapToInt(Customer::getServiceTimeMinutes).sum();
    }
}
