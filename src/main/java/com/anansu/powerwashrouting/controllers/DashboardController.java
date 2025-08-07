package com.anansu.powerwashrouting.controllers;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    @GetMapping
    public String dashboard(Model model) {
        return "dashboard/index";
    }

    @GetMapping("/routes")
    public String routes(Model model) {
        return "dashboard/routes";
    }

    @GetMapping("/vehicles")
    public String vehicles(Model model) {
        return "dashboard/vehicles";
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        return "dashboard/jobs";
    }

    @GetMapping("/estimates")
    public String estimates(Model model) {
        return "dashboard/estimates";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        return "dashboard/analytics";
    }
}
