package com.netmaster.nmx.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import com.netmaster.nmx.service.CustomUserDetailsService;
import com.netmaster.nmx.security.EnsureTenantIsActive;
import com.netmaster.nmx.security.LoginRateLimitFilter;
import com.netmaster.nmx.security.RolePermissionMiddleware;
import com.netmaster.nmx.security.SuperAdminAuthMiddleware;
import com.netmaster.nmx.security.SupportReadOnlyGuardFilter;
import com.netmaster.nmx.security.TenantAuthMiddleware;
import com.netmaster.nmx.security.TenantContextMiddleware;
import com.netmaster.nmx.security.TenantScopeEnforcementFilter;
import com.netmaster.nmx.security.SuperadminDeleteFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomerDeleteConfirmationFilter customerDeleteConfirmationFilter;
    private final UserActivityLogFilter userActivityLogFilter;
    private final TenantContextMiddleware tenantContextMiddleware;
    private final SuperAdminAuthMiddleware superAdminAuthMiddleware;
    private final TenantAuthMiddleware tenantAuthMiddleware;
    private final EnsureTenantIsActive ensureTenantIsActive;
    private final TenantScopeEnforcementFilter tenantScopeEnforcementFilter;
    private final SupportReadOnlyGuardFilter supportReadOnlyGuardFilter;
    private final RolePermissionMiddleware rolePermissionMiddleware;
    private final LoginRateLimitFilter loginRateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .userDetailsService(userDetailsService)
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/register-tenant", "/superadmin/**", "/tenant/**"))
            .authorizeHttpRequests(auth -> auth
                // Public pages
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/","/login","/register","/register.html","/css/**","/js/**","/images/**", "/register-tenant").permitAll()
                // Tenant and superadmin flows use custom session middleware instead of Spring Security auth.
                .requestMatchers("/superadmin/**", "/tenant/**").permitAll()

                // Role management stays restricted to SUPER_ADMIN only.
                .requestMatchers("/role/**").hasAuthority("ROLE_SUPER_ADMIN")
                .requestMatchers("/system/tenant-approval/**").hasAuthority("ROLE_SUPER_ADMIN")
                .requestMatchers("/system/tenant-directory/**").hasAuthority("ROLE_SUPER_ADMIN")
                
                // Main application modules
                .requestMatchers("/dashboard/**", "/pelanggan/**", 
                                "/monitoring/**", "/reports/**", "/automation/**", 
                                "/system/**", "/crm/**", "/mapping/**", "/company/**", "/setting/**", "/user/**",
                                "/network/**", "/finance/**", "/ticketing/**").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SIDE_ADMIN")
                
                // Side Admin - Read and Create only (will be handled in controllers)
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.sendRedirect("/login");
                })
            )
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.sameOrigin())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .sessionManagement(session -> session
                .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
            )

            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
            );

        http.addFilterAfter(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(tenantContextMiddleware, LoginRateLimitFilter.class);
        http.addFilterAfter(superAdminAuthMiddleware, TenantContextMiddleware.class);
        http.addFilterAfter(tenantAuthMiddleware, SuperAdminAuthMiddleware.class);
        http.addFilterAfter(ensureTenantIsActive, TenantAuthMiddleware.class);
        http.addFilterAfter(tenantScopeEnforcementFilter, EnsureTenantIsActive.class);
        http.addFilterAfter(supportReadOnlyGuardFilter, TenantScopeEnforcementFilter.class);
        http.addFilterAfter(rolePermissionMiddleware, SupportReadOnlyGuardFilter.class);
        http.addFilterAfter(customerDeleteConfirmationFilter, RolePermissionMiddleware.class);
        http.addFilterAfter(userActivityLogFilter, CustomerDeleteConfirmationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<SuperadminDeleteFilter> disableLegacySuperadminDeleteFilter(SuperadminDeleteFilter filter) {
        FilterRegistrationBean<SuperadminDeleteFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CustomerDeleteConfirmationFilter> disableCustomerDeleteConfirmationFilterRegistration(CustomerDeleteConfirmationFilter filter) {
        FilterRegistrationBean<CustomerDeleteConfirmationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/register-tenant", configuration);
        return source;
    }
}
