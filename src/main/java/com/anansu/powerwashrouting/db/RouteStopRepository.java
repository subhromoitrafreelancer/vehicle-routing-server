package com.anansu.powerwashrouting.db;

import com.anansu.powerwashrouting.model.Job;
import com.anansu.powerwashrouting.model.Route;
import com.anansu.powerwashrouting.model.RouteStop;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByRouteOrderBySequenceNumber(Route route);

    Optional<RouteStop> findByRouteAndJob(Route route, Job job);

    @Query("SELECT rs FROM RouteStop rs WHERE rs.route.id = :routeId " +
            "ORDER BY rs.sequenceNumber")
    List<RouteStop> findStopsByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT rs FROM RouteStop rs WHERE rs.actualArrivalTime IS NULL " +
            "AND rs.estimatedArrivalTime <= :currentTime " +
            "ORDER BY rs.estimatedArrivalTime")
    List<RouteStop> findOverdueStops(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT MAX(rs.sequenceNumber) FROM RouteStop rs WHERE rs.route.id = :routeId")
    Optional<Integer> findMaxSequenceNumberForRoute(@Param("routeId") Long routeId);
}
