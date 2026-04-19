# NMX ISP Management Platform - Network Monitoring Module Implementation Plan

## Information Gathered:

### Current State Analysis:
1. **Backend (Existing):**
   - `MonitoringController.java` - Comprehensive REST APIs for network summary, device status, interface traffic, alerts, OLT monitoring
   - `NetworkAlert.java` - Alert model with severity levels, status workflow
   - `MikrotikDevice.java` - Mikrotik router model
   - `OltDevice.java` - OLT device model
   - `NetworkAlertRepository.java` - Alert repository with filtering capabilities

2. **Frontend (Existing):**
   - `device-status.html` - Professional NOC dashboard with stats cards, filters, device cards, traffic charts, alerts panel
   - `noc-dashboard.css` - Extensive NOC styling
   - Uses Chart.js for traffic graphs

3. **Sidebar Navigation:**
   - Has Monitoring dropdown with: Trafik, Device Status, Alert, Log Event

### Gaps to Fill:
1. **Missing Backend Models:** NetworkDevice (unified), DeviceMetrics
2. **Missing Backend Services:** SNMP polling, Mikrotik API service, OLT service, Ping service, Alert detection
3. **Missing Frontend Pages:** NOC Wallboard, Topology Map, Device Details Page, Customer Connection Monitoring
4. **Database Tables:** network_devices, device_metrics

---

## Implementation Plan:

### Phase 1: Database & Models
1. Create `network_devices` table SQL
2. Create `device_metrics` table SQL
3. Create `NetworkDevice.java` model
4. Create `DeviceMetrics.java` model
5. Create repositories

### Phase 2: Backend Services
1. Create `PingService.java` - ICMP ping monitoring
2. Create `SnmpPollingService.java` - SNMP polling service
3. Create `MikrotikMonitoringService.java` - RouterOS API integration
4. Create `OltMonitoringService.java` - OLT device monitoring
5. Create `AlertDetectionService.java` - Automatic alert generation

### Phase 3: REST API Extensions
1. Add network device CRUD endpoints
2. Add metrics endpoints
3. Add topology endpoints
4. Add customer connection endpoints
5. Add NOC wallboard endpoints

### Phase 4: Frontend - New Pages
1. Create `noc-wallboard.html` - Full screen NOC display
2. Create `topology.html` - Network topology map
3. Create `device-detail.html` - Device detail page
4. Create `customer-connection.html` - Customer monitoring
5. Update sidebar with new menu items

### Phase 5: Frontend - Enhancements
1. Enhance device-status.html with real data integration
2. Add WebSocket support for real-time updates
3. Add topology visualization

---

## Dependent Files to Edit:
- `database/nmx_unified.sql` - Extend the authoritative schema when new tables are needed
- `src/main/java/com/netmaster/nmx/model/` - Add new models
- `src/main/java/com/netmaster/nmx/repository/` - Add new repositories
- `src/main/java/com/netmaster/nmx/service/` - Add new services
- `src/main/java/com/netmaster/nmx/controller/MonitoringController.java` - Extend APIs
- `src/main/resources/templates/layout/sidebar.html` - Add new menu items
- Create new template files

---

## Followup Steps:
1. Run SQL to create new tables
2. Test backend services
3. Verify frontend integration
4. Test real-time updates

