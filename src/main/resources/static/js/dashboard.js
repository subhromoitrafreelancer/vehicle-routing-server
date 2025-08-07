class RoutingDashboard {
    constructor() {
        this.baseUrl = '/api';
        this.currentDate = new Date().toISOString().split('T')[0];
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.updateCurrentDate();
        this.loadDashboardData();
        this.setupAutoRefresh();
    }

    setupEventListeners() {
        // Date picker
        document.getElementById('route-date').addEventListener('change', (e) => {
            this.currentDate = e.target.value;
            this.loadDashboardData();
        });

        // Generate routes button
        document.getElementById('generate-routes-btn').addEventListener('click', () => {
            this.generateRoutes();
        });

        // Re-optimize button
        document.getElementById('reoptimize-btn').addEventListener('click', () => {
            this.reoptimizeRoutes();
        });

        // Emergency job form
        document.getElementById('schedule-emergency-btn').addEventListener('click', () => {
            this.scheduleEmergencyJob();
        });

        // Estimates form
        document.getElementById('schedule-estimates-btn').addEventListener('click', () => {
            this.scheduleEstimates();
        });
    }

    updateCurrentDate() {
        const now = new Date();
        document.getElementById('current-date').textContent = now.toLocaleDateString();
        document.getElementById('route-date').value = this.currentDate;
    }

    async loadDashboardData() {
        try {
            await Promise.all([
                this.loadRouteStats(),
                this.loadRoutes(),
                this.loadVehicleStatus()
            ]);
        } catch (error) {
            console.error('Error loading dashboard data:', error);
            this.showAlert('Error loading dashboard data', 'danger');
        }
    }

    async loadRouteStats() {
        try {
            const response = await fetch(`${this.baseUrl}/routing/stats?date=${this.currentDate}`);
            const stats = await response.json();

            document.getElementById('total-routes').textContent = stats.totalRoutes;
            document.getElementById('total-jobs').textContent = stats.totalStops;
            document.getElementById('total-distance').textContent = `${stats.totalDistanceKm.toFixed(1)} km`;
            document.getElementById('fuel-cost').textContent = `${stats.estimatedFuelCost.toFixed(2)}`;
        } catch (error) {
            console.error('Error loading route stats:', error);
        }
    }

    async loadRoutes() {
        try {
            const response = await fetch(`${this.baseUrl}/routing/routes?date=${this.currentDate}`);
            const routes = await response.json();

            const tbody = document.getElementById('routes-tbody');
            tbody.innerHTML = '';

            routes.forEach(route => {
                const row = this.createRouteRow(route);
                tbody.appendChild(row);
            });
        } catch (error) {
            console.error('Error loading routes:', error);
        }
    }

    createRouteRow(route) {
        const row = document.createElement('tr');

        const statusBadge = this.getStatusBadge(route.status);
        const durationHours = Math.floor(route.totalDurationMinutes / 60);
        const durationMins = route.totalDurationMinutes % 60;

        row.innerHTML = `
            <td>${route.vehicle.licensePlate}</td>
            <td>
                <span class="badge bg-info">${route.stops.length}</span>
            </td>
            <td>${durationHours}h ${durationMins}m</td>
            <td>${route.totalDistanceKm.toFixed(1)} km</td>
            <td>${statusBadge}</td>
            <td>
                <button class="btn btn-sm btn-outline-primary" onclick="dashboard.viewRoute(${route.id})">
                    <i class="fas fa-eye"></i>
                </button>
                <button class="btn btn-sm btn-outline-warning" onclick="dashboard.editRoute(${route.id})">
                    <i class="fas fa-edit"></i>
                </button>
            </td>
        `;

        return row;
    }

    getStatusBadge(status) {
        const statusClasses = {
            'PLANNED': 'bg-secondary',
            'IN_PROGRESS': 'bg-primary',
            'COMPLETED': 'bg-success',
            'CANCELLED': 'bg-danger'
        };

        return `<span class="badge ${statusClasses[status] || 'bg-secondary'}">${status}</span>`;
    }

    async loadVehicleStatus() {
        try {
            const response = await fetch(`${this.baseUrl}/vehicles/available`);
            const vehicles = await response.json();

            const container = document.getElementById('vehicle-status-list');
            container.innerHTML = '';

            vehicles.forEach(vehicle => {
                const statusDiv = this.createVehicleStatusDiv(vehicle);
                container.appendChild(statusDiv);
            });
        } catch (error) {
            console.error('Error loading vehicle status:', error);
        }
    }

    createVehicleStatusDiv(vehicle) {
        const div = document.createElement('div');
        div.className = 'vehicle-status';

        const statusClass = vehicle.available ? 'status-available' :
            vehicle.maintenanceScheduled ? 'status-maintenance' : 'status-busy';

        div.innerHTML = `
            <div>
                <strong>${vehicle.licensePlate}</strong>
                <br>
                <small>${vehicle.type}</small>
            </div>
            <div>
                <span class="status-indicator ${statusClass}"></span>
            </div>
        `;

        return div;
    }

    async generateRoutes() {
        try {
            this.showLoading('Generating optimized routes...');

            const response = await fetch(`${this.baseUrl}/routing/generate-routes?date=${this.currentDate}`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showAlert('Routes generated successfully!', 'success');
                this.loadDashboardData();
            } else {
                throw new Error('Failed to generate routes');
            }
        } catch (error) {
            console.error('Error generating routes:', error);
            this.showAlert('Error generating routes', 'danger');
        } finally {
            this.hideLoading();
        }
    }

    async reoptimizeRoutes() {
        try {
            this.showLoading('Re-optimizing routes...');

            // Get all vehicle IDs for the current date
            const routesResponse = await fetch(`${this.baseUrl}/routing/routes?date=${this.currentDate}`);
            const routes = await routesResponse.json();
            const vehicleIds = routes.map(route => route.vehicle.id);

            if (vehicleIds.length === 0) {
                this.showAlert('No routes found for re-optimization', 'warning');
                return;
            }

            const response = await fetch(`${this.baseUrl}/routing/reoptimize-routes?date=${this.currentDate}&vehicleIds=${vehicleIds.join(',')}`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showAlert('Routes re-optimized successfully!', 'success');
                this.loadDashboardData();
            } else {
                throw new Error('Failed to re-optimize routes');
            }
        } catch (error) {
            console.error('Error re-optimizing routes:', error);
            this.showAlert('Error re-optimizing routes', 'danger');
        } finally {
            this.hideLoading();
        }
    }

    async scheduleEmergencyJob() {
        try {
            const formData = {
                customerId: document.getElementById('customer-id').value,
                address: document.getElementById('emergency-address').value,
                serviceType: document.getElementById('service-type').value,
                preferredTime: document.getElementById('preferred-time').value
            };

            const response = await fetch(`${this.baseUrl}/routing/emergency-job`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                this.showAlert('Emergency job scheduled successfully!', 'success');
                document.getElementById('emergency-form').reset();
                bootstrap.Modal.getInstance(document.getElementById('emergency-modal')).hide();
                this.loadDashboardData();
            } else {
                throw new Error('Failed to schedule emergency job');
            }
        } catch (error) {
            console.error('Error scheduling emergency job:', error);
            this.showAlert('Error scheduling emergency job', 'danger');
        }
    }

    async scheduleEstimates() {
        try {
            const addresses = document.getElementById('addresses').value
                .split('\n')
                .filter(addr => addr.trim())
                .map(addr => addr.trim());

            if (addresses.length === 0) {
                this.showAlert('Please enter at least one address', 'warning');
                return;
            }

            const formData = {
                date: document.getElementById('estimate-date').value,
                addresses: addresses
            };

            const response = await fetch(`${this.baseUrl}/routing/schedule-estimates`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                const estimates = await response.json();
                this.showAlert(`${estimates.length} estimates scheduled successfully!`, 'success');
                document.getElementById('estimates-form').reset();
                bootstrap.Modal.getInstance(document.getElementById('estimates-modal')).hide();
                this.loadDashboardData();
            } else {
                throw new Error('Failed to schedule estimates');
            }
        } catch (error) {
            console.error('Error scheduling estimates:', error);
            this.showAlert('Error scheduling estimates', 'danger');
        }
    }

    viewRoute(routeId) {
        // Navigate to route details page
        window.location.href = `/dashboard/routes?id=${routeId}`;
    }

    editRoute(routeId) {
        // Navigate to route edit page
        window.location.href = `/dashboard/routes/edit?id=${routeId}`;
    }

    showAlert(message, type) {
        // Create and show Bootstrap alert
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
        alertDiv.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        // Insert at top of main content
        const main = document.querySelector('main');
        main.insertBefore(alertDiv, main.firstChild);

        // Auto-hide after 5 seconds
        setTimeout(() => {
            if (alertDiv.parentNode) {
                alertDiv.remove();
            }
        }, 5000);
    }

    showLoading(message) {
        // Show loading spinner
        const loadingDiv = document.createElement('div');
        loadingDiv.id = 'loading-overlay';
        loadingDiv.className = 'position-fixed top-0 start-0 w-100 h-100 d-flex justify-content-center align-items-center';
        loadingDiv.style.backgroundColor = 'rgba(0,0,0,0.5)';
        loadingDiv.style.zIndex = '9999';
        loadingDiv.innerHTML = `
            <div class="bg-white p-4 rounded text-center">
                <div class="spinner-border text-primary mb-3"></div>
                <div>${message}</div>
            </div>
        `;

        document.body.appendChild(loadingDiv);
    }

    hideLoading() {
        const loadingDiv = document.getElementById('loading-overlay');
        if (loadingDiv) {
            loadingDiv.remove();
        }
    }

    setupAutoRefresh() {
        // Refresh data every 30 seconds
        setInterval(() => {
            this.loadDashboardData();
        }, 30000);
    }
}

// Initialize dashboard when page loads
let dashboard;
document.addEventListener('DOMContentLoaded', () => {
    dashboard = new RoutingDashboard();
});
