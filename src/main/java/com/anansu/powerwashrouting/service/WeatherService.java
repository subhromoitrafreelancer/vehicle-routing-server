package com.anansu.powerwashrouting.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class WeatherService {

    public WeatherCondition getWeatherForecast(LocalDate date) {
        // Integration with weather API (OpenWeatherMap, WeatherAPI, etc.)
        // For now, return a mock weather condition
        return new WeatherCondition(false, false, 15.0, 65.0);
    }

    public static class WeatherCondition {
        private boolean raining;
        private boolean snowing;
        private double windSpeedMph;
        private double temperatureFahrenheit;

        public WeatherCondition(boolean raining, boolean snowing, double windSpeedMph, double temperatureFahrenheit) {
            this.raining = raining;
            this.snowing = snowing;
            this.windSpeedMph = windSpeedMph;
            this.temperatureFahrenheit = temperatureFahrenheit;
        }

        public boolean isRaining() { return raining; }
        public boolean isSnowing() { return snowing; }
        public double getWindSpeedMph() { return windSpeedMph; }
        public double getTemperatureFahrenheit() { return temperatureFahrenheit; }
    }
}
