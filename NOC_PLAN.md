# NOC Dashboard Implementation Plan

## Information Gathered:

### Current State:
1. **device-status.html**: Simple layout with basic stats (Total, Online, Offline, Maintenance) and device cards showing device name, IP/location, status, CPU, Memory
2. **noc-dashboard.css**: Comprehensive NOC dashboard styles already exist (filter bar, KPI cards, device cards, quality metrics, traffic charts, alerts panel, OLT/PON monitoring)
3. **MonitoringController.java**: Has REST APIs for network summary, device status, interface traffic, network quality, top bandwidth, OLT monitoring, alerts, traffic history
4. **Base Template**: Uses Thymeleaf fragments with Chart.js included

### Backend Models:
- MikrotikDevice: name, ipAddress, location, status, cpuLoad, memoryUsed, memoryTotal, uptimeSeconds, lastMonitored
- OltDevice: name, ipAddress, vendor, model, location, status, onuCount, opticalRxPower, temperature
- NetworkAlert: deviceName, deviceType, alertType, severity, message, value, createdAt

---

## Plan:

### File to Edit:
- **src/main/resources/templates/monitoring/device-status.html** - Main NOC dashboard page

### Implementation Steps:

#### 1. Upgrade Header with Comprehensive Summary Cards (8 KPIs)
- Total Device, Online, Offline, Maintenance, Warning, Total Traffic, Average CPU, Average Memory

#### 2. Add Filter & Search Bar
- Filter by Device Type (Router, OLT, Switch, AP, Server)
- Filter by Location/POP
- Filter by Status (Online, Offline, Maintenance)
- Search by Device Name or IP
- Auto-refresh selector (5s, 10s, 30s, Manual)

#### 3. Create Professional Device Cards (Network Device Health Card)
- Device icon by type (Router, OLT, Switch, AP)
- Device Name, Type, IP, Location
- Status badge with pulse animation
- Uptime, Latency, Last Seen
- Health metrics: CPU, Memory, Temperature, Disk
- Traffic metrics: Bandwidth IN/OUT, Sessions, Packet Loss

#### 4. Add OLT-Specific Cards
- Total ONU, Online ONU, Offline ONU
- PON Port Status
- Optical Power (RX/TX)
- Temperature, CPU, Memory

#### 5. Add Traffic Graph Section
- Real-time bandwidth chart (IN/OUT)
- Time range selector (5 min, 1 hour, 24 hours)
- Current stats display

#### 6. Add Network Alerts Panel
- Alert list with severity (CRITICAL, WARNING, INFO)
- Device name, alert type, message, timestamp
- Acknowledge/Resolve actions

#### 7. Add Device Detail Drawer/Modal
- Device full info (Name, Type, Vendor, Model, Firmware, IP, Location)
- Interface list with status and traffic

#### 8. Add Quick Action Buttons
- Ping, SSH, Reboot, View Logs, Detail

#### 9. Update JavaScript for:
- Auto-refresh functionality
- Filter/search handling
- Chart initialization
- Modal/drawer handling

---

## CSS Classes to Use (from noc-dashboard.css):
- .noc-stats-grid, .noc-stat-card
- .noc-filter-bar, .noc-filter-select
- .noc-device-grid, .noc-device-card
- .noc-chart-container
- .noc-alerts-list
- .noc-status-badge
- .noc-utilization
- .noc-live-indicator

