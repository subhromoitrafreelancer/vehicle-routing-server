package com.anansu.powerwashrouting.controllers;

import com.anansu.powerwashrouting.db.JobRepository;
import com.anansu.powerwashrouting.model.Job;
import com.anansu.powerwashrouting.model.JobStatus;
import com.anansu.powerwashrouting.model.ServiceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    @Autowired
    private JobRepository jobRepository;

    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) ServiceType serviceType) {

        if (status != null) {
            return ResponseEntity.ok(jobRepository.findByStatus(status));
        } else if (serviceType != null) {
            return ResponseEntity.ok(jobRepository.findByServiceType(serviceType));
        } else {
            return ResponseEntity.ok(jobRepository.findAll());
        }
    }

    @GetMapping("/unassigned")
    public ResponseEntity<List<Job>> getUnassignedJobs() {
        return ResponseEntity.ok(jobRepository.findUnassignedJobsByPriority());
    }

    @GetMapping("/emergency")
    public ResponseEntity<List<Job>> getEmergencyJobs() {
        return ResponseEntity.ok(jobRepository.findByEmergencyTrue());
    }

    @GetMapping("/estimates")
    public ResponseEntity<List<Job>> getEstimates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(jobRepository.findEstimatesForDateRange(startDate, endDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable Long id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Job> createJob(@RequestBody Job job) {
        Job savedJob = jobRepository.save(job);
        return ResponseEntity.ok(savedJob);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Job> updateJob(@PathVariable Long id, @RequestBody Job job) {
        return jobRepository.findById(id)
                .map(existingJob -> {
                    job.setId(id);
                    return ResponseEntity.ok(jobRepository.save(job));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        if (jobRepository.existsById(id)) {
            jobRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
