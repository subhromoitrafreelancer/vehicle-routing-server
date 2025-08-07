package com.anansu.powerwashrouting.controllers;

import com.anansu.powerwashrouting.db.RouteRepository;
import com.anansu.powerwashrouting.db.VehicleRepository;
import com.anansu.powerwashrouting.model.Job;
import com.anansu.powerwashrouting.model.JobStatus;
import com.anansu.powerwashrouting.model.Route;
import com.anansu.powerwashrouting.model.ServiceType;
import com.anansu.powerwashrouting.service.RouteOptimizationService;
import com.anansu.powerwashrouting.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routing")
@CrossOrigin(origins = "*")
public class RoutingController {

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private SchedulerService schedulingService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    /**
     * Generate optimized routes for a specific date
     */
    @PostMapping("/generate-routes")
    public ResponseEntity<List<Route>> generateRoutes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Route> routes = routeOptimizationService.generateOptimizedRoutes(date);
        return ResponseEntity.ok(routes);
    }

    /**
     * Re-optimize existing routes
     */
    @PostMapping("/reoptimize-routes")
    public ResponseEntity<List<Route>> reoptimizeRoutes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam List<Long> vehicleIds) {

        List<Route> routes = routeOptimizationService.reoptimizeRoutes(date, vehicleIds);
        return ResponseEntity.ok(routes);
    }

    /**
     * Get routes for a specific date
     */
    @GetMapping("/routes")
    public ResponseEntity<List<Route>> getRoutes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Route> routes = routeRepository.findByRouteDate(date);
        return ResponseEntity.ok(routes);
    }

    /**
     * Get route details for a specific vehicle and date
     */
    @GetMapping("/routes/{vehicleId}")
    public ResponseEntity<Route> getRouteForVehicle(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return routeRepository.findByVehicleIdAndRouteDate(vehicleId, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get daily schedule for a vehicle
     */
    @GetMapping("/schedule/{vehicleId}")
    public ResponseEntity<SchedulerService.DailySchedule> getDailySchedule(
            @PathVariable Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        SchedulerService.DailySchedule schedule = schedulingService.getDailySchedule(vehicleId, date);
        return ResponseEntity.ok(schedule);
    }

    /**
     * Schedule emergency job
     */
    @PostMapping("/emergency-job")
    public ResponseEntity<Route> scheduleEmergencyJob(@RequestBody EmergencyJobRequest request) {
        Route route = schedulingService.scheduleEmergencyJob(
                request.getCustomerId(),
                request.getAddress(),
                request.getServiceType(),
                request.getPreferredTime()
        );
        return ResponseEntity.ok(route);
    }

    /**
     * Schedule estimates
     */
    @PostMapping("/schedule-estimates")
    public ResponseEntity<List<Job>> scheduleEstimates(@RequestBody ScheduleEstimatesRequest request) {
        List<Job> estimates = schedulingService.scheduleEstimates(
                request.getDate(),
                request.getAddresses()
        );
        return ResponseEntity.ok(estimates);
    }

    /**
     * Get route optimization statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getRouteStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Route> routes = routeRepository.findByRouteDate(date);

        int totalStops = routes.stream().mapToInt(r -> r.getStops().size()).sum();
        int totalDuration = routes.stream().mapToInt(r -> r.getTotalDurationMinutes()).sum();
        double totalDistance = routes.stream().mapToDouble(r -> r.getTotalDistanceKm()).sum();
        double totalFuelCost = routes.stream().mapToDouble(r -> r.getEstimatedFuelCost()).sum();

        Map<String, Object> stats = Map.of(
                "totalRoutes", routes.size(),
                "totalStops", totalStops,
                "totalDurationMinutes", totalDuration,
                "totalDistanceKm", totalDistance,
                "estimatedFuelCost", totalFuelCost,
                "averageStopsPerRoute", routes.isEmpty() ? 0 : (double) totalStops / routes.size()
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Update job status
     */
    @PutMapping("/jobs/{jobId}/status")
    public ResponseEntity<Void> updateJobStatus(
            @PathVariable Long jobId,
            @RequestParam JobStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime completedTime) {

        if (status == JobStatus.COMPLETED) {
            schedulingService.completeJob(jobId, completedTime != null ? completedTime : LocalDateTime.now());
        }

        return ResponseEntity.ok().build();
    }

    // Request DTOs
    public static class EmergencyJobRequest {
        private String customerId;
        private String address;
        private ServiceType serviceType;
        private LocalDateTime preferredTime;

        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public ServiceType getServiceType() { return serviceType; }
        public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

        public LocalDateTime getPreferredTime() { return preferredTime; }
        public void setPreferredTime(LocalDateTime preferredTime) { this.preferredTime = preferredTime; }
    }

    public static class ScheduleEstimatesRequest {
        private LocalDate date;
        private List<String> addresses;

        // Getters and setters
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public List<String> getAddresses() { return addresses; }
        public void setAddresses(List<String> addresses) { this.addresses = addresses; }
    }
}
