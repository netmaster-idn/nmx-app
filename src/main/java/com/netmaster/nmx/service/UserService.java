package com.netmaster.nmx.service;

import com.netmaster.nmx.model.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserService {
    
    List<User> getAllUsers();
    
    Optional<User> getUserById(Long id);
    
    Optional<User> getUserByUsername(String username);
    
    User createUser(User user, Set<Long> roleIds);
    
    User updateUser(Long id, User user, Set<Long> roleIds);
    
    void deleteUser(Long id);
    
    boolean hasPermission(String username, String requiredPermission);
    
    String getUserPermissionLevel(String username);
}

