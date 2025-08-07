package com.anansu.powerwashrouting.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_stops")
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false)
    private Integer sequenceNumber;

    private LocalDateTime estimatedArrivalTime;
    private LocalDateTime estimatedDepartureTime;
    private LocalDateTime actualArrivalTime;
    private LocalDateTime actualDepartureTime;

    private Double distanceFromPreviousKm;
    private Integer travelTimeFromPreviousMinutes;

    public RouteStop() {}

    public RouteStop(Route route, Job job, Integer sequenceNumber) {
        this.route = route;
        this.job = job;
        this.sequenceNumber = sequenceNumber;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public LocalDateTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    public void setEstimatedArrivalTime(LocalDateTime estimatedArrivalTime) {
        this.estimatedArrivalTime = estimatedArrivalTime;
    }

    public LocalDateTime getEstimatedDepartureTime() { return estimatedDepartureTime; }
    public void setEstimatedDepartureTime(LocalDateTime estimatedDepartureTime) {
        this.estimatedDepartureTime = estimatedDepartureTime;
    }

    public LocalDateTime getActualArrivalTime() { return actualArrivalTime; }
    public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
    }

    public LocalDateTime getActualDepartureTime() { return actualDepartureTime; }
    public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
        this.actualDepartureTime = actualDepartureTime;
    }

    public Double getDistanceFromPreviousKm() { return distanceFromPreviousKm; }
    public void setDistanceFromPreviousKm(Double distanceFromPreviousKm) {
        this.distanceFromPreviousKm = distanceFromPreviousKm;
    }

    public Integer getTravelTimeFromPreviousMinutes() { return travelTimeFromPreviousMinutes; }
    public void setTravelTimeFromPreviousMinutes(Integer travelTimeFromPreviousMinutes) {
        this.travelTimeFromPreviousMinutes = travelTimeFromPreviousMinutes;
    }
}
