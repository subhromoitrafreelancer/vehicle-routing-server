package com.anansu.powerwashrouting.db;

import com.anansu.powerwashrouting.model.*;
import com.anansu.powerwashrouting.model.JobStatus;
import com.anansu.powerwashrouting.model.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByServiceType(ServiceType serviceType);

    List<Job> findByEmergencyTrue();

    @Query("SELECT j FROM Job j WHERE j.status = :status " +
            "AND j.scheduledStartTime BETWEEN :startDate AND :endDate")
    List<Job> findByStatusAndScheduledDateRange(@Param("status") JobStatus status,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT j FROM Job j WHERE j.status = 'SCHEDULED' " +
            "AND (j.assignedVehicleId IS NULL OR j.assignedVehicleId = :vehicleId) " +
            "AND (j.earliestStartTime IS NULL OR j.earliestStartTime <= :endTime) " +
            "AND (j.latestStartTime IS NULL OR j.latestStartTime >= :startTime)")
    List<Job> findUnassignedJobsForTimeRange(@Param("vehicleId") Long vehicleId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    @Query("SELECT j FROM Job j WHERE j.status = 'SCHEDULED' " +
            "AND j.assignedVehicleId IS NULL " +
            "ORDER BY j.priority DESC, j.quoteAmount DESC, j.preferredStartTime")
    List<Job> findUnassignedJobsByPriority();

    @Query("SELECT j FROM Job j WHERE j.serviceType = 'ESTIMATE' " +
            "AND j.status = 'SCHEDULED' " +
            "AND j.scheduledStartTime BETWEEN :startDate AND :endDate")
    List<Job> findEstimatesForDateRange(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT j FROM Job j WHERE j.weatherDependent = true " +
            "AND j.status = 'SCHEDULED' " +
            "AND j.scheduledStartTime BETWEEN :startDate AND :endDate")
    List<Job> findWeatherDependentJobs(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Simplified distance query using Haversine formula in JPQL (H2 compatible)
    @Query("SELECT j FROM Job j WHERE j.status = 'SCHEDULED' " +
            "AND (6371 * acos(cos(radians(:latitude)) * cos(radians(j.latitude)) * " +
            "cos(radians(j.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
            "sin(radians(j.latitude)))) <= :radiusKm " +
            "ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(j.latitude)) * " +
            "cos(radians(j.longitude) - radians(:longitude)) + sin(radians(:latitude)) * " +
            "sin(radians(j.latitude))))")
    List<Job> findJobsWithinRadius(@Param("latitude") Double latitude,
                                   @Param("longitude") Double longitude,
                                   @Param("radiusKm") Double radiusKm);

    @Query("SELECT j FROM Job j WHERE j.customerId = :customerId " +
            "AND j.recurring = true AND j.status != 'CANCELLED'")
    List<Job> findRecurringJobsForCustomer(@Param("customerId") String customerId);
}
