package com.anansu.powerwashrouting.service;

import com.anansu.powerwashrouting.service.RouteOptimizationService.Location;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.time.LocalDateTime;
import java.util.List;

@PlanningSolution
public class VehicleRoutingSolution {

    private Location depot;
    private List<OptimizationVehicle> vehicles;
    private List<Customer> customers;
    private LocalDateTime workStart;
    private LocalDateTime workEnd;

    private HardMediumSoftScore score;

    public VehicleRoutingSolution() {}

    public VehicleRoutingSolution(List<OptimizationVehicle> vehicles, List<Customer> customers) {
        this.vehicles = vehicles;
        this.customers = customers;
    }

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "vehicleRange")
    public List<OptimizationVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<OptimizationVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    @PlanningEntityCollectionProperty
    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    @PlanningScore
    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }

    // Problem fact getters and setters
    public Location getDepot() { return depot; }
    public void setDepot(Location depot) { this.depot = depot; }

    public LocalDateTime getWorkStart() { return workStart; }
    public void setWorkStart(LocalDateTime workStart) { this.workStart = workStart; }

    public LocalDateTime getWorkEnd() { return workEnd; }
    public void setWorkEnd(LocalDateTime workEnd) { this.workEnd = workEnd; }

    public void setWorkingHours(LocalDateTime start, LocalDateTime end) {
        this.workStart = start;
        this.workEnd = end;
    }

    // Helper methods for debugging and monitoring
    public int getTotalAssignedCustomers() {
        return (int) customers.stream()
                .filter(customer -> customer.getVehicle() != null)
                .count();
    }

    public int getTotalUnassignedCustomers() {
        return (int) customers.stream()
                .filter(customer -> customer.getVehicle() == null)
                .count();
    }

    @Override
    public String toString() {
        return "VehicleRoutingSolution{" +
                "vehicles=" + vehicles.size() +
                ", customers=" + customers.size() +
                ", assigned=" + getTotalAssignedCustomers() +
                ", unassigned=" + getTotalUnassignedCustomers() +
                ", score=" + score +
                '}';
    }
}
