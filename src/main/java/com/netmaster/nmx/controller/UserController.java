package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.UserUpsertRequest;
import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.RoleRepository;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    // Check if current user has permission
    private boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return switch (permission) {
            case "CREATE", "UPDATE", "DELETE", "MANAGE_USERS" -> TenantRoleAccess.canManageUsers(auth);
            case "READ" -> TenantRoleAccess.canRead(auth);
            default -> false;
        };
    }

    // Get current user's permission level
    private String getCurrentUserPermissionLevel() {
        return TenantRoleAccess.permissionLevel(SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping("/list")
    public String listUsers(Model model) {
        if (!hasPermission("READ")) {
            return "redirect:/dashboard?access_denied";
        }
        
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        
        return "user/user-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        if (!hasPermission("MANAGE_USERS")) {
            return "redirect:/dashboard?access_denied";
        }
        
        User user = new User();
        List<Role> roles = roleRepository.findAll();
        
        model.addAttribute("user", user);
        model.addAttribute("roles", roles);
        model.addAttribute("isEdit", false);
        
        return "user/user-form";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute User user, @RequestParam Set<Long> roleIds) {
        if (!hasPermission("MANAGE_USERS")) {
            return "redirect:/dashboard?access_denied";
        }
        
        try {
            userService.createUser(user, roleIds);
            return "redirect:/user/list?success";
        } catch (Exception e) {
            log.error("Error saving user: ", e);
            return "redirect:/user/new?error";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        if (!hasPermission("MANAGE_USERS")) {
            return "redirect:/dashboard?access_denied";
        }
        
        Optional<User> user = userService.getUserById(id);
        if (user.isPresent()) {
            List<Role> roles = roleRepository.findAll();
            
            model.addAttribute("user", user.get());
            model.addAttribute("roles", roles);
            model.addAttribute("isEdit", true);
            
            return "user/user-form";
        }
        
        return "redirect:/user/list?not_found";
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable Long id, @ModelAttribute User user, @RequestParam Set<Long> roleIds) {
        if (!hasPermission("MANAGE_USERS")) {
            return "redirect:/dashboard?access_denied";
        }
        
        try {
            userService.updateUser(id, user, roleIds);
            return "redirect:/user/list?success";
        } catch (Exception e) {
            log.error("Error updating user: ", e);
            return "redirect:/user/edit/" + id + "?error";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return "redirect:/dashboard?access_denied";
        }
        
        try {
            userService.deleteUser(id);
            return "redirect:/user/list?success";
        } catch (Exception e) {
            log.error("Error deleting user: ", e);
            return "redirect:/user/list?error";
        }
    }

    // API endpoints for JSON responses
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<User>>> getAllUsersApi() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data user berhasil diambil", userService.getAllUsers()));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<User>> getUserApi(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.success("Data user ditemukan", user)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User tidak ditemukan")));
    }

    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<ApiResponse<User>> createUserApi(@RequestBody UserUpsertRequest request) {
        if (!hasPermission("MANAGE_USERS")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        
        try {
            User created = userService.createUser(request.toUser(), request.getRoleIds());
            return ResponseEntity.ok(ApiResponse.success("User berhasil dibuat", created));
        } catch (Exception e) {
            log.error("Error creating user: ", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<User>> updateUserApi(@PathVariable Long id, @RequestBody UserUpsertRequest request) {
        if (!hasPermission("MANAGE_USERS")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }

        try {
            User updated = userService.updateUser(id, request.toUser(), request.getRoleIds());
            return ResponseEntity.ok(ApiResponse.success("User berhasil diperbarui", updated));
        } catch (Exception e) {
            log.error("Error updating user: ", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deleteUserApi(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(ApiResponse.success("User berhasil dihapus", null));
        } catch (Exception e) {
            log.error("Error deleting user: ", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/permissions")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> getCurrentUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return ResponseEntity.ok(ApiResponse.success("Permission level aktif", getCurrentUserPermissionLevel()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Unauthorized"));
    }
}

