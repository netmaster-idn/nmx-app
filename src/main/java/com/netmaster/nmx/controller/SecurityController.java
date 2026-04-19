package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.service.SuperadminDeleteGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SuperadminDeleteGuardService guardService;

    @PostMapping("/confirm-delete")
    public ResponseEntity<ApiResponse<Void>> confirmDelete(@RequestBody ConfirmDeleteRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (guardService.isPasswordValid(request.password(), auth)) {
            return ResponseEntity.ok(ApiResponse.success("Password valid", null));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Password superadmin tidak sesuai."));
    }
}

record ConfirmDeleteRequest(String password) {}
