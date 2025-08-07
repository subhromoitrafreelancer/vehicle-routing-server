package com.anansu.powerwashrouting.service;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class VehicleRoutingPlanner {

    private final Solver<VehicleRoutingSolution> solver;

    public VehicleRoutingPlanner() {
        List<PhaseConfig> phaseList = List.of(
                new ConstructionHeuristicPhaseConfig(),
                // Local search phase
                new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(new UnionMoveSelectorConfig()
                                .withMoveSelectorList(
                                        // Change move: assign customer to different vehicle
                                        List.of(new ChangeMoveSelectorConfig())
                                                        /*.withSel
                                                        .withEntityConfig(null) // Use default
                                                        .withValueSelectorConfig(null) */ // Use default
                                ))
        );
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VehicleRoutingSolution.class)
                .withEntityClasses(Customer.class)
                .withConstraintProviderClass(VehicleRoutingConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofMinutes(2)) // Limit solving time to 2 minutes
                        .withBestScoreLimit("0hard/0medium/*soft"))
                // Stop if perfect hard/medium score found
                .withPhaseList(phaseList);

        SolverFactory<VehicleRoutingSolution> solverFactory = SolverFactory.create(solverConfig);
        this.solver = solverFactory.buildSolver();
    }

    public VehicleRoutingSolution solve(VehicleRoutingSolution problem) {
        // Clear any existing assignments
        problem.getCustomers().forEach(customer -> customer.setVehicle(null));
        problem.getVehicles().forEach(vehicle -> vehicle.getCustomers().clear());

        System.out.println("Starting optimization with " +
                problem.getCustomers().size() + " customers and " +
                problem.getVehicles().size() + " vehicles");

        VehicleRoutingSolution solution = solver.solve(problem);

        System.out.println("Optimization completed. Score: " + solution.getScore());
        System.out.println("Assigned customers: " + solution.getTotalAssignedCustomers());
        System.out.println("Unassigned customers: " + solution.getTotalUnassignedCustomers());

        return solution;
    }

    /**
     * Solve with a custom time limit
     */
    public VehicleRoutingSolution solve(VehicleRoutingSolution problem, Duration timeLimit) {
        SolverConfig customConfig = new SolverConfig()
                .withSolutionClass(VehicleRoutingSolution.class)
                .withEntityClasses(Customer.class)
                .withConstraintProviderClass(VehicleRoutingConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(timeLimit)
                        .withBestScoreLimit("0hard/0medium/*soft"));

        Solver<Object> customSolver = SolverFactory.create(customConfig).buildSolver();

        // Clear any existing assignments
        problem.getCustomers().forEach(customer -> customer.setVehicle(null));
        problem.getVehicles().forEach(vehicle -> vehicle.getCustomers().clear());

        return (VehicleRoutingSolution) customSolver.solve(problem);
    }

    /**
     * Quick solve for emergency insertions (30 seconds max)
     */
    public VehicleRoutingSolution quickSolve(VehicleRoutingSolution problem) {
        return solve(problem, Duration.ofSeconds(30));
    }
}

