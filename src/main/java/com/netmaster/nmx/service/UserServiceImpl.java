package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.RoleRepository;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public User createUser(User user, Set<Long> roleIds) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new RuntimeException("Username wajib diisi");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("Password wajib diisi");
        }
        userRepository.findByUsername(user.getUsername()).ifPresent(existing -> {
            throw new RuntimeException("Username sudah digunakan");
        });

        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Set roles
        Set<Role> roles = new HashSet<>();
        for (Long roleId : roleIds) {
            Optional<Role> role = roleRepository.findById(roleId);
            role.ifPresent(roles::add);
        }
        user.setRoles(roles);
        
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUser(Long id, User user, Set<Long> roleIds) {
        Optional<User> existingUser = userRepository.findById(id);
        
        if (existingUser.isPresent()) {
            User userToUpdate = existingUser.get();

            if (user.getUsername() != null && !user.getUsername().isBlank()
                    && !user.getUsername().equals(userToUpdate.getUsername())) {
                userRepository.findByUsername(user.getUsername()).ifPresent(existing -> {
                    throw new RuntimeException("Username sudah digunakan");
                });
                userToUpdate.setUsername(user.getUsername());
            }
            
            // Update basic fields
            userToUpdate.setFullName(user.getFullName());
            userToUpdate.setEmail(user.getEmail());
            userToUpdate.setPhone(user.getPhone());
            userToUpdate.setEmployeeId(user.getEmployeeId());
            userToUpdate.setActive(user.isActive());
            
            // Update password only if provided
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                userToUpdate.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            
            // Update roles
            Set<Role> roles = new HashSet<>();
            for (Long roleId : roleIds) {
                Optional<Role> role = roleRepository.findById(roleId);
                role.ifPresent(roles::add);
            }
            userToUpdate.setRoles(roles);
            
            return userRepository.save(userToUpdate);
        }
        
        throw new RuntimeException("User not found with id: " + id);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public boolean hasPermission(String username, String requiredPermission) {
        Optional<User> user = userRepository.findByUsername(username);
        
        if (user.isPresent()) {
            String permissionLevel = getUserPermissionLevel(username);
            
            switch (requiredPermission) {
                case "CREATE":
                    return true; // All roles can create
                case "READ":
                    return true; // All roles can read
                case "UPDATE":
                    return permissionLevel.equals("FULL") || permissionLevel.equals("WRITE");
                case "DELETE":
                    return permissionLevel.equals("FULL") || permissionLevel.equals("WRITE");
                case "MANAGE_USERS":
                    return permissionLevel.equals("FULL");
                default:
                    return false;
            }
        }
        
        return false;
    }

    @Override
    public String getUserPermissionLevel(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        
        if (user.isPresent()) {
            Set<Role> roles = user.get().getRoles();
            for (Role role : roles) {
                return role.getPermissionsLevel();
            }
        }
        
        return "NONE";
    }
}

