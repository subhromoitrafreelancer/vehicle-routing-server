package com.anansu.powerwashrouting.service;

import com.anansu.powerwashrouting.db.JobRepository;
import com.anansu.powerwashrouting.db.RouteRepository;
import com.anansu.powerwashrouting.db.RouteStopRepository;
import com.anansu.powerwashrouting.db.VehicleRepository;
import com.anansu.powerwashrouting.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class RouteOptimizationService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private VehicleRoutingPlanner vehicleRoutingPlanner;

    @Autowired
    private GoogleMapsService googleMapsService;

    @Autowired
    private WeatherService weatherService;

    private static final LocalTime WORK_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime WORK_END_TIME = LocalTime.of(18, 0);
    private static final int MAX_OVERTIME_MINUTES = 120; // 2 hours

    /**
     * Generate optimized routes for a specific date
     */
    public List<Route> generateOptimizedRoutes(LocalDate date) {
        // Get available vehicles
        List<Vehicle> availableVehicles = getAvailableVehicles(date);

        // Get unassigned jobs for the date
        List<Job> unassignedJobs = getUnassignedJobs(date);

        // Filter weather-dependent jobs based on forecast
        List<Job> schedulableJobs = filterJobsByWeather(unassignedJobs, date);

        // Prioritize jobs
        schedulableJobs = prioritizeJobs(schedulableJobs);

        // Create optimization problem
        VehicleRoutingSolution problem = createRoutingProblem(availableVehicles, schedulableJobs, date);

        // Solve using OptaPlanner
        VehicleRoutingSolution solution = vehicleRoutingPlanner.solve(problem);

        // Convert solution to Route entities
        List<Route> optimizedRoutes = convertSolutionToRoutes(solution, date);

        // Save routes to database
        return routeRepository.saveAll(optimizedRoutes);
    }

    /**
     * Re-optimize existing routes (for dynamic updates)
     */
    public List<Route> reoptimizeRoutes(LocalDate date, List<Long> vehicleIds) {
        // Get existing routes for the vehicles
        List<Route> existingRoutes = vehicleIds.stream()
                .map(vehicleId -> routeRepository.findByVehicleIdAndRouteDate(vehicleId, date))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // Extract all jobs from existing routes
        List<Job> allJobs = existingRoutes.stream()
                .flatMap(route -> route.getStops().stream())
                .map(RouteStop::getJob)
                .collect(Collectors.toList());

        // Add any new unassigned jobs
        allJobs.addAll(getUnassignedJobs(date));

        // Get available vehicles
        List<Vehicle> vehicles = vehicleIds.stream()
                .map(vehicleRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // Delete existing routes
        routeRepository.deleteAll(existingRoutes);

        // Create new optimization problem
        VehicleRoutingSolution problem = createRoutingProblem(vehicles, allJobs, date);
        VehicleRoutingSolution solution = vehicleRoutingPlanner.solve(problem);

        return routeRepository.saveAll(convertSolutionToRoutes(solution, date));
    }

    /**
     * Add emergency job to existing routes
     */
    public Route handleEmergencyJob(Job emergencyJob) {
        LocalDate jobDate = emergencyJob.getPreferredStartTime().toLocalDate();

        // Find best vehicle based on proximity and availability
        Vehicle bestVehicle = findBestVehicleForEmergencyJob(emergencyJob, jobDate);

        if (bestVehicle == null) {
            throw new RuntimeException("No available vehicle for emergency job");
        }

        // Get existing route or create new one
        Route route = routeRepository.findByVehicleIdAndRouteDate(bestVehicle.getId(), jobDate)
                .orElse(new Route(jobDate, bestVehicle));

        // Insert emergency job optimally
        insertEmergencyJobIntoRoute(route, emergencyJob);

        return routeRepository.save(route);
    }

    private List<Vehicle> getAvailableVehicles(LocalDate date) {
        LocalDateTime dateTime = date.atTime(WORK_START_TIME);
        return vehicleRepository.findAvailableVehiclesForDate(dateTime);
    }

    private List<Job> getUnassignedJobs(LocalDate date) {
        LocalDateTime startOfDay = date.atTime(WORK_START_TIME);
        LocalDateTime endOfDay = date.atTime(WORK_END_TIME).plusMinutes(MAX_OVERTIME_MINUTES);

        return jobRepository.findUnassignedJobsForTimeRange(null, startOfDay, endOfDay);
    }

    private List<Job> filterJobsByWeather(List<Job> jobs, LocalDate date) {
        // Get weather forecast for the date
        WeatherService.WeatherCondition weather = weatherService.getWeatherForecast(date);

        return jobs.stream()
                //.filter(job -> !job.isWeatherDependent() || isWeatherSuitable(weather))
                .collect(Collectors.toList());
    }

    private boolean isWeatherSuitable(WeatherService.WeatherCondition weather) {
        // Define weather conditions suitable for exterior cleaning
        return !weather.isRaining() && !weather.isSnowing() &&
                weather.getWindSpeedMph() < 25 &&
                weather.getTemperatureFahrenheit() > 32;
    }

    private List<Job> prioritizeJobs(List<Job> jobs) {
        return jobs.stream()
                .sorted((j1, j2) -> {
                    // Emergency jobs first
                    if (j1.isEmergency() != j2.isEmergency()) {
                        return j1.isEmergency() ? -1 : 1;
                    }

                    // Then by priority level
                    int priorityCompare = Integer.compare(j2.getPriority().getLevel(), j1.getPriority().getLevel());
                    if (priorityCompare != 0) return priorityCompare;

                    // Then by quote amount (higher quotes first)
                    if (j1.getQuoteAmount() != null && j2.getQuoteAmount() != null) {
                        int quoteCompare = j2.getQuoteAmount().compareTo(j1.getQuoteAmount());
                        if (quoteCompare != 0) return quoteCompare;
                    }

                    // Finally by preferred start time
                    if (j1.getPreferredStartTime() != null && j2.getPreferredStartTime() != null) {
                        return j1.getPreferredStartTime().compareTo(j2.getPreferredStartTime());
                    }

                    return 0;
                })
                .collect(Collectors.toList());
    }

    private VehicleRoutingSolution createRoutingProblem(List<Vehicle> vehicles, List<Job> jobs, LocalDate date) {
        VehicleRoutingSolution problem = new VehicleRoutingSolution();

        // Set depot location (company office)
        Location depot = new Location(40.7128, -74.0060); // Example: NYC coordinates
        problem.setDepot(depot);

        // Convert vehicles to optimization vehicles
        List<OptimizationVehicle> optimizationVehicles = vehicles.stream()
                .map(this::convertToOptimizationVehicle)
                .collect(Collectors.toList());
        problem.setVehicles(optimizationVehicles);

        // Convert jobs to optimization customers
        List<Customer> customers = jobs.stream()
                .map(this::convertToCustomer)
                .collect(Collectors.toList());
        problem.setCustomers(customers);

        // Set working hours
        LocalDateTime workStart = date.atTime(WORK_START_TIME);
        LocalDateTime workEnd = date.atTime(WORK_END_TIME).plusMinutes(MAX_OVERTIME_MINUTES);
        problem.setWorkingHours(workStart, workEnd);

        return problem;
    }

    private OptimizationVehicle convertToOptimizationVehicle(Vehicle vehicle) {
        OptimizationVehicle optVehicle = new OptimizationVehicle();
        optVehicle.setId(vehicle.getId());
        optVehicle.setCapacity(vehicle.getMaxCrewSize());
        optVehicle.setCapabilities(vehicle.getCapabilities());
        optVehicle.setFuelEfficiency(vehicle.getFuelEfficiency());
        return optVehicle;
    }

    private Customer convertToCustomer(Job job) {
        Customer customer = new Customer();
        customer.setId(job.getId());
        customer.setLocation(new Location(job.getLatitude(), job.getLongitude()));
        customer.setServiceType(job.getServiceType());
        customer.setServiceTimeMinutes(job.getEstimatedDurationMinutes());
        customer.setRequiredCrewSize(job.getRequiredCrewSize());
        customer.setPriority(job.getPriority().getLevel());
        customer.setTimeWindow(job.getEarliestStartTime(), job.getLatestStartTime());
        customer.setPreferredTime(job.getPreferredStartTime());
        customer.setQuoteValue(job.getQuoteAmount());
        return customer;
    }

    private List<Route> convertSolutionToRoutes(VehicleRoutingSolution solution, LocalDate date) {
        List<Route> routes = new ArrayList<>();

        for (OptimizationVehicle optVehicle : solution.getVehicles()) {
            if (optVehicle.getCustomers().isEmpty()) continue;

            Vehicle vehicle = vehicleRepository.findById(optVehicle.getId()).orElse(null);
            if (vehicle == null) continue;

            Route route = new Route(date, vehicle);
            route.setStartTime(date.atTime(WORK_START_TIME));

            List<RouteStop> stops = new ArrayList<>();
            LocalDateTime currentTime = route.getStartTime();
            Location previousLocation = solution.getDepot();
            double totalDistance = 0;

            for (int i = 0; i < optVehicle.getCustomers().size(); i++) {
                Customer customer = optVehicle.getCustomers().get(i);
                Job job = jobRepository.findById(customer.getId()).orElse(null);
                if (job == null) continue;

                // Calculate travel time and distance
                Location currentLocation = customer.getLocation();
                TravelInfo travelInfo = googleMapsService.getTravelInfo(
                        previousLocation, currentLocation, currentTime);

                currentTime = currentTime.plusMinutes(travelInfo.getDurationMinutes());
                totalDistance += travelInfo.getDistanceKm();

                // Create route stop
                RouteStop stop = new RouteStop(route, job, i + 1);
                stop.setEstimatedArrivalTime(currentTime);
                stop.setEstimatedDepartureTime(currentTime.plusMinutes(job.getEstimatedDurationMinutes()));
                stop.setDistanceFromPreviousKm(travelInfo.getDistanceKm());
                stop.setTravelTimeFromPreviousMinutes(travelInfo.getDurationMinutes());

                stops.add(stop);

                // Update job assignment
                job.setAssignedVehicleId(vehicle.getId());
                job.setScheduledStartTime(currentTime);
                jobRepository.save(job);

                // Update for next iteration
                currentTime = stop.getEstimatedDepartureTime();
                previousLocation = currentLocation;
            }

            // Calculate return to depot
            TravelInfo returnTravel = googleMapsService.getTravelInfo(
                    previousLocation, solution.getDepot(), currentTime);
            currentTime = currentTime.plusMinutes(returnTravel.getDurationMinutes());
            totalDistance += returnTravel.getDistanceKm();

            route.setStops(stops);
            route.setEndTime(currentTime);
            route.setTotalDistanceKm(totalDistance);
            route.setTotalDurationMinutes((int) java.time.Duration.between(route.getStartTime(), route.getEndTime()).toMinutes());
            route.setEstimatedFuelCost(calculateFuelCost(totalDistance, vehicle.getFuelEfficiency()));

            routes.add(route);
        }

        return routes;
    }

    private Vehicle findBestVehicleForEmergencyJob(Job emergencyJob, LocalDate date) {
        List<Vehicle> availableVehicles = getAvailableVehicles(date);

        return availableVehicles.stream()
                .filter(vehicle -> canHandleServiceType(vehicle, emergencyJob.getServiceType()))
                .filter(vehicle -> vehicle.getMaxCrewSize() >= emergencyJob.getRequiredCrewSize())
                .min((v1, v2) -> {
                    // Find vehicle with route closest to emergency job location
                    double distance1 = getMinDistanceToExistingRoute(v1, emergencyJob, date);
                    double distance2 = getMinDistanceToExistingRoute(v2, emergencyJob, date);
                    return Double.compare(distance1, distance2);
                })
                .orElse(null);
    }

    private boolean canHandleServiceType(Vehicle vehicle, ServiceType serviceType) {
        return vehicle.getCapabilities() == null ||
                vehicle.getCapabilities().isEmpty() ||
                vehicle.getCapabilities().contains(serviceType);
    }

    private double getMinDistanceToExistingRoute(Vehicle vehicle, Job emergencyJob, LocalDate date) {
        Optional<Route> routeOpt = routeRepository.findByVehicleIdAndRouteDate(vehicle.getId(), date);
        if (routeOpt.isEmpty()) {
            return 0; // No existing route, so distance is minimal
        }

        Route route = routeOpt.get();
        return route.getStops().stream()
                .mapToDouble(stop -> calculateDistance(
                        stop.getJob().getLatitude(), stop.getJob().getLongitude(),
                        emergencyJob.getLatitude(), emergencyJob.getLongitude()))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private void insertEmergencyJobIntoRoute(Route route, Job emergencyJob) {
        if (route.getStops().isEmpty()) {
            // First job in route
            RouteStop stop = new RouteStop(route, emergencyJob, 1);
            stop.setEstimatedArrivalTime(route.getStartTime());
            stop.setEstimatedDepartureTime(route.getStartTime().plusMinutes(emergencyJob.getEstimatedDurationMinutes()));
            route.getStops().add(stop);
        } else {
            // Find best insertion point
            int bestPosition = findBestInsertionPosition(route, emergencyJob);
            insertJobAtPosition(route, emergencyJob, bestPosition);
        }

        // Recalculate all timings
        recalculateRouteTiming(route);
    }

    private int findBestInsertionPosition(Route route, Job emergencyJob) {
        double minCostIncrease = Double.MAX_VALUE;
        int bestPosition = 0;

        for (int i = 0; i <= route.getStops().size(); i++) {
            double costIncrease = calculateInsertionCost(route, emergencyJob, i);
            if (costIncrease < minCostIncrease) {
                minCostIncrease = costIncrease;
                bestPosition = i;
            }
        }

        return bestPosition;
    }

    private double calculateInsertionCost(Route route, Job newJob, int position) {
        Location newLocation = new Location(newJob.getLatitude(), newJob.getLongitude());

        if (position == 0) {
            // Insert at beginning
            if (route.getStops().isEmpty()) return 0;

            Location nextLocation = new Location(
                    route.getStops().get(0).getJob().getLatitude(),
                    route.getStops().get(0).getJob().getLongitude());

            return calculateDistance(newLocation, nextLocation);
        } else if (position == route.getStops().size()) {
            // Insert at end
            Location prevLocation = new Location(
                    route.getStops().get(position - 1).getJob().getLatitude(),
                    route.getStops().get(position - 1).getJob().getLongitude());

            return calculateDistance(prevLocation, newLocation);
        } else {
            // Insert in middle
            Location prevLocation = new Location(
                    route.getStops().get(position - 1).getJob().getLatitude(),
                    route.getStops().get(position - 1).getJob().getLongitude());
            Location nextLocation = new Location(
                    route.getStops().get(position).getJob().getLatitude(),
                    route.getStops().get(position).getJob().getLongitude());

            double originalDistance = calculateDistance(prevLocation, nextLocation);
            double newDistance = calculateDistance(prevLocation, newLocation) +
                    calculateDistance(newLocation, nextLocation);

            return newDistance - originalDistance;
        }
    }

    private void insertJobAtPosition(Route route, Job job, int position) {
        RouteStop newStop = new RouteStop(route, job, position + 1);
        route.getStops().add(position, newStop);

        // Update sequence numbers
        for (int i = position + 1; i < route.getStops().size(); i++) {
            route.getStops().get(i).setSequenceNumber(i + 1);
        }
    }

    private void recalculateRouteTiming(Route route) {
        if (route.getStops().isEmpty()) return;

        LocalDateTime currentTime = route.getStartTime();
        Location depot = new Location(40.7128, -74.0060); // Company depot
        Location previousLocation = depot;
        double totalDistance = 0;

        for (RouteStop stop : route.getStops()) {
            Job job = stop.getJob();
            Location currentLocation = new Location(job.getLatitude(), job.getLongitude());

            // Get travel info from Google Maps
            TravelInfo travelInfo = googleMapsService.getTravelInfo(
                    previousLocation, currentLocation, currentTime);

            currentTime = currentTime.plusMinutes(travelInfo.getDurationMinutes());
            totalDistance += travelInfo.getDistanceKm();

            stop.setEstimatedArrivalTime(currentTime);
            stop.setEstimatedDepartureTime(currentTime.plusMinutes(job.getEstimatedDurationMinutes()));
            stop.setDistanceFromPreviousKm(travelInfo.getDistanceKm());
            stop.setTravelTimeFromPreviousMinutes(travelInfo.getDurationMinutes());

            currentTime = stop.getEstimatedDepartureTime();
            previousLocation = currentLocation;
        }

        // Calculate return to depot
        TravelInfo returnTravel = googleMapsService.getTravelInfo(previousLocation, depot, currentTime);
        currentTime = currentTime.plusMinutes(returnTravel.getDurationMinutes());
        totalDistance += returnTravel.getDistanceKm();

        route.setEndTime(currentTime);
        route.setTotalDistanceKm(totalDistance);
        route.setTotalDurationMinutes(
                (int) java.time.Duration.between(route.getStartTime(), route.getEndTime()).toMinutes());
        route.setEstimatedFuelCost(
                calculateFuelCost(totalDistance, route.getVehicle().getFuelEfficiency()));
    }

    private double calculateFuelCost(double distanceKm, Double fuelEfficiency) {
        if (fuelEfficiency == null || fuelEfficiency <= 0) {
            fuelEfficiency = 10.0; // Default fuel efficiency
        }

        double distanceMiles = distanceKm * 0.621371;
        double gallonsUsed = distanceMiles / fuelEfficiency;
        double fuelPricePerGallon = 3.50; // Current average gas price

        return gallonsUsed * fuelPricePerGallon;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistance(new Location(lat1, lon1), new Location(lat2, lon2));
    }

    private double calculateDistance(Location loc1, Location loc2) {
        // Haversine formula for calculating distance between two points on Earth
        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double lonDistance = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // Distance in km

        return distance;
    }

    // Helper classes for optimization
    public static class Location {
        private double latitude;
        private double longitude;

        public Location(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }

    public static class TravelInfo {
        private double distanceKm;
        private int durationMinutes;

        public TravelInfo(double distanceKm, int durationMinutes) {
            this.distanceKm = distanceKm;
            this.durationMinutes = durationMinutes;
        }

        public double getDistanceKm() { return distanceKm; }
        public int getDurationMinutes() { return durationMinutes; }
    }
}
