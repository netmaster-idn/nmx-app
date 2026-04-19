package com.netmaster.nmx.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MasterRepositoryUsageGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "com", "netmaster", "nmx");

    private static final Set<String> SCANNED_AREAS = Set.of(
            "controller",
            "service",
            "security",
            "config"
    );

    private static final Set<String> ALLOWED_FILES = Set.of(
            "config/MasterDataInitializer.java",
            "config/TenantSchemaSyncStartupRunner.java",
            "controller/NavigationModelAdvice.java",
            "controller/SuperAdminApprovalPanelController.java",
            "controller/SuperAdminTenantController.java",
            "security/TenantResolver.java",
            "service/MasterAuditLogService.java",
            "service/MasterSuperAdminAuthService.java",
            "service/SuperAdminDashboardService.java",
            "service/SuperAdminTenantAccessService.java",
            "service/TenantApprovalService.java",
            "service/TenantAuthenticationService.java",
            "service/TenantConnectionManager.java",
            "service/TenantProvisioningService.java",
            "service/TenantRegistrationService.java"
    );

    @Test
    void masterRepositoriesStayOutOfOperationalTenantFlow() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isInsideScannedArea)
                    .filter(this::importsMasterRepository)
                    .map(this::toRelativePath)
                    .filter(relativePath -> !ALLOWED_FILES.contains(relativePath))
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .withFailMessage(
                        "Detected new master.repository usage outside the safe whitelist:%n%s%n%n" +
                                "If this access is truly required, add the file to the whitelist in %s after review.",
                        String.join(System.lineSeparator(), violations),
                        "MasterRepositoryUsageGuardTest"
                )
                .isEmpty();
    }

    private boolean isInsideScannedArea(Path path) {
        String relative = toRelativePath(path);
        return SCANNED_AREAS.stream().anyMatch(area -> relative.startsWith(area + "/"));
    }

    private boolean importsMasterRepository(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .anyMatch(line -> line.startsWith("import com.netmaster.nmx.master.repository."));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read source file: " + path, ex);
        }
    }

    private String toRelativePath(Path path) {
        return SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
    }
}
