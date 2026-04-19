package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InventoryItemRequest;
import com.netmaster.nmx.dto.InventoryTransactionView;
import com.netmaster.nmx.dto.InventoryUsageRequest;
import com.netmaster.nmx.model.InventoryItem;
import com.netmaster.nmx.model.InventoryTransaction;
import com.netmaster.nmx.model.Technician;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.InventoryItemRepository;
import com.netmaster.nmx.repository.InventoryTransactionRepository;
import com.netmaster.nmx.repository.TechnicianRepository;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nmx.inventory", name = "enabled", havingValue = "true")
public class InventoryController {

    private static final List<String> CATEGORY_ORDER = List.of(
            "Mikrotik",
            "Olt",
            "ODP",
            "Splitter Pasif",
            "Splitter Rasio",
            "ONT",
            "STB",
            "Patchcord",
            "Sleeve Protecion",
            "Lainnya"
    );

    private static final Map<String, String> CATEGORY_PREFIX = Map.of(
            "Mikrotik", "MIK",
            "Olt", "OLT",
            "ODP", "ODP",
            "Splitter Pasif", "SPF",
            "Splitter Rasio", "SRA",
            "ONT", "ONT",
            "STB", "STB",
            "Patchcord", "PTC",
            "Sleeve Protecion", "SLV",
            "Lainnya", "INV"
    );

    private final InventoryItemRepository itemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getAllItems(
            @RequestParam(required = false) String keyword
    ) {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        List<InventoryItem> items = StringUtils.hasText(keyword)
                ? itemRepository.searchByKeyword(keyword.trim())
                : itemRepository.findAllByOrderByUpdatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success("Data item berhasil diambil", items));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<InventoryItem>> getItem(@PathVariable Long id) {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        return itemRepository.findById(id)
                .map(item -> ResponseEntity.ok(ApiResponse.success("Item ditemukan", item)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Item tidak ditemukan")));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<InventoryItem>> createItem(@RequestBody InventoryItemRequest request) {
        if (!isSuperAdmin()) {
            return forbidden("Akses ditolak - Full CRUD inventory hanya untuk Super Admin");
        }

        try {
            InventoryItem item = new InventoryItem();
            applyItemChanges(item, request, true);
            InventoryItem saved = itemRepository.save(item);
            return ResponseEntity.ok(ApiResponse.success("Item berhasil dibuat", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<InventoryItem>> updateItem(
            @PathVariable Long id,
            @RequestBody InventoryItemRequest request
    ) {
        if (!isSuperAdmin()) {
            return forbidden("Akses ditolak - Full CRUD inventory hanya untuk Super Admin");
        }

        try {
            InventoryItem existing = itemRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Item tidak ditemukan"));
            applyItemChanges(existing, request, false);

            InventoryItem updated = itemRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Item berhasil diperbarui", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Long id) {
        if (!isSuperAdmin()) {
            return forbidden("Akses ditolak - Full CRUD inventory hanya untuk Super Admin");
        }

        if (!itemRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Item tidak ditemukan"));
        }
        if (transactionRepository.countByItemId(id) > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Item tidak dapat dihapus karena sudah memiliki riwayat transaksi"));
        }

        itemRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Item berhasil dihapus", null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        List<InventoryItem> allItems = itemRepository.findAll();
        List<Map<String, Object>> categoryStats = CATEGORY_ORDER.stream()
                .map(category -> buildCategoryStat(category, allItems))
                .toList();

        long activeItems = allItems.stream().filter(InventoryItem::isActive).count();
        int totalStock = allItems.stream().mapToInt(item -> safeInt(item.getCurrentStock())).sum();
        long lowStock = allItems.stream().filter(this::isLowStock).count();
        long outOfStock = allItems.stream().filter(item -> safeInt(item.getCurrentStock()) == 0).count();
        long usageTransactions = transactionRepository.countByType("USE");

        return ResponseEntity.ok(ApiResponse.success("Statistik inventory", Map.of(
                "summary", Map.of(
                        "totalItems", allItems.size(),
                        "activeItems", activeItems,
                        "totalStock", totalStock,
                        "lowStock", lowStock,
                        "outOfStock", outOfStock,
                        "usageTransactions", usageTransactions
                ),
                "categories", categoryStats
        )));
    }

    @GetMapping("/items/search")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> searchItems(@RequestParam String keyword) {
        return getAllItems(keyword);
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransactionView>>> getAllTransactions() {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        List<InventoryTransactionView> transactions = transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toTransactionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data transaksi berhasil diambil", transactions));
    }

    @GetMapping("/transactions/item/{itemId}")
    public ResponseEntity<ApiResponse<List<InventoryTransactionView>>> getTransactionsByItem(@PathVariable Long itemId) {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        List<InventoryTransactionView> transactions = transactionRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(this::toTransactionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data transaksi berhasil diambil", transactions));
    }

    @GetMapping("/technicians")
    public ResponseEntity<ApiResponse<List<Technician>>> getTechnicians() {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Data teknisi berhasil diambil",
                technicianRepository.findByIsActiveTrueOrderByNameAsc()
        ));
    }

    @PostMapping("/items/{id}/use")
    public ResponseEntity<ApiResponse<InventoryTransactionView>> useItem(
            @PathVariable Long id,
            @RequestBody InventoryUsageRequest request
    ) {
        if (!hasInventoryAccess()) {
            return forbidden("Akses ditolak");
        }

        try {
            InventoryItem item = itemRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Item tidak ditemukan"));
            Technician technician = resolveTechnician(request.getTechnicianId());
            int quantity = validateQuantity(request.getQuantity());
            int currentStock = safeInt(item.getCurrentStock());

            if (currentStock < quantity) {
                throw new IllegalArgumentException("Stok tidak mencukupi untuk pemakaian item");
            }

            item.setCurrentStock(currentStock - quantity);
            itemRepository.save(item);

            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setTransactionCode(generateTransactionCode("USE"));
            transaction.setType("USE");
            transaction.setItem(item);
            transaction.setQuantity(quantity);
            transaction.setReference(trimToNull(request.getReference()));
            transaction.setNotes(trimToNull(request.getNotes()));
            transaction.setTechnician(technician);
            transaction.setCreatedBy(getCurrentUserEntity().orElse(null));

            InventoryTransaction saved = transactionRepository.save(transaction);
            return ResponseEntity.ok(ApiResponse.success("Pemakaian item berhasil disimpan", toTransactionView(saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private void applyItemChanges(InventoryItem item, InventoryItemRequest request, boolean creating) {
        String name = trimToNull(request.getName());
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Nama item wajib diisi");
        }

        String category = normalizeCategory(request.getCategory());
        String unit = trimToNull(request.getUnit());
        if (!StringUtils.hasText(unit)) {
            throw new IllegalArgumentException("Satuan wajib diisi");
        }

        int minStock = safeNonNegative(request.getMinStock(), "Minimum stok tidak boleh negatif");
        int currentStock = safeNonNegative(request.getCurrentStock(), "Stok tidak boleh negatif");
        if (request.getPrice() != null && request.getPrice().signum() < 0) {
            throw new IllegalArgumentException("Harga tidak boleh negatif");
        }

        if (creating || !StringUtils.hasText(item.getItemCode())) {
            item.setItemCode(generateItemCode(category));
        }

        item.setName(name);
        item.setCategory(category);
        item.setDescription(trimToNull(request.getDescription()));
        item.setUnit(unit);
        item.setMinStock(minStock);
        item.setCurrentStock(currentStock);
        item.setPrice(request.getPrice());
        item.setSupplierId(request.getSupplierId());
        item.setLocation(trimToNull(request.getLocation()));
        item.setWarehouse(trimToNull(request.getWarehouse()));
        item.setActive(request.getActive() == null || request.getActive());
    }

    private Map<String, Object> buildCategoryStat(String category, List<InventoryItem> items) {
        List<InventoryItem> categoryItems = items.stream()
                .filter(item -> category.equalsIgnoreCase(item.getCategory()))
                .sorted(Comparator.comparing(InventoryItem::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        int stock = categoryItems.stream().mapToInt(item -> safeInt(item.getCurrentStock())).sum();
        long lowStock = categoryItems.stream().filter(this::isLowStock).count();

        return Map.of(
                "category", category,
                "prefix", CATEGORY_PREFIX.getOrDefault(category, "INV"),
                "itemCount", categoryItems.size(),
                "stock", stock,
                "lowStock", lowStock
        );
    }

    private InventoryTransactionView toTransactionView(InventoryTransaction transaction) {
        InventoryItem item = transaction.getItem();
        Technician technician = transaction.getTechnician();
        User createdBy = transaction.getCreatedBy();

        return InventoryTransactionView.builder()
                .id(transaction.getId())
                .transactionCode(transaction.getTransactionCode())
                .type(transaction.getType())
                .quantity(transaction.getQuantity())
                .reference(transaction.getReference())
                .notes(transaction.getNotes())
                .createdAt(transaction.getCreatedAt())
                .itemId(item != null ? item.getId() : null)
                .itemCode(item != null ? item.getItemCode() : null)
                .itemName(item != null ? item.getName() : null)
                .category(item != null ? item.getCategory() : null)
                .technicianId(technician != null ? technician.getId() : null)
                .technicianName(technician != null ? technician.getName() : null)
                .createdById(createdBy != null ? createdBy.getId() : null)
                .createdByName(createdBy != null ? createdBy.getFullName() : null)
                .build();
    }

    private boolean hasInventoryAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_SUPER_ADMIN")
                        || role.equals("ROLE_ADMIN")
                        || role.equals("ROLE_SIDE_ADMIN"));
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }

    private Optional<User> getCurrentUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            return Optional.empty();
        }
        return userRepository.findByUsername(auth.getName());
    }

    private Technician resolveTechnician(Long technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Teknisi wajib dipilih");
        }

        Technician technician = technicianRepository.findById(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Teknisi tidak ditemukan"));
        if (!Boolean.TRUE.equals(technician.getIsActive())) {
            throw new IllegalArgumentException("Teknisi tidak aktif");
        }
        return technician;
    }

    private int validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Jumlah pemakaian harus lebih besar dari 0");
        }
        return quantity;
    }

    private boolean isLowStock(InventoryItem item) {
        int minStock = safeInt(item.getMinStock());
        int currentStock = safeInt(item.getCurrentStock());
        return currentStock > 0 && minStock > 0 && currentStock <= minStock;
    }

    private int safeNonNegative(Integer value, String message) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeCategory(String rawCategory) {
        if (!StringUtils.hasText(rawCategory)) {
            throw new IllegalArgumentException("Kategori wajib dipilih");
        }

        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        return CATEGORY_ORDER.stream()
                .filter(category -> category.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Kategori inventory tidak valid"));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private synchronized String generateItemCode(String category) {
        String prefix = CATEGORY_PREFIX.getOrDefault(category, "INV");
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String basePrefix = prefix + "-" + period + "-";

        int nextSequence = itemRepository.findTopByItemCodeStartingWithOrderByItemCodeDesc(basePrefix)
                .map(InventoryItem::getItemCode)
                .map(this::extractSequence)
                .orElse(0) + 1;

        return basePrefix + String.format("%04d", nextSequence);
    }

    private synchronized String generateTransactionCode(String type) {
        String prefix = type.toUpperCase(Locale.ROOT);
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String basePrefix = prefix + "-" + period + "-";

        int nextSequence = transactionRepository.findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(basePrefix)
                .map(InventoryTransaction::getTransactionCode)
                .map(this::extractSequence)
                .orElse(0) + 1;

        return basePrefix + String.format("%04d", nextSequence);
    }

    private int extractSequence(String code) {
        if (!StringUtils.hasText(code) || !code.contains("-")) {
            return 0;
        }

        String[] parts = code.split("-");
        String lastPart = parts[parts.length - 1];
        try {
            return Integer.parseInt(lastPart);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(message));
    }
}
