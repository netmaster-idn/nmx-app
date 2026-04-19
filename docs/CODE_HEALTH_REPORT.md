# NMX Code Health Report

Generated from the repository state on 2026-03-10.

## Executive Summary

The current project is a monolithic Spring Boot application with useful domain coverage, but it is not yet an enterprise ISP/NOC platform. The strongest gaps are schema inconsistency, runtime query failures, hardcoded bootstrap/security data, incomplete network integrations, and controller-heavy business logic.

## Critical Findings

1. Build verification is blocked locally because the Maven wrapper command is not executing correctly in this environment and no system `mvn` is installed.
2. Sensitive values were committed in [application.properties](/e:/nmx/nmx/src/main/resources/application.properties) and default user passwords were logged in [DataInitializer.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/config/DataInitializer.java).
3. Several JPA queries were invalid for Hibernate/PostgreSQL.
   - `LIMIT` used in JPQL in [NetworkAlertRepository.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/repository/NetworkAlertRepository.java)
   - `LIMIT` used in JPQL in [DeviceMetricsRepository.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/repository/DeviceMetricsRepository.java)
   - `TIMESTAMPDIFF` used in JPQL in [NetworkAlertRepository.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/repository/NetworkAlertRepository.java)
4. Scheduling-based monitoring services existed but were not enabled because [NmxApplication.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/NmxApplication.java) lacked `@EnableScheduling`.
5. Database SQL files previously overlapped and diverged. The repository now keeps `database/nmx_unified.sql` as the authoritative schema.
6. Entity/schema drift exists.
   - [Invoice.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/model/Invoice.java) and related services still need ongoing regression coverage against the unified schema
   - [NetworkAlert.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/model/NetworkAlert.java) still deserves explicit schema verification as monitoring evolves
7. Authorization is role-based but not modular RBAC. There is no permission entity or policy layer yet.

## Major Findings

1. The package structure is flat by technical layer, not by business module.
2. Controllers own filtering, aggregation, and workflow logic that belongs in service/application layers.
3. Multiple endpoints load full tables with `findAll()` and then filter in memory.
4. Network monitoring still uses placeholder/randomized metrics in [SnmpPollingService.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/service/SnmpPollingService.java) and hardcoded dashboard data in [MonitoringController.java](/e:/nmx/nmx/src/main/java/com/netmaster/nmx/controller/MonitoringController.java).
5. Several pages are placeholders with `TODO` content.
6. `target/` build artifacts are present in the repository and should not be source-controlled.

## Missing Platform Capabilities

1. No real SNMP, SSH, MikroTik API, TR-069, or OLT vendor connector implementation.
2. No queue worker or async job framework for polling, alert delivery, discovery, or backups.
3. No unified device inventory taxonomy for vendor/model/type/profile catalogs in the running application.
4. No billing payment/transaction ledger in the Java domain model.
5. No topology graph persistence or discovery workflow integrated with the UI.
6. No JWT/API token authentication despite API endpoints being present.

## Changes Implemented In This Pass

1. Replaced committed datasource secrets with environment-driven configuration.
2. Disabled unsafe default-user seeding by default and removed password logging.
3. Added request validation support and centralized API exception handling.
4. Enabled scheduling so monitoring jobs can actually run.
5. Fixed invalid JPQL patterns by switching to repository methods/Pageable/default methods.
6. Normalized alert severity/source values to match repository filtering semantics.
7. Added an explicit target architecture document and enterprise schema baseline.

## Recommended Next Refactor Waves

1. Move from flat packages into bounded modules under `core`, `auth`, `customers`, `devices`, `monitoring`, `billing`, `alerts`, `automation`, `acs`, and `reports`.
2. Replace placeholder monitoring code with adapter-based collectors per protocol/vendor.
3. Introduce Flyway migrations and retire the overlapping SQL snapshots.
4. Split browser session auth and API auth, then add JWT/service tokens for machine access.
5. Add pagination/specifications for high-cardinality endpoints.
6. Add integration tests for repositories and service workflows against PostgreSQL.
