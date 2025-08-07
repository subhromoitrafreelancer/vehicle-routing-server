-- =====================================
-- JOBS TABLE - Core Work Orders
-- =====================================
-- Purpose: Stores all work requests (services and estimates) that need to be scheduled
-- Business Logic: Central repository for customer service requests with scheduling constraints

    CREATE TABLE JOBS (
    -- Primary Identity
    ID BIGINT PRIMARY KEY,                    -- Unique job identifier
    
    -- Customer & Location Information
    CUSTOMER_ID VARCHAR(255) NOT NULL,       -- Links to external CRM customer ID
    ADDRESS VARCHAR(500) NOT NULL,           -- Full street address for service
    LATITUDE DOUBLE NOT NULL,                -- Geographic coordinate for routing
    LONGITUDE DOUBLE NOT NULL,               -- Geographic coordinate for routing
    
    -- Service Definition
    SERVICE_TYPE VARCHAR(50) NOT NULL,       -- PRESSURE_WASHING, ROOF_CLEANING, WINDOW_CLEANING, HOUSE_WASHING, ESTIMATE
    STATUS VARCHAR(50) DEFAULT 'SCHEDULED',  -- SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, RESCHEDULED
    PRIORITY VARCHAR(50) DEFAULT 'MEDIUM',   -- LOW, MEDIUM, HIGH, EMERGENCY (affects scheduling order)
    
    -- Business Value & Effort
    QUOTE_AMOUNT DECIMAL(10,2),              -- Dollar value of job (influences prioritization)
    ESTIMATED_DURATION_MINUTES INTEGER,     -- Expected service time (defaults from service type)
    REQUIRED_CREW_SIZE INTEGER DEFAULT 2,   -- Minimum crew members needed (affects vehicle selection)
    
    -- Time Constraints (Customer Requirements)
    EARLIEST_START_TIME TIMESTAMP,          -- Customer's earliest acceptable start time
    LATEST_START_TIME TIMESTAMP,            -- Customer's latest acceptable start time  
    PREFERRED_START_TIME TIMESTAMP,         -- Customer's preferred time (optimization target)
    
    -- Operational Constraints
    WEATHER_DEPENDENT BOOLEAN DEFAULT true, -- Can job be done in rain/snow? (affects rescheduling)
    EMERGENCY BOOLEAN DEFAULT false,        -- Needs immediate attention? (overrides normal scheduling)
    
    -- Recurring Service Management
    RECURRING BOOLEAN DEFAULT false,        -- Is this a repeat customer?
    RECURRING_SCHEDULE VARCHAR(100),        -- Cron expression for next occurrence
    
    -- Assignment & Tracking (Populated by Optimizer)
    ASSIGNED_VEHICLE_ID BIGINT,             -- Which vehicle will handle this job
    SCHEDULED_START_TIME TIMESTAMP,        -- When optimizer scheduled this job
    ACTUAL_START_TIME TIMESTAMP,           -- When crew actually started (real-world tracking)
    ACTUAL_END_TIME TIMESTAMP,             -- When job was completed (performance metrics)
    
    -- Indexes for Performance
    INDEX idx_jobs_status (STATUS),
    INDEX idx_jobs_service_type (SERVICE_TYPE),
    INDEX idx_jobs_scheduled_time (SCHEDULED_START_TIME),
    INDEX idx_jobs_customer (CUSTOMER_ID),
    INDEX idx_jobs_location (LATITUDE, LONGITUDE),
    INDEX idx_jobs_emergency (EMERGENCY),
    INDEX idx_jobs_assigned_vehicle (ASSIGNED_VEHICLE_ID)
);

-- Business Logic Examples:
-- 1. Priority Calculation: Emergency > High Priority > High Quote Value > Preferred Time
-- 2. Vehicle Selection: Required crew size <= Vehicle capacity + Service compatibility
-- 3. Time Windows: Earliest <= Scheduled <= Latest (hard constraint)
-- 4. Weather Logic: If weather_dependent=true and bad weather, reschedule automatically

-- =====================================
-- ROUTES TABLE - Daily Vehicle Schedules
-- =====================================
-- Purpose: Represents optimized daily route for each vehicle
-- Business Logic: One route per vehicle per day, contains summary metrics

CREATE TABLE ROUTES (
-- Primary Identity
ID BIGINT PRIMARY KEY,                   -- Unique route identifier
ROUTE_DATE DATE NOT NULL,               -- Which day this route is for

    -- Vehicle Assignment
    VEHICLE_ID BIGINT NOT NULL,             -- Which vehicle executes this route
    
    -- Route Timing
    START_TIME TIMESTAMP,                   -- When vehicle leaves depot (typically 8 AM)
    END_TIME TIMESTAMP,                     -- When vehicle returns to depot
    
    -- Performance Metrics (Calculated by Optimizer)
    TOTAL_DISTANCE_KM DOUBLE,              -- Total driving distance for the day
    TOTAL_DURATION_MINUTES INTEGER,        -- Total time away from depot
    ESTIMATED_FUEL_COST DOUBLE,            -- Projected fuel expense
    
    -- Operational Status
    STATUS VARCHAR(50) DEFAULT 'PLANNED',   -- PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
    
    -- Constraints & Relationships
    FOREIGN KEY (VEHICLE_ID) REFERENCES VEHICLES(ID),
    UNIQUE KEY unique_vehicle_date (VEHICLE_ID, ROUTE_DATE),
    
    -- Indexes for Performance  
    INDEX idx_routes_date (ROUTE_DATE),
    INDEX idx_routes_vehicle (VEHICLE_ID),
    INDEX idx_routes_status (STATUS)
    );

-- Business Logic Examples:
-- 1. One Route per Vehicle per Day: A truck can't have multiple routes on same day
-- 2. Working Hours: START_TIME >= 8:00 AM, END_TIME <= 8:00 PM (6 PM + 2hr overtime)
-- 3. Efficiency Metrics: Minimize total_distance while maximizing jobs completed
-- 4. Cost Tracking: estimated_fuel_cost = (distance / fuel_efficiency) * gas_price

-- =====================================
-- ROUTE_STOPS TABLE - Individual Job Sequences
-- =====================================  
-- Purpose: Links jobs to routes in optimal sequence, tracks execution details
-- Business Logic: Defines exact order of job execution with timing and travel data

    CREATE TABLE ROUTE_STOPS (
    -- Primary Identity
    ID BIGINT PRIMARY KEY,                   -- Unique stop identifier

    -- Relationships
    ROUTE_ID BIGINT NOT NULL,               -- Which route this stop belongs to
    JOB_ID BIGINT NOT NULL,                 -- Which job is performed at this stop
    
    -- Sequence & Order (Core Optimization Output)
    SEQUENCE_NUMBER INTEGER NOT NULL,       -- Order of execution (1st, 2nd, 3rd job of day)
    
    -- Planned Timing (Optimizer Calculations)
    ESTIMATED_ARRIVAL_TIME TIMESTAMP,      -- When vehicle should arrive at customer
    ESTIMATED_DEPARTURE_TIME TIMESTAMP,    -- When vehicle should finish and leave
    
    -- Travel Metrics (From Previous Location)
    DISTANCE_FROM_PREVIOUS_KM DOUBLE,      -- Driving distance from last stop/depot
    TRAVEL_TIME_FROM_PREVIOUS_MINUTES INTEGER, -- Driving time from last stop/depot
    
    -- Reality Tracking (Filled During Execution)
    ACTUAL_ARRIVAL_TIME TIMESTAMP,         -- When crew really arrived
    ACTUAL_DEPARTURE_TIME TIMESTAMP,       -- When crew really finished
    
    -- Constraints & Relationships
    FOREIGN KEY (ROUTE_ID) REFERENCES ROUTES(ID) ON DELETE CASCADE,
    FOREIGN KEY (JOB_ID) REFERENCES JOBS(ID),
    UNIQUE KEY unique_route_job (ROUTE_ID, JOB_ID),
    UNIQUE KEY unique_route_sequence (ROUTE_ID, SEQUENCE_NUMBER),
    
    -- Indexes for Performance
    INDEX idx_route_stops_route (ROUTE_ID),
    INDEX idx_route_stops_job (JOB_ID),
    INDEX idx_route_stops_sequence (ROUTE_ID, SEQUENCE_NUMBER)
    );

-- Business Logic Examples:
-- 1. Sequence Optimization: Minimize total travel time between stops
-- 2. No Backtracking: Avoid zig-zagging across territory (soft constraint)
-- 3. Time Accumulation: Each stop's arrival = previous departure + travel time
-- 4. Performance Analysis: Compare estimated vs actual times for improvement

-- =====================================
-- TABLE RELATIONSHIPS & WORKFLOW
-- =====================================

-- Data Flow Process:
-- 1. CRM Integration → Insert JOBS (unassigned, status='SCHEDULED')
-- 2. Route Optimizer → Create ROUTES + ROUTE_STOPS (assigns jobs to vehicles)
-- 3. Daily Execution → Update actual times in ROUTE_STOPS
-- 4. Completion → Update JOBS.status = 'COMPLETED'

-- Key Business Rules Enforced:
/*
1. CAPACITY CONSTRAINT:
   SUM(jobs.required_crew_size) per route <= vehicle.max_crew_size

2. TIME WINDOWS:
   jobs.earliest_start_time <= route_stops.estimated_arrival_time <= jobs.latest_start_time

3. SERVICE COMPATIBILITY:
   jobs.service_type IN vehicle_capabilities.capabilities

4. WORKING HOURS:
   routes.start_time >= 8:00 AM AND routes.end_time <= 8:00 PM + 2 hours overtime

5. NO DOUBLE BOOKING:
   One job can only be in one route_stop
   One vehicle can only have one route per day

6. EMERGENCY PRIORITY:
   jobs.emergency = true → Gets priority placement in routes
   */

-- Sample Queries for Business Logic:

-- Get daily schedule for a vehicle
SELECT r.route_date, v.license_plate,
rs.sequence_number, j.address, j.service_type,
rs.estimated_arrival_time, rs.estimated_departure_time
FROM routes r
JOIN vehicles v ON r.vehicle_id = v.id
JOIN route_stops rs ON r.id = rs.route_id  
JOIN jobs j ON rs.job_id = j.id
WHERE r.vehicle_id = ? AND r.route_date = ?
ORDER BY rs.sequence_number;

-- Check route efficiency metrics
SELECT r.route_date, v.license_plate,
COUNT(rs.id) as total_jobs,
r.total_distance_km,
r.total_duration_minutes / 60.0 as total_hours,
r.estimated_fuel_cost
FROM routes r
JOIN vehicles v ON r.vehicle_id = v.id  
JOIN route_stops rs ON r.id = rs.route_id
GROUP BY r.id;

-- Find unassigned high-priority jobs
SELECT j.id, j.customer_id, j.service_type, j.priority, j.quote_amount
FROM jobs j
WHERE j.status = 'SCHEDULED'
AND j.assigned_vehicle_id IS NULL
AND (j.emergency = true OR j.priority = 'HIGH')
ORDER BY j.emergency DESC, j.priority DESC, j.quote_amount DESC;
