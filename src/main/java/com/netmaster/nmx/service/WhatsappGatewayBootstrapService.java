package com.netmaster.nmx.service;

import com.netmaster.nmx.config.WhatsappGatewayBootstrapProperties;
import com.netmaster.nmx.dto.WhatsappGatewayBootstrapStatusData;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappGatewayBootstrapService {

    private final WhatsappGatewayBootstrapProperties properties;

    private final ExecutorService installExecutor = Executors.newSingleThreadExecutor(new GatewayThreadFactory());

    private volatile Process gatewayProcess;
    private volatile Future<?> installTask;
    private volatile String installState = "idle";
    private volatile String installMessage = "Gateway WhatsApp belum dianalisis.";
    private volatile LocalDateTime installUpdatedAt;

    public WhatsappGatewayBootstrapStatusData getStatus() {
        Path gatewayDir = resolveGatewayDirectory();
        boolean sourceAvailable = gatewayDir != null;
        boolean installed = sourceAvailable && isDependencyInstalled(gatewayDir);
        boolean runtimeAvailable = resolveNodeCommand() != null;
        boolean reachable = isGatewayReachable();
        boolean installationRunning = isInstallationRunning();

        String resolvedMessage = installMessage;
        String resolvedState = installState;

        if (reachable && !installationRunning && ("idle".equalsIgnoreCase(resolvedState) || "running".equalsIgnoreCase(resolvedState))) {
            resolvedState = "success";
            resolvedMessage = "Service gateway WhatsApp aktif. Session WhatsApp bisa diinisialisasi dari tombol Hubungkan.";
        } else if (!reachable && !installationRunning && ("success".equalsIgnoreCase(resolvedState) || "running".equalsIgnoreCase(resolvedState))) {
            resolvedState = "error";
            resolvedMessage = "Gateway WhatsApp belum aktif atau prosesnya berhenti setelah bootstrap aplikasi.";
        } else if (sourceAvailable && installed && !runtimeAvailable && !installationRunning) {
            resolvedState = "error";
            resolvedMessage = "Dependency gateway sudah ada, tetapi Node.js belum terpasang atau belum masuk PATH.";
        } else if (sourceAvailable && !installed && !installationRunning && "idle".equalsIgnoreCase(resolvedState)) {
            if (resolveNpmCommand() == null) {
                resolvedState = "error";
                resolvedMessage = "Dependency gateway belum terinstal dan npm belum tersedia. Install Node.js terlebih dahulu.";
            } else {
                resolvedMessage = "Dependency gateway belum terinstal. Instalasi diperlukan sebelum dipakai.";
            }
        } else if (!sourceAvailable) {
            resolvedState = "error";
            resolvedMessage = "Folder source WhatsApp gateway tidak ditemukan di project ini.";
        }

        return new WhatsappGatewayBootstrapStatusData(
                sourceAvailable,
                installed,
                runtimeAvailable,
                reachable,
                installationRunning,
                resolvedState,
                resolvedMessage,
                gatewayDir != null ? gatewayDir.toString() : null,
                properties.isEnabled(),
                properties.isAutoInstall(),
                installUpdatedAt
        );
    }

    public synchronized WhatsappGatewayBootstrapStatusData installAndStartAsync() {
        if (isInstallationRunning()) {
            updateInstallState("running", "Instalasi gateway WhatsApp sedang berjalan di belakang layar.");
            return getStatus();
        }

        updateInstallState("running", "Menyiapkan instalasi gateway WhatsApp...");
        installTask = installExecutor.submit(() -> {
            try {
                Path gatewayDir = requireGatewayDirectory();
                ensureDependencies(gatewayDir);
                startGateway(gatewayDir);

            if (waitForGateway()) {
                updateInstallState("success", "Service gateway WhatsApp berhasil diinstal dan dijalankan.");
                return;
            }

                updateInstallState("error", "Gateway selesai diproses, tetapi belum merespons di port yang dikonfigurasi.");
            } catch (Exception ex) {
                log.warn("Instalasi/start gateway WhatsApp gagal: {}", ex.getMessage());
                updateInstallState("error", ex.getMessage());
            }
        });
        return getStatus();
    }

    public void ensureStartedOnApplicationBoot() {
        try {
            Path gatewayDir = resolveGatewayDirectory();
            if (gatewayDir == null) {
                updateInstallState("error", "Folder WhatsApp gateway tidak ditemukan.");
                return;
            }

            if (isGatewayReachable()) {
                updateInstallState("success", "Service gateway WhatsApp sudah aktif.");
                log.info("WhatsApp gateway sudah aktif di {}:{}; bootstrap dilewati.", properties.getHost(), properties.getPort());
                return;
            }

            if (properties.isAutoInstall()) {
                ensureDependencies(gatewayDir);
            }
            startGateway(gatewayDir);

            if (waitForGateway()) {
                updateInstallState("success", "Service gateway WhatsApp aktif setelah bootstrap aplikasi.");
                return;
            }

            updateInstallState("error", "WhatsApp gateway belum merespons setelah bootstrap aplikasi.");
        } catch (Exception ex) {
            updateInstallState("error", ex.getMessage());
            log.warn("Gagal bootstrap WhatsApp gateway otomatis: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        stopGatewayProcess();
        installExecutor.shutdownNow();
    }

    private Path resolveGatewayDirectory() {
        Path baseDir = Path.of("").toAbsolutePath().normalize();
        Path gatewayDir = baseDir.resolve(properties.getDirectory()).normalize();
        if (!Files.isDirectory(gatewayDir)) {
            return null;
        }
        if (!Files.exists(gatewayDir.resolve("package.json"))) {
            return null;
        }
        return gatewayDir;
    }

    private Path requireGatewayDirectory() {
        Path gatewayDir = resolveGatewayDirectory();
        if (gatewayDir == null) {
            throw new IllegalStateException("Folder source WhatsApp gateway tidak ditemukan.");
        }
        return gatewayDir;
    }

    private boolean isDependencyInstalled(Path gatewayDir) {
        Path nodeModulesDir = gatewayDir.resolve("node_modules");
        return Files.isDirectory(nodeModulesDir) && Files.exists(nodeModulesDir.resolve("whatsapp-web.js"));
    }

    private void ensureDependencies(Path gatewayDir) throws IOException, InterruptedException {
        if (!shouldInstallDependencies(gatewayDir)) {
            updateInstallState("running", "Dependency gateway sudah tersedia. Menjalankan service...");
            return;
        }

        String npmCommand = resolveNpmCommand();
        if (npmCommand == null) {
            throw new IllegalStateException("npm tidak ditemukan. Install Node.js lalu coba aktifkan BOT WA lagi.");
        }

        updateInstallState("running", "Menginstal dependency WhatsApp gateway...");
        ProcessBuilder builder = new ProcessBuilder(buildCommand(npmCommand, "install", "--no-fund", "--no-audit"));
        configureProcess(builder, gatewayDir, gatewayDir.resolve("bootstrap-install.log"));
        Process process = builder.start();
        boolean finished = process.waitFor(properties.getInstallTimeoutMinutes(), TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("npm install timeout pada WhatsApp gateway.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("npm install WhatsApp gateway gagal dengan exit code " + process.exitValue() + ".");
        }
    }

    private boolean shouldInstallDependencies(Path gatewayDir) throws IOException {
        Path nodeModulesDir = gatewayDir.resolve("node_modules");
        Path packageLock = gatewayDir.resolve("package-lock.json");
        Path packageJson = gatewayDir.resolve("package.json");

        if (!isDependencyInstalled(gatewayDir)) {
            return true;
        }
        if (Files.exists(packageLock) && Files.getLastModifiedTime(packageLock).toMillis() > Files.getLastModifiedTime(nodeModulesDir).toMillis()) {
            return true;
        }
        return Files.exists(packageJson) && Files.getLastModifiedTime(packageJson).toMillis() > Files.getLastModifiedTime(nodeModulesDir).toMillis();
    }

    private synchronized void startGateway(Path gatewayDir) throws IOException {
        if (isGatewayReachable()) {
            updateInstallState("success", "Gateway WhatsApp sudah aktif.");
            return;
        }
        if (gatewayProcess != null && gatewayProcess.isAlive()) {
            updateInstallState("running", "Gateway WhatsApp sedang dijalankan...");
            return;
        }

        String nodeCommand = resolveNodeCommand();
        if (nodeCommand == null) {
            throw new IllegalStateException("Node.js tidak ditemukan. Install Node.js atau tambahkan ke PATH sebelum menjalankan gateway WhatsApp.");
        }

        updateInstallState("running", "Menjalankan service WhatsApp gateway...");
        ProcessBuilder builder = new ProcessBuilder(buildCommand(nodeCommand, "src/server.js"));
        configureProcess(builder, gatewayDir, gatewayDir.resolve("bootstrap-start.log"));
        gatewayProcess = builder.start();
    }

    private boolean waitForGateway() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(Math.max(5, properties.getStartupWaitSeconds())));
        while (Instant.now().isBefore(deadline)) {
            if (isGatewayReachable()) {
                log.info("WhatsApp gateway aktif di {}:{}.", properties.getHost(), properties.getPort());
                return true;
            }
            Thread.sleep(1000L);
        }
        return false;
    }

    private boolean isGatewayReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()), 1000);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isInstallationRunning() {
        Future<?> currentTask = installTask;
        return currentTask != null && !currentTask.isDone();
    }

    private List<String> buildCommand(String executable, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(args));
        return command;
    }

    private String resolveNodeCommand() {
        return findExecutable(isWindows()
                ? List.of("node.exe", "node")
                : List.of("node"));
    }

    private String resolveNpmCommand() {
        return findExecutable(isWindows()
                ? List.of("npm.cmd", "npm.exe", "npm")
                : List.of("npm"));
    }

    private String findExecutable(List<String> candidates) {
        String pathValue = System.getenv("PATH");
        if (pathValue != null && !pathValue.isBlank()) {
            String[] entries = pathValue.split(java.io.File.pathSeparator);
            for (String rawEntry : entries) {
                if (rawEntry == null || rawEntry.isBlank()) {
                    continue;
                }
                String entry = rawEntry.trim().replace("\"", "");
                if (entry.contains("%")) {
                    continue;
                }
                for (String candidate : candidates) {
                    Path resolved = Path.of(entry).resolve(candidate);
                    if (Files.isRegularFile(resolved)) {
                        return resolved.toString();
                    }
                }
            }
        }

        if (isWindows()) {
            for (String baseDir : List.of(
                    "C:/Program Files/nodejs",
                    "C:/Program Files (x86)/nodejs",
                    System.getProperty("user.home", "") + "/AppData/Roaming/npm",
                    System.getProperty("user.home", "") + "/AppData/Local/Programs/nodejs"
            )) {
                if (baseDir == null || baseDir.isBlank()) {
                    continue;
                }
                for (String candidate : candidates) {
                    Path resolved = Path.of(baseDir.replace("/", java.io.File.separator)).resolve(candidate);
                    if (Files.isRegularFile(resolved)) {
                        return resolved.toString();
                    }
                }
            }
        }

        return null;
    }

    private void configureProcess(ProcessBuilder builder, Path gatewayDir, Path logFile) throws IOException {
        Files.createDirectories(logFile.getParent());
        if (!Files.exists(logFile)) {
            Files.writeString(logFile, "", StandardOpenOption.CREATE);
        }

        builder.directory(gatewayDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        String currentPath = System.getenv("PATH");
        if (currentPath != null) {
            builder.environment().put("PATH", currentPath);
        }
    }

    private synchronized void stopGatewayProcess() {
        Process process = gatewayProcess;
        if (process == null) {
            return;
        }
        if (!process.isAlive()) {
            gatewayProcess = null;
            return;
        }

        log.info("Menghentikan proses WhatsApp gateway otomatis.");
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            gatewayProcess = null;
        }
    }

    private void updateInstallState(String state, String message) {
        installState = state;
        installMessage = message;
        installUpdatedAt = LocalDateTime.now();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private static final class GatewayThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "whatsapp-gateway-bootstrap");
            thread.setDaemon(true);
            return thread;
        }
    }
}
