package com.anansu.powerwashrouting.db;

import com.anansu.powerwashrouting.model.ServiceType;
import com.anansu.powerwashrouting.model.Vehicle;
import com.anansu.powerwashrouting.model.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByAvailableTrue();

    List<Vehicle> findByType(VehicleType type);

    @Query("SELECT v FROM Vehicle v WHERE v.available = true " +
            "AND (v.maintenanceScheduled IS NULL OR v.maintenanceScheduled > :date)")
    List<Vehicle> findAvailableVehiclesForDate(@Param("date") LocalDateTime date);

    @Query("SELECT v FROM Vehicle v JOIN v.capabilities c WHERE c IN :serviceTypes")
    List<Vehicle> findByCapabilitiesIn(@Param("serviceTypes") Set<ServiceType> serviceTypes);

    @Query("SELECT v FROM Vehicle v WHERE v.available = true " +
            "AND v.maxCrewSize >= :requiredCrewSize " +
            "AND (v.maintenanceScheduled IS NULL OR v.maintenanceScheduled > :date)")
    List<Vehicle> findSuitableVehicles(@Param("requiredCrewSize") Integer requiredCrewSize,
                                       @Param("date") LocalDateTime date);
}
