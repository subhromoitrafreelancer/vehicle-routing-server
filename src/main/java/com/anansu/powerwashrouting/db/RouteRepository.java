package com.anansu.powerwashrouting.db;

import com.anansu.powerwashrouting.model.Route;
import com.anansu.powerwashrouting.model.RouteStatus;
import com.anansu.powerwashrouting.model.Vehicle;
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
public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByRouteDate(LocalDate routeDate);

    List<Route> findByVehicleAndRouteDate(Vehicle vehicle, LocalDate routeDate);

    Optional<Route> findByVehicleIdAndRouteDate(Long vehicleId, LocalDate routeDate);

    List<Route> findByStatus(RouteStatus status);

    @Query("SELECT r FROM Route r WHERE r.routeDate BETWEEN :startDate AND :endDate")
    List<Route> findByDateRange(@Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM Route r WHERE r.vehicle.id = :vehicleId " +
            "AND r.routeDate BETWEEN :startDate AND :endDate")
    List<Route> findByVehicleAndDateRange(@Param("vehicleId") Long vehicleId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM Route r WHERE r.status = 'IN_PROGRESS'")
    List<Route> findActiveRoutes();

    @Query("SELECT COUNT(r) FROM Route r WHERE r.routeDate = :date AND r.status != 'CANCELLED'")
    Long countRoutesForDate(@Param("date") LocalDate date);
}
