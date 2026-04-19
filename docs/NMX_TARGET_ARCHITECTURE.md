# NMX Target Architecture

## Module Layout

The target structure should be modular by business capability, not by technical layer alone.

```text
src/main/java/com/netmaster/nmx
  core/
    config/
    exception/
    security/
    web/
  auth/
    api/
    application/
    domain/
    infrastructure/
  users/
  customers/
  services/
  devices/
  network/
  monitoring/
  alerts/
  acs/
  billing/
  automation/
  notifications/
  reports/
```

Each module should contain:

```text
module/
  api/
    controller/
    dto/
  application/
    service/
    query/
    command/
  domain/
    model/
    repository/
  infrastructure/
    persistence/
    integration/
    mapper/
```

## Cross-Cutting Rules

1. Controllers only orchestrate request/response mapping.
2. Business rules live in `application/service`.
3. JPA repositories and external clients live in `infrastructure`.
4. DTOs are not reused as entities.
5. Monitoring collectors must be adapter-based by protocol/vendor.
6. Background polling, discovery, backups, billing runs, and firmware jobs must execute through queue workers or scheduled job modules.

## Protocol Adapter Strategy

1. `monitoring.integration.snmp`
2. `monitoring.integration.icmp`
3. `devices.integration.ssh`
4. `devices.integration.mikrotik`
5. `acs.integration.genieacs`
6. `network.integration.olt.huawei`
7. `network.integration.olt.zte`
8. `network.integration.olt.fiberhome`

## Data Domains

1. `core/auth`: users, roles, permissions, audit.
2. `customers/services/billing`: customer lifecycle, plans, invoices, payments, recurring jobs.
3. `devices/network/monitoring`: inventory, interfaces, metrics, discovery, topology.
4. `alerts/notifications`: rule engine, history, routing, escalation.
5. `acs`: ONT inventory, parameters, sessions, firmware, tasks.

## Migration Plan

1. Stabilize current monolith with validation, exception handling, schema baseline, and repository fixes.
2. Introduce Flyway migrations and a single source-of-truth schema.
3. Carve out `core`, `auth`, `customers`, and `monitoring` first because they are the current highest-change areas.
4. Move remaining domains incrementally while preserving URL compatibility.
