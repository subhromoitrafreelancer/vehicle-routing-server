package com.anansu.powerwashrouting.service;



import com.anansu.powerwashrouting.service.RouteOptimizationService.Location;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.time.LocalDateTime;

public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Hard constraints
                vehicleCapacity(constraintFactory),
                serviceTypeCompatibility(constraintFactory),
                timeWindows(constraintFactory),
                workingHours(constraintFactory),

                // Medium constraints
                minimizeBacktracking(constraintFactory),
                balanceWorkload(constraintFactory),

                // Soft constraints
                minimizeTravelTime(constraintFactory),
                preferredTimes(constraintFactory),
                prioritizeHighValueJobs(constraintFactory),
                minimizeFuelCost(constraintFactory)
        };
    }

    // Hard Constraints
    private Constraint vehicleCapacity(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(OptimizationVehicle.class)
                .filter(vehicle -> vehicle.getTotalDemand() > vehicle.getCapacity())
                .penalize("Vehicle capacity exceeded", HardMediumSoftScore.ONE_HARD,
                        vehicle -> vehicle.getTotalDemand() - vehicle.getCapacity());
    }

    private Constraint serviceTypeCompatibility(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .filter(customer -> customer.getVehicle() != null &&
                        !canVehicleHandleService(customer.getVehicle(), customer))
                .penalize("Service type compatibility",HardMediumSoftScore.ONE_HARD, customer-> 1);
    }

    private Constraint timeWindows(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .filter(customer -> customer.getVehicle() != null)
                .filter(this::violatesTimeWindow)
                .penalize("Time window violations",HardMediumSoftScore.ONE_HARD, this::timeWindowViolationMinutes);
    }

    private Constraint workingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(OptimizationVehicle.class)
                .filter(this::exceedsWorkingHours)
                .penalize("Working hours exceeded", HardMediumSoftScore.ONE_HARD,this::overtimeMinutes);
    }

    // Medium Constraints
    private Constraint minimizeBacktracking(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .join(Customer.class,
                        Joiners.equal(Customer::getVehicle),
                        Joiners.lessThan(customer -> getSequenceNumber(customer)))
                .filter((customer1, customer2) -> isBacktracking(customer1, customer2))
                .penalize("Minimize backtracking",HardMediumSoftScore.ONE_MEDIUM,
                        (customer1, customer2) -> calculateBacktrackingPenalty(customer1, customer2));
    }

    private Constraint balanceWorkload(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(OptimizationVehicle.class)
                .join(OptimizationVehicle.class, Joiners.lessThan(OptimizationVehicle::getId))
                .penalize("Balance workload",HardMediumSoftScore.ONE_MEDIUM,
                        (vehicle1, vehicle2) -> Math.abs(vehicle1.getTotalServiceTime() - vehicle2.getTotalServiceTime()));
    }

    // Soft Constraints
    private Constraint minimizeTravelTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .filter(customer -> customer.getVehicle() != null)
                .penalize("Minimize travel time",HardMediumSoftScore.ONE_SOFT, this::calculateTravelTime);
    }

    private Constraint preferredTimes(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .filter(customer -> customer.getPreferredTime() != null && customer.getVehicle() != null)
                .penalize("Preferred time adherence", HardMediumSoftScore.ONE_SOFT,this::calculatePreferredTimeDeviation);
    }

    private Constraint prioritizeHighValueJobs(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Customer.class)
                .filter(customer -> customer.getVehicle() == null) // Unassigned jobs
                .penalize("Prioritize high value jobs",HardMediumSoftScore.ONE_SOFT,
                        customer -> customer.getPriority() * getQuoteValuePoints(customer));

    }

    private Constraint minimizeFuelCost(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(OptimizationVehicle.class)
                .penalize("Minimize fuel cost", HardMediumSoftScore.ONE_SOFT,this::calculateFuelCost);
    }

    // Helper methods
    private boolean canVehicleHandleService(OptimizationVehicle vehicle, Customer customer) {
        if (vehicle.getCapabilities() == null || vehicle.getCapabilities().isEmpty()) {
            return true; // No restrictions
        }
        return vehicle.getCapabilities().contains(customer.getServiceType());
    }

    private boolean violatesTimeWindow(Customer customer) {
        LocalDateTime arrivalTime = calculateArrivalTime(customer);
        return (customer.getEarliestStartTime() != null && arrivalTime.isBefore(customer.getEarliestStartTime())) ||
                (customer.getLatestStartTime() != null && arrivalTime.isAfter(customer.getLatestStartTime()));
    }

    private int timeWindowViolationMinutes(Customer customer) {
        LocalDateTime arrivalTime = calculateArrivalTime(customer);

        if (customer.getEarliestStartTime() != null && arrivalTime.isBefore(customer.getEarliestStartTime())) {
            return (int) java.time.Duration.between(arrivalTime, customer.getEarliestStartTime()).toMinutes();
        }

        if (customer.getLatestStartTime() != null && arrivalTime.isAfter(customer.getLatestStartTime())) {
            return (int) java.time.Duration.between(customer.getLatestStartTime(), arrivalTime).toMinutes();
        }

        return 0;
    }

    private boolean exceedsWorkingHours(OptimizationVehicle vehicle) {
        if (vehicle.getCustomers().isEmpty()) return false;

        LocalDateTime workStart = LocalDateTime.of(2024, 1, 1, 8, 0); // Will be properly set from solution
        LocalDateTime maxWorkEnd = workStart.plusHours(10).plusMinutes(120); // 8-6 + 2 hrs overtime

        LocalDateTime routeEnd = calculateRouteEndTime(vehicle);
        return routeEnd.isAfter(maxWorkEnd);
    }

    private int overtimeMinutes(OptimizationVehicle vehicle) {
        LocalDateTime workStart = LocalDateTime.of(2024, 1, 1, 8, 0);
        LocalDateTime regularWorkEnd = workStart.plusHours(10); // 8-6
        LocalDateTime routeEnd = calculateRouteEndTime(vehicle);

        if (routeEnd.isAfter(regularWorkEnd)) {
            return (int) java.time.Duration.between(regularWorkEnd, routeEnd).toMinutes();
        }
        return 0;
    }

    private int getSequenceNumber(Customer customer) {
        int sequence = 0;
        Customer current = customer;
        while (current.getPreviousCustomer() != null) {
            sequence++;
            current = current.getPreviousCustomer();
        }
        return sequence;
    }

    private boolean isBacktracking(Customer customer1, Customer customer2) {
        // Calculate if going from customer1 to customer2 represents backtracking
        // This is a simplified implementation - could be enhanced with actual geographic analysis
        Location loc1 = customer1.getLocation();
        Location loc2 = customer2.getLocation();

        // Simple distance-based backtracking detection
        double distance = calculateDistance(loc1, loc2);
        return distance > 15.0; // Arbitrary threshold in km
    }

    private int calculateBacktrackingPenalty(Customer customer1, Customer customer2) {
        double distance = calculateDistance(customer1.getLocation(), customer2.getLocation());
        return (int) Math.max(0, (distance - 15.0) * 10); // Penalty grows with distance
    }

    private int calculateTravelTime(Customer customer) {
        if (customer.getPreviousCustomer() == null) {
            // Travel from depot
            return estimateTravelTime(getDepotLocation(), customer.getLocation());
        } else {
            return estimateTravelTime(customer.getPreviousCustomer().getLocation(), customer.getLocation());
        }
    }

    private int calculatePreferredTimeDeviation(Customer customer) {
        LocalDateTime arrivalTime = calculateArrivalTime(customer);
        LocalDateTime preferredTime = customer.getPreferredTime();

        return Math.abs((int) java.time.Duration.between(preferredTime, arrivalTime).toMinutes());
    }

    private int getQuoteValuePoints(Customer customer) {
        if (customer.getQuoteValue() == null) return 1;

        // Convert quote value to points (e.g., $100 = 1 point)
        return customer.getQuoteValue().intValue() / 100;
    }

    private int calculateFuelCost(OptimizationVehicle vehicle) {
        if (vehicle.getCustomers().isEmpty()) return 0;

        double totalDistance = 0;
        Location currentLocation = getDepotLocation();

        for (Customer customer : vehicle.getCustomers()) {
            totalDistance += calculateDistance(currentLocation, customer.getLocation());
            currentLocation = customer.getLocation();
        }

        // Return to depot
        totalDistance += calculateDistance(currentLocation, getDepotLocation());

        // Convert to fuel cost (simplified)
        double fuelEfficiency = vehicle.getFuelEfficiency() != null ? vehicle.getFuelEfficiency() : 10.0;
        double distanceMiles = totalDistance * 0.621371;
        double gallons = distanceMiles / fuelEfficiency;

        return (int) (gallons * 350); // $3.50 per gallon * 100 for integer math
    }

    private LocalDateTime calculateArrivalTime(Customer customer) {
        // Simplified calculation - in real implementation, this would use actual route timing
        LocalDateTime workStart = LocalDateTime.of(2024, 1, 1, 8, 0);
        int sequenceNumber = getSequenceNumber(customer);

        // Estimate based on position in route
        return workStart.plusMinutes(sequenceNumber * 60); // Rough estimate
    }

    private LocalDateTime calculateRouteEndTime(OptimizationVehicle vehicle) {
        if (vehicle.getCustomers().isEmpty()) {
            return LocalDateTime.of(2024, 1, 1, 8, 0);
        }

        LocalDateTime workStart = LocalDateTime.of(2024, 1, 1, 8, 0);
        int totalTime = vehicle.getTotalServiceTime();

        // Add travel time estimates
        for (Customer customer : vehicle.getCustomers()) {
            totalTime += calculateTravelTime(customer);
        }

        return workStart.plusMinutes(totalTime);
    }

    private double calculateDistance(Location loc1, Location loc2) {
        // Haversine formula
        final int R = 6371; // Earth's radius in km

        double latDistance = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double lonDistance = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private int estimateTravelTime(Location from, Location to) {
        double distance = calculateDistance(from, to);
        // Assume average speed of 30 mph in urban areas
        double timeHours = distance / 48.28; // 30 mph converted to km/h
        return (int) (timeHours * 60); // Convert to minutes
    }

    private Location getDepotLocation() {
        // This should be injected or configured properly in a real implementation
        return new Location(40.7128, -74.0060); // Example depot location
    }
}
