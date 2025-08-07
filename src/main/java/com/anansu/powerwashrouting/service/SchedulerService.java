package com.anansu.powerwashrouting.service;


import com.anansu.powerwashrouting.model.*;
import com.anansu.powerwashrouting.db.*;
//import com.anansu.powerwashrouting.service.CrmIntegrationService;
import com.anansu.powerwashrouting.service.GoogleMapsService;
import com.anansu.powerwashrouting.service.RouteOptimizationService.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;
import java.math.BigDecimal;

@Service
@Transactional
public class SchedulerService {

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private CrmIntegrationService crmIntegrationService;

    @Autowired
    private GoogleMapsService googleMapsService;

    @Autowired
    private WeatherService weatherService;

    private static final LocalTime WORK_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime WORK_END_TIME = LocalTime.of(18, 0);
    private static final int MAX_OVERTIME_MINUTES = 120; // 2 hours

    /**
     * Daily job to import new approved quotes and generate routes
     * Runs every day at 6 AM
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void dailyRouteGeneration() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try {
            // Import new approved quotes from CRM
            importApprovedQuotes();

            // Generate optimized routes for tomorrow
            List<Route> routes = routeOptimizationService.generateOptimizedRoutes(tomorrow);

            // Create estimate appointments in CRM
            createEstimateAppointments(tomorrow);

            // Log route generation results
            logRouteGenerationResults(routes, tomorrow);

        } catch (Exception e) {
            System.err.println("Error in daily route generation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Schedule estimates for a specific date
     */
    public List<Job> scheduleEstimates(LocalDate date, List<String> addresses) {
        List<Job> estimates = addresses.stream()
                .map(address -> createEstimateJob(address, date))
                .filter(job -> job != null)
                .collect(Collectors.toList());

        if (estimates.isEmpty()) {
            throw new RuntimeException("No valid estimates could be created from provided addresses");
        }

        // Save estimates
        estimates = jobRepository.saveAll(estimates);

        // Optimize estimate routes
        routeOptimizationService.generateOptimizedRoutes(date);

        return estimates;
    }

    /**
     * Handle emergency job request
     */
    public Route scheduleEmergencyJob(String customerId, String address, ServiceType serviceType,
                                      LocalDateTime preferredTime) {

        // Validate input
        if (customerId == null || address == null || serviceType == null || preferredTime == null) {
            throw new IllegalArgumentException("All emergency job parameters are required");
        }

        // Create emergency job
        Job emergencyJob = new Job();
        emergencyJob.setCustomerId(customerId);
        emergencyJob.setAddress(address);
        emergencyJob.setServiceType(serviceType);
        emergencyJob.setPreferredStartTime(preferredTime);
        emergencyJob.setEmergency(true);
        emergencyJob.setPriority(Priority.EMERGENCY);
        emergencyJob.setEstimatedDurationMinutes(serviceType.getDefaultDurationMinutes());

        // Set time windows for emergency (more flexible)
        emergencyJob.setEarliestStartTime(preferredTime.minusHours(2));
        emergencyJob.setLatestStartTime(preferredTime.plusHours(4));

        // Geocode address
        Location location = googleMapsService.geocodeAddress(address);
        if (location == null) {
            throw new RuntimeException("Unable to geocode address: " + address);
        }

        emergencyJob.setLatitude(location.getLatitude());
        emergencyJob.setLongitude(location.getLongitude());

        // Save job
        emergencyJob = jobRepository.save(emergencyJob);

        // Insert into existing route
        Route route = routeOptimizationService.handleEmergencyJob(emergencyJob);

        // Notify CRM about emergency job
        try {
            crmIntegrationService.updateJobStatus(emergencyJob.getId(), "EMERGENCY_SCHEDULED", LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Failed to notify CRM about emergency job: " + e.getMessage());
        }

        return route;
    }

    /**
     * Reschedule jobs due to weather
     * Runs every day at 8 PM
     */
    @Scheduled(cron = "0 0 20 * * *")
    public void checkWeatherAndReschedule() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        try {
            // Get weather forecast
            WeatherService.WeatherCondition weather = weatherService.getWeatherForecast(tomorrow);

            if (!isWeatherSuitableForWork(weather)) {
                System.out.println("Unsuitable weather detected for " + tomorrow + ": " + weather);

                // Reschedule weather-dependent jobs
                List<Job> weatherDependentJobs = jobRepository.findWeatherDependentJobs(
                        tomorrow.atTime(LocalTime.MIN),
                        tomorrow.atTime(LocalTime.MAX)
                );

                LocalDate nextSuitableDate = findNextSuitableDate(tomorrow.plusDays(1));

                for (Job job : weatherDependentJobs) {
                    rescheduleJob(job, nextSuitableDate);
                }

                // Regenerate routes for tomorrow
                if (!weatherDependentJobs.isEmpty()) {
                    routeOptimizationService.generateOptimizedRoutes(tomorrow);
                    System.out.println("Rescheduled " + weatherDependentJobs.size() + " weather-dependent jobs");
                }
            }
        } catch (Exception e) {
            System.err.println("Error in weather check and reschedule: " + e.getMessage());
        }
    }

    /**
     * Update job status when completed
     */
    public void completeJob(Long jobId, LocalDateTime completedTime) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.COMPLETED);
        job.setActualEndTime(completedTime != null ? completedTime : LocalDateTime.now());

        jobRepository.save(job);

        // Update CRM
        try {
            crmIntegrationService.updateJobStatus(jobId, "COMPLETED", job.getActualEndTime());
        } catch (Exception e) {
            System.err.println("Failed to update CRM job status: " + e.getMessage());
        }

        // If this was a recurring job, schedule next occurrence
        if (job.isRecurring()) {
            scheduleNextRecurrence(job);
        }

        // Update route stop actual times
        updateRouteStopActualTimes(job, completedTime);
    }

    /**
     * Get daily schedule for a specific vehicle
     */
    public DailySchedule getDailySchedule(Long vehicleId, LocalDate date) {
        // Validate vehicle exists
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

        Route route = routeRepository.findByVehicleIdAndRouteDate(vehicleId, date)
                .orElse(null);

        if (route == null) {
            return new DailySchedule(vehicleId, date, List.of(), vehicle.getLicensePlate());
        }

        List<ScheduleItem> items = route.getStops().stream()
                .map(this::convertToScheduleItem)
                .collect(Collectors.toList());

        return new DailySchedule(vehicleId, date, items, vehicle.getLicensePlate());
    }

    /**
     * Get all schedules for a specific date
     */
    public List<DailySchedule> getAllSchedulesForDate(LocalDate date) {
        List<Route> routes = routeRepository.findByRouteDate(date);

        return routes.stream()
                .map(route -> getDailySchedule(route.getVehicle().getId(), date))
                .collect(Collectors.toList());
    }

    /**
     * Manually reschedule a specific job
     */
    public void rescheduleJob(Long jobId, LocalDate newDate, LocalTime newTime) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // Remove from current route if assigned
        if (job.getAssignedVehicleId() != null) {
            removeJobFromRoute(job);
        }

        // Update job timing
        job.setPreferredStartTime(newDate.atTime(newTime));
        job.setEarliestStartTime(newDate.atTime(WORK_START_TIME));
        job.setLatestStartTime(newDate.atTime(WORK_END_TIME));
        job.setAssignedVehicleId(null);
        job.setScheduledStartTime(null);
        job.setStatus(JobStatus.SCHEDULED);

        jobRepository.save(job);

        // Re-optimize routes for both old and new dates
        LocalDate oldDate = job.getScheduledStartTime() != null ?
                job.getScheduledStartTime().toLocalDate() : LocalDate.now();

        if (!oldDate.equals(newDate)) {
            routeOptimizationService.generateOptimizedRoutes(oldDate);
        }
        routeOptimizationService.generateOptimizedRoutes(newDate);
    }

    /**
     * Get route efficiency metrics
     */
    public RouteMetrics getRouteMetrics(LocalDate date) {
        List<Route> routes = routeRepository.findByRouteDate(date);

        if (routes.isEmpty()) {
            return new RouteMetrics(date, 0, 0, 0.0, 0.0, 0, 0.0);
        }

        int totalRoutes = routes.size();
        int totalJobs = routes.stream().mapToInt(r -> r.getStops().size()).sum();
        double totalDistance = routes.stream().mapToDouble(r -> r.getTotalDistanceKm()).sum();
        double totalFuelCost = routes.stream().mapToDouble(r -> r.getEstimatedFuelCost()).sum();
        int totalDuration = routes.stream().mapToInt(r -> r.getTotalDurationMinutes()).sum();
        double avgJobsPerRoute = (double) totalJobs / totalRoutes;

        return new RouteMetrics(date, totalRoutes, totalJobs, totalDistance,
                totalFuelCost, totalDuration, avgJobsPerRoute);
    }

    // Private helper methods

    private void importApprovedQuotes() {
        try {
            List<Job> newJobs = crmIntegrationService.fetchApprovedQuotes();
            jobRepository.saveAll(newJobs);
            System.out.println("Imported " + newJobs.size() + " new jobs from CRM");
        } catch (Exception e) {
            System.err.println("Error importing approved quotes: " + e.getMessage());
        }
    }

    private void createEstimateAppointments(LocalDate date) {
        LocalDateTime startOfDay = date.atTime(WORK_START_TIME);
        LocalDateTime endOfDay = date.atTime(WORK_END_TIME);

        List<Job> estimates = jobRepository.findEstimatesForDateRange(startOfDay, endOfDay);

        for (Job estimate : estimates) {
            try {
                crmIntegrationService.createEstimateAppointment(estimate);
            } catch (Exception e) {
                System.err.println("Error creating estimate appointment for job " + estimate.getId() + ": " + e.getMessage());
            }
        }
    }

    private Job createEstimateJob(String address, LocalDate date) {
        try {
            Location location = googleMapsService.geocodeAddress(address);
            if (location == null) {
                System.err.println("Could not geocode address: " + address);
                return null;
            }

            Job estimate = new Job();
            estimate.setAddress(address);
            estimate.setLatitude(location.getLatitude());
            estimate.setLongitude(location.getLongitude());
            estimate.setServiceType(ServiceType.ESTIMATE);
            estimate.setPreferredStartTime(date.atTime(9, 0)); // Default to 9 AM
            estimate.setEarliestStartTime(date.atTime(WORK_START_TIME));
            estimate.setLatestStartTime(date.atTime(WORK_END_TIME));
            estimate.setEstimatedDurationMinutes(ServiceType.ESTIMATE.getDefaultDurationMinutes());
            estimate.setRequiredCrewSize(1); // Estimates typically require 1 person
            estimate.setPriority(Priority.MEDIUM);
            estimate.setWeatherDependent(false); // Estimates can be done in most weather

            return estimate;
        } catch (Exception e) {
            System.err.println("Error creating estimate job for address: " + address + " - " + e.getMessage());
            return null;
        }
    }

    private boolean isWeatherSuitableForWork(WeatherService.WeatherCondition weather) {
        return !weather.isRaining() && !weather.isSnowing() &&
                weather.getWindSpeedMph() < 25 &&
                weather.getTemperatureFahrenheit() > 32;
    }

    private LocalDate findNextSuitableDate(LocalDate startDate) {
        LocalDate date = startDate;
        for (int i = 0; i < 7; i++) { // Check next 7 days
            try {
                WeatherService.WeatherCondition weather = weatherService.getWeatherForecast(date);
                if (isWeatherSuitableForWork(weather)) {
                    return date;
                }
            } catch (Exception e) {
                System.err.println("Error checking weather for " + date + ": " + e.getMessage());
            }
            date = date.plusDays(1);
        }
        return startDate.plusDays(7); // Default to next week if no suitable weather found
    }

    private void rescheduleJob(Job job, LocalDate newDate) {
        // Calculate time offset to maintain preferred time of day
        LocalTime originalTime = job.getPreferredStartTime() != null ?
                job.getPreferredStartTime().toLocalTime() : LocalTime.of(9, 0);

        job.setPreferredStartTime(newDate.atTime(originalTime));
        job.setEarliestStartTime(newDate.atTime(WORK_START_TIME));
        job.setLatestStartTime(newDate.atTime(WORK_END_TIME));
        job.setStatus(JobStatus.RESCHEDULED);
        job.setAssignedVehicleId(null);
        job.setScheduledStartTime(null);

        jobRepository.save(job);

        // Notify CRM about rescheduling
        try {
            crmIntegrationService.updateJobStatus(job.getId(), "RESCHEDULED_WEATHER", LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("Failed to notify CRM about job rescheduling: " + e.getMessage());
        }
    }

    private void scheduleNextRecurrence(Job completedJob) {
        try {
            // This is a simplified implementation
            // In a real system, you'd parse the cron expression in recurringSchedule
            if (completedJob.getRecurringSchedule() != null) {
                Job nextJob = new Job();

                // Copy properties from completed job
                nextJob.setCustomerId(completedJob.getCustomerId());
                nextJob.setAddress(completedJob.getAddress());
                nextJob.setLatitude(completedJob.getLatitude());
                nextJob.setLongitude(completedJob.getLongitude());
                nextJob.setServiceType(completedJob.getServiceType());
                nextJob.setEstimatedDurationMinutes(completedJob.getEstimatedDurationMinutes());
                nextJob.setRequiredCrewSize(completedJob.getRequiredCrewSize());
                nextJob.setPriority(completedJob.getPriority());
                nextJob.setQuoteAmount(completedJob.getQuoteAmount());
                nextJob.setRecurring(true);
                nextJob.setRecurringSchedule(completedJob.getRecurringSchedule());
                nextJob.setWeatherDependent(completedJob.isWeatherDependent());

                // Schedule for next occurrence (simplified - monthly)
                LocalDateTime nextDate = completedJob.getPreferredStartTime().plusMonths(1);
                nextJob.setPreferredStartTime(nextDate);
                nextJob.setEarliestStartTime(nextDate.toLocalDate().atTime(WORK_START_TIME));
                nextJob.setLatestStartTime(nextDate.toLocalDate().atTime(WORK_END_TIME));

                jobRepository.save(nextJob);
                System.out.println("Scheduled next recurrence for customer " + nextJob.getCustomerId());
            }
        } catch (Exception e) {
            System.err.println("Error scheduling next recurrence: " + e.getMessage());
        }
    }

    private void updateRouteStopActualTimes(Job job, LocalDateTime completedTime) {
        try {
            Optional<Route> routeOpt = routeRepository.findByVehicleIdAndRouteDate(
                    job.getAssignedVehicleId(),
                    job.getScheduledStartTime().toLocalDate()
            );

            if (routeOpt.isPresent()) {
                Route route = routeOpt.get();
                Optional<RouteStop> stopOpt = route.getStops().stream()
                        .filter(stop -> stop.getJob().getId().equals(job.getId()))
                        .findFirst();

                if (stopOpt.isPresent()) {
                    RouteStop stop = stopOpt.get();
                    stop.setActualArrivalTime(job.getActualStartTime());
                    stop.setActualDepartureTime(completedTime);
                    routeStopRepository.save(stop);
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating route stop actual times: " + e.getMessage());
        }
    }

    private void removeJobFromRoute(Job job) {
        try {
            if (job.getAssignedVehicleId() != null && job.getScheduledStartTime() != null) {
                Optional<Route> routeOpt = routeRepository.findByVehicleIdAndRouteDate(
                        job.getAssignedVehicleId(),
                        job.getScheduledStartTime().toLocalDate()
                );

                if (routeOpt.isPresent()) {
                    Route route = routeOpt.get();
                    route.getStops().removeIf(stop -> stop.getJob().getId().equals(job.getId()));
                    routeRepository.save(route);
                }
            }
        } catch (Exception e) {
            System.err.println("Error removing job from route: " + e.getMessage());
        }
    }

    private ScheduleItem convertToScheduleItem(RouteStop stop) {
        Job job = stop.getJob();
        return new ScheduleItem(
                job.getId(),
                job.getCustomerId(),
                job.getAddress(),
                job.getServiceType(),
                stop.getEstimatedArrivalTime(),
                stop.getEstimatedDepartureTime(),
                job.getEstimatedDurationMinutes(),
                stop.getSequenceNumber(),
                job.getPriority(),
                job.isEmergency()
        );
    }

    private void logRouteGenerationResults(List<Route> routes, LocalDate date) {
        System.out.println("=== Route Generation Results for " + date + " ===");
        System.out.println("Generated " + routes.size() + " routes");

        int totalJobs = 0;
        double totalDistance = 0;
        int totalDuration = 0;

        for (Route route : routes) {
            totalJobs += route.getStops().size();
            totalDistance += route.getTotalDistanceKm();
            totalDuration += route.getTotalDurationMinutes();

            System.out.println("Vehicle " + route.getVehicle().getLicensePlate() +
                    ": " + route.getStops().size() + " stops, " +
                    String.format("%.1f", route.getTotalDistanceKm()) + " km, " +
                    (route.getTotalDurationMinutes() / 60) + "h " +
                    (route.getTotalDurationMinutes() % 60) + "m");
        }

        System.out.println("Total: " + totalJobs + " jobs, " +
                String.format("%.1f", totalDistance) + " km, " +
                (totalDuration / 60) + "h " + (totalDuration % 60) + "m");
        System.out.println("===============================================");
    }

    // Inner classes for return types

    /**
     * Daily schedule for a vehicle
     */
    public static class DailySchedule {
        private Long vehicleId;
        private LocalDate date;
        private List<ScheduleItem> items;
        private String vehicleLicensePlate;

        public DailySchedule(Long vehicleId, LocalDate date, List<ScheduleItem> items, String vehicleLicensePlate) {
            this.vehicleId = vehicleId;
            this.date = date;
            this.items = items;
            this.vehicleLicensePlate = vehicleLicensePlate;
        }

        // Getters
        public Long getVehicleId() { return vehicleId; }
        public LocalDate getDate() { return date; }
        public List<ScheduleItem> getItems() { return items; }
        public String getVehicleLicensePlate() { return vehicleLicensePlate; }

        public int getTotalJobs() { return items.size(); }
        public int getTotalDurationMinutes() {
            return items.stream().mapToInt(ScheduleItem::getDurationMinutes).sum();
        }
    }

    /**
     * Individual schedule item
     */
    public static class ScheduleItem {
        private Long jobId;
        private String customerId;
        private String address;
        private ServiceType serviceType;
        private LocalDateTime arrivalTime;
        private LocalDateTime departureTime;
        private Integer durationMinutes;
        private Integer sequenceNumber;
        private Priority priority;
        private boolean emergency;

        public ScheduleItem(Long jobId, String customerId, String address, ServiceType serviceType,
                            LocalDateTime arrivalTime, LocalDateTime departureTime, Integer durationMinutes,
                            Integer sequenceNumber, Priority priority, boolean emergency) {
            this.jobId = jobId;
            this.customerId = customerId;
            this.address = address;
            this.serviceType = serviceType;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
            this.durationMinutes = durationMinutes;
            this.sequenceNumber = sequenceNumber;
            this.priority = priority;
            this.emergency = emergency;
        }

        // Getters
        public Long getJobId() { return jobId; }
        public String getCustomerId() { return customerId; }
        public String getAddress() { return address; }
        public ServiceType getServiceType() { return serviceType; }
        public LocalDateTime getArrivalTime() { return arrivalTime; }
        public LocalDateTime getDepartureTime() { return departureTime; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public Integer getSequenceNumber() { return sequenceNumber; }
        public Priority getPriority() { return priority; }
        public boolean isEmergency() { return emergency; }
    }

    /**
     * Route efficiency metrics
     */
    public static class RouteMetrics {
        private LocalDate date;
        private int totalRoutes;
        private int totalJobs;
        private double totalDistanceKm;
        private double totalFuelCost;
        private int totalDurationMinutes;
        private double averageJobsPerRoute;

        public RouteMetrics(LocalDate date, int totalRoutes, int totalJobs, double totalDistanceKm,
                            double totalFuelCost, int totalDurationMinutes, double averageJobsPerRoute) {
            this.date = date;
            this.totalRoutes = totalRoutes;
            this.totalJobs = totalJobs;
            this.totalDistanceKm = totalDistanceKm;
            this.totalFuelCost = totalFuelCost;
            this.totalDurationMinutes = totalDurationMinutes;
            this.averageJobsPerRoute = averageJobsPerRoute;
        }

        // Getters
        public LocalDate getDate() { return date; }
        public int getTotalRoutes() { return totalRoutes; }
        public int getTotalJobs() { return totalJobs; }
        public double getTotalDistanceKm() { return totalDistanceKm; }
        public double getTotalFuelCost() { return totalFuelCost; }
        public int getTotalDurationMinutes() { return totalDurationMinutes; }
        public double getAverageJobsPerRoute() { return averageJobsPerRoute; }

        public double getAverageDistancePerRoute() {
            return totalRoutes > 0 ? totalDistanceKm / totalRoutes : 0;
        }

        public double getAverageDurationPerRoute() {
            return totalRoutes > 0 ? (double) totalDurationMinutes / totalRoutes : 0;
        }
    }
}
