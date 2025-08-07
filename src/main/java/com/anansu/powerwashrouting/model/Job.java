package com.anansu.powerwashrouting.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;

    private BigDecimal quoteAmount;
    private Integer estimatedDurationMinutes;
    private Integer requiredCrewSize = 2;

    // Time windows
    private LocalDateTime earliestStartTime;
    private LocalDateTime latestStartTime;
    private LocalDateTime preferredStartTime;

    // Weather considerations
    private boolean weatherDependent = true;

    // Recurring job information
    private boolean recurring = false;
    private String recurringSchedule; // cron expression

    // Assignment tracking
    private Long assignedVehicleId;
    private LocalDateTime scheduledStartTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    // Emergency flags
    private boolean emergency = false;

    public Job() {}

    public Job(String customerId, String address, Double latitude, Double longitude, ServiceType serviceType) {
        this.customerId = customerId;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.serviceType = serviceType;
        this.estimatedDurationMinutes = serviceType.getDefaultDurationMinutes();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        if (this.estimatedDurationMinutes == null) {
            this.estimatedDurationMinutes = serviceType.getDefaultDurationMinutes();
        }
    }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public BigDecimal getQuoteAmount() { return quoteAmount; }
    public void setQuoteAmount(BigDecimal quoteAmount) { this.quoteAmount = quoteAmount; }

    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public Integer getRequiredCrewSize() { return requiredCrewSize; }
    public void setRequiredCrewSize(Integer requiredCrewSize) { this.requiredCrewSize = requiredCrewSize; }

    public LocalDateTime getEarliestStartTime() { return earliestStartTime; }
    public void setEarliestStartTime(LocalDateTime earliestStartTime) {
        this.earliestStartTime = earliestStartTime;
    }

    public LocalDateTime getLatestStartTime() { return latestStartTime; }
    public void setLatestStartTime(LocalDateTime latestStartTime) { this.latestStartTime = latestStartTime; }

    public LocalDateTime getPreferredStartTime() { return preferredStartTime; }
    public void setPreferredStartTime(LocalDateTime preferredStartTime) {
        this.preferredStartTime = preferredStartTime;
    }

    public boolean isWeatherDependent() { return weatherDependent; }
    public void setWeatherDependent(boolean weatherDependent) { this.weatherDependent = weatherDependent; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    public String getRecurringSchedule() { return recurringSchedule; }
    public void setRecurringSchedule(String recurringSchedule) { this.recurringSchedule = recurringSchedule; }

    public Long getAssignedVehicleId() { return assignedVehicleId; }
    public void setAssignedVehicleId(Long assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }

    public LocalDateTime getScheduledStartTime() { return scheduledStartTime; }
    public void setScheduledStartTime(LocalDateTime scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }

    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }

    public boolean isEmergency() { return emergency; }
    public void setEmergency(boolean emergency) { this.emergency = emergency; }
}
