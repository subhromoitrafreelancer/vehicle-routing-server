# Exterior Cleaning Routing System

A comprehensive vehicle routing and scheduling system designed specifically for exterior cleaning businesses operating multiple trucks and specialized equipment.

## Features

### Core Functionality
- **Multi-vehicle route optimization** using OptaPlanner
- **Real-time traffic integration** with Google Maps API
- **Customer time window management**
- **Emergency job insertion**
- **Weather-based rescheduling**
- **Estimate scheduling and clustering**
- **Recurring job management**

### Business Logic
- Priority-based job scheduling (quote value + effort)
- Vehicle capacity and capability constraints
- Working hours with overtime limits (2 hours max)
- No-backtracking optimization
- Geographic clustering for efficiency
- Crew size requirements

### Integration Capabilities
- CRM API integration for approved quotes
- Google Maps for geocoding and routing
- Weather service integration
- Automated daily route generation
- Excel import/export for job tracking

## Technology Stack

- **Backend**: Spring Boot 2.7.0, Java 11
- **Optimization**: OptaPlanner 8.23.0
- **Database**: PostgreSQL with PostGIS for spatial data
- **Caching**: Redis
- **Frontend**: Bootstrap 5, JavaScript, Thymeleaf
- **External APIs**: Google Maps, Weather services
- **Build**: Maven

## Quick Start

### Prerequisites
- Java 11+
- PostgreSQL with PostGIS extension
- Redis (optional, for caching)
- Google Maps API key
- Maven 3.6+

### Installation

1. **Clone and setup database**
```bash
git clone <repository-url>
cd exterior-cleaning-routing

# Create PostgreSQL database
createdb exterior_routing
psql -d exterior_routing -c "CREATE EXTENSION postgis;"
```

2. **Configure application**
```bash
# Copy and edit application.yml
cp src/main/resources/application.yml.example src/main/resources/application.yml

# Set required environment variables
export GOOGLE_MAPS_API_KEY="your_google_maps_api_key"
export DB_USERNAME="your_db_username"
export DB_PASSWORD="your_db_password"
export CRM_API_URL="your_crm_api_endpoint"
export CRM_API_KEY="your_crm_api_key"
```

3. **Build and run**
```bash
mvn clean install
mvn spring-boot:run
```

4. **Access the application**
- Dashboard: http://localhost:8080/dashboard
- API Documentation: http://localhost:8080/swagger-ui.html

### Initial Setup

1. **Add vehicles** via the dashboard or API:
```bash
curl -X POST http://localhost:8080/api/vehicles \
  -H "Content-Type: application/json" \
  -d '{
    "licensePlate": "TRUCK001",
    "type": "TRUCK",
    "maxCrewSize": 3,
    "capabilities": ["PRESSURE_WASHING", "HOUSE_WASHING"]
  }'
```

2. **Import jobs** from your CRM or add manually through the API

3. **Generate routes** for tomorrow:
```bash
curl -X POST "http://localhost:8080/api/routing/generate-routes?date=2024-01-15"
```

## API Reference

### Route Management
- `POST /api/routing/generate-routes?date={date}` - Generate optimized routes
- `GET /api/routing/routes?date={date}` - Get routes for date
- `POST /api/routing/emergency-job` - Schedule emergency job
- `POST /api/routing/reoptimize-routes` - Re-optimize existing routes

### Vehicle Management
- `GET /api/vehicles` - List all vehicles
- `POST /api/vehicles` - Create vehicle
- `PUT /api/vehicles/{id}/availability` - Update availability

### Job Management
- `GET /api/jobs` - List jobs with filters
- `POST /api/jobs` - Create job
- `PUT /api/jobs/{id}/status` - Update job status

## Configuration

### Core Settings
```yaml
routing:
  depot:
    latitude: 40.7128    # Your company location
    longitude: -74.0060
  working-hours:
    start: "08:00"
    end: "18:00"
    max-overtime-minutes: 120
  optimization:
    solver-time-limit-minutes: 2
```

### Google Maps Integration
- Requires valid API key with Directions, Distance Matrix, and Geocoding APIs enabled
- Handles traffic data for realistic travel times
- Fallback to straight-line distance if API fails

### Weather Integration
- Automatically reschedules weather-dependent jobs
- Configurable weather thresholds
- Daily weather check at 8 PM for next day

## Deployment

### Production Deployment
1. Build production JAR: `mvn clean package -Pprod`
2. Configure production database and Redis
3. Set environment variables for API keys
4. Deploy with: `java -jar target/routing-system-1.0.0.jar`

### Docker Deployment
```bash
# Build image
docker build -t exterior-routing .

# Run with docker-compose
docker-compose up -d
```

## Monitoring and Analytics

The system provides comprehensive analytics:
- Route efficiency metrics
- Fuel cost tracking
- Job completion rates
- Vehicle utilization
- Customer satisfaction metrics

## Support and Maintenance

### Scheduled Tasks
- **6 AM Daily**: Import new jobs and generate next day routes
- **8 PM Daily**: Weather check and rescheduling
- **Weekly**: Route optimization report generation

### Troubleshooting
- Check logs in `/logs` directory
- Monitor database connections
- Verify API key quotas
- Review OptaPlanner solver statistics

## License

Proprietary software for exterior cleaning businesses. Contact for licensing terms.

## Contributing

1. Fork the repository
2. Create feature branch
3. Submit pull request with tests
4. Follow code style guidelines

For questions or support, contact the development team.
