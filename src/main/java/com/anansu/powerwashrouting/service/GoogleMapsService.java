package com.anansu.powerwashrouting.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.anansu.powerwashrouting.service.RouteOptimizationService.Location;
import com.anansu.powerwashrouting.service.RouteOptimizationService.TravelInfo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class GoogleMapsService {

    @Value("${google.maps.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final String DISTANCE_MATRIX_API_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";

    public GoogleMapsService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get travel information between two locations considering traffic
     */
    public TravelInfo getTravelInfo(Location origin, Location destination, LocalDateTime departureTime) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(DIRECTIONS_API_URL)
                    .queryParam("origin", origin.getLatitude() + "," + origin.getLongitude())
                    .queryParam("destination", destination.getLatitude() + "," + destination.getLongitude())
                    .queryParam("departure_time", departureTime.toEpochSecond(ZoneOffset.UTC))
                    .queryParam("traffic_model", "pessimistic")
                    .queryParam("key", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && "OK".equals(response.get("status"))) {
                return parseDirectionsResponse(response);
            } else {
                // Fallback to straight-line distance calculation
                return calculateFallbackTravelInfo(origin, destination);
            }
        } catch (Exception e) {
            // Log error and use fallback
            System.err.println("Error calling Google Maps API: " + e.getMessage());
            return calculateFallbackTravelInfo(origin, destination);
        }
    }

    /**
     * Get distance matrix for multiple origins and destinations
     */
    public TravelMatrix getTravelMatrix(Location[] origins, Location[] destinations, LocalDateTime departureTime) {
        try {
            String originsParam = buildLocationParam(origins);
            String destinationsParam = buildLocationParam(destinations);

            String url = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_API_URL)
                    .queryParam("origins", originsParam)
                    .queryParam("destinations", destinationsParam)
                    .queryParam("departure_time", departureTime.toEpochSecond(ZoneOffset.UTC))
                    .queryParam("traffic_model", "pessimistic")
                    .queryParam("units", "metric")
                    .queryParam("key", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && "OK".equals(response.get("status"))) {
                return parseDistanceMatrixResponse(response, origins.length, destinations.length);
            } else {
                return createFallbackMatrix(origins, destinations);
            }
        } catch (Exception e) {
            System.err.println("Error calling Google Distance Matrix API: " + e.getMessage());
            return createFallbackMatrix(origins, destinations);
        }
    }

    /**
     * Geocode an address to get coordinates
     */
    public Location geocodeAddress(String address) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", address)
                    .queryParam("key", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && "OK".equals(response.get("status"))) {
                return parseGeocodeResponse(response);
            }
        } catch (Exception e) {
            System.err.println("Error geocoding address: " + e.getMessage());
        }

        return null;
    }

    private TravelInfo parseDirectionsResponse(Map<String, Object> response) {
        try {
            Map<String, Object> route = (Map<String, Object>) ((Object[]) response.get("routes"))[0];
            Map<String, Object> leg = (Map<String, Object>) ((Object[]) route.get("legs"))[0];

            Map<String, Object> distance = (Map<String, Object>) leg.get("distance");
            Map<String, Object> duration = (Map<String, Object>) leg.get("duration_in_traffic");

            if (duration == null) {
                duration = (Map<String, Object>) leg.get("duration");
            }

            double distanceKm = ((Number) distance.get("value")).doubleValue() / 1000.0;
            int durationMinutes = ((Number) duration.get("value")).intValue() / 60;

            return new TravelInfo(distanceKm, durationMinutes);
        } catch (Exception e) {
            System.err.println("Error parsing directions response: " + e.getMessage());
            return new TravelInfo(0, 0);
        }
    }

    private TravelMatrix parseDistanceMatrixResponse(Map<String, Object> response, int numOrigins, int numDestinations) {
        TravelMatrix matrix = new TravelMatrix(numOrigins, numDestinations);

        try {
            Object[] rows = (Object[]) response.get("rows");

            for (int i = 0; i < rows.length; i++) {
                Map<String, Object> row = (Map<String, Object>) rows[i];
                Object[] elements = (Object[]) row.get("elements");

                for (int j = 0; j < elements.length; j++) {
                    Map<String, Object> element = (Map<String, Object>) elements[j];

                    if ("OK".equals(element.get("status"))) {
                        Map<String, Object> distance = (Map<String, Object>) element.get("distance");
                        Map<String, Object> duration = (Map<String, Object>) element.get("duration_in_traffic");

                        if (duration == null) {
                            duration = (Map<String, Object>) element.get("duration");
                        }

                        double distanceKm = ((Number) distance.get("value")).doubleValue() / 1000.0;
                        int durationMinutes = ((Number) duration.get("value")).intValue() / 60;

                        matrix.setTravelInfo(i, j, new TravelInfo(distanceKm, durationMinutes));
                    } else {
                        // Use fallback calculation for failed elements
                        matrix.setTravelInfo(i, j, new TravelInfo(0, 0));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing distance matrix response: " + e.getMessage());
        }

        return matrix;
    }

    private Location parseGeocodeResponse(Map<String, Object> response) {
        try {
            Object[] results = (Object[]) response.get("results");
            Map<String, Object> result = (Map<String, Object>) results[0];
            Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
            Map<String, Object> location = (Map<String, Object>) geometry.get("location");

            double lat = ((Number) location.get("lat")).doubleValue();
            double lng = ((Number) location.get("lng")).doubleValue();

            return new Location(lat, lng);
        } catch (Exception e) {
            System.err.println("Error parsing geocode response: " + e.getMessage());
            return null;
        }
    }

    private String buildLocationParam(Location[] locations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < locations.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(locations[i].getLatitude()).append(",").append(locations[i].getLongitude());
        }
        return sb.toString();
    }

    private TravelInfo calculateFallbackTravelInfo(Location origin, Location destination) {
        double distance = calculateDistance(origin, destination);
        int duration = (int) (distance / 0.5); // Assume 30 km/h average speed
        return new TravelInfo(distance, Math.max(5, duration)); // Minimum 5 minutes
    }

    private TravelMatrix createFallbackMatrix(Location[] origins, Location[] destinations) {
        TravelMatrix matrix = new TravelMatrix(origins.length, destinations.length);

        for (int i = 0; i < origins.length; i++) {
            for (int j = 0; j < destinations.length; j++) {
                TravelInfo info = calculateFallbackTravelInfo(origins[i], destinations[j]);
                matrix.setTravelInfo(i, j, info);
            }
        }

        return matrix;
    }

    private double calculateDistance(Location loc1, Location loc2) {
        final int R = 6371; // Earth's radius in km

        double latDistance = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double lonDistance = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Matrix to store travel information between multiple points
     */
    public static class TravelMatrix {
        private final TravelInfo[][] matrix;
        private final int numOrigins;
        private final int numDestinations;

        public TravelMatrix(int numOrigins, int numDestinations) {
            this.numOrigins = numOrigins;
            this.numDestinations = numDestinations;
            this.matrix = new TravelInfo[numOrigins][numDestinations];
        }

        public void setTravelInfo(int originIndex, int destinationIndex, TravelInfo travelInfo) {
            matrix[originIndex][destinationIndex] = travelInfo;
        }

        public TravelInfo getTravelInfo(int originIndex, int destinationIndex) {
            return matrix[originIndex][destinationIndex];
        }

        public int getNumOrigins() { return numOrigins; }
        public int getNumDestinations() { return numDestinations; }
    }
}
