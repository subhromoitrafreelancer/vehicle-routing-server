

# Solver Inputs


1. DEPOT LOCATION (Company Office/Warehouse)
This is typically configured in application.yml or injected into the solver
Example: 40.7128, -74.0060 (NYC area - adjust to your actual location)

2. WORKING HOURS CONFIGURATION  
Already configured in RouteOptimizationService:
WORK_START_TIME = 8:00 AM
WORK_END_TIME = 6:00 PM  
MAX_OVERTIME = 2 hours

3. SERVICE TYPE DEFAULTS (Already defined in ServiceType enum)
PRESSURE_WASHING: 240 minutes (4 hours)
ROOF_CLEANING: 210 minutes (3.5 hours)  
WINDOW_CLEANING: 180 minutes (3 hours)
HOUSE_WASHING: 240 minutes (4 hours)
ESTIMATE: 60 minutes (1 hour)

4. OPTIMIZATION CONSTRAINTS CONFIGURATION
These are built into the ConstraintProvider but can be adjusted:

Hard Constraints:
- Vehicle capacity must not be exceeded
- Service type compatibility (vehicle capabilities)
- Customer time windows must be respected  
- Working hours (8 AM - 8 PM max) must be respected

Medium Constraints (Business Preferences):  
- Minimize backtracking (distance threshold: 15km)
- Balance workload between vehicles

Soft Constraints (Optimization Goals):
- Minimize total travel time
- Respect customer preferred times
- Prioritize high-value jobs (emergency > high priority > quote value)
- Minimize fuel costs

5. SOLVER CONFIGURATION (Already in VehicleRoutingPlanner)
- Time limit: 2 minutes for daily optimization
- 30 seconds for emergency job insertion
- Construction heuristic + Local search phases
