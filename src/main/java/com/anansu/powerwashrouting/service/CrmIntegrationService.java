package com.anansu.powerwashrouting.service;

import com.anansu.powerwashrouting.model.Job;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrmIntegrationService {
    public void updateJobStatus(Long id, String emergencyScheduled, LocalDateTime now) {

    }

    public List<Job> fetchApprovedQuotes() {
        return null;
    }

    public void createEstimateAppointment(Job estimate) {

    }
}
