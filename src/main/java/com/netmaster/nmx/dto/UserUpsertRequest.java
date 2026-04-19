package com.netmaster.nmx.dto;

import com.netmaster.nmx.model.User;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserUpsertRequest {
    private String employeeId;
    private String fullName;
    private String username;
    private String password;
    private String email;
    private String phone;
    private Boolean active;
    private Set<Long> roleIds = new HashSet<>();

    public User toUser() {
        User user = new User();
        user.setEmployeeId(employeeId);
        user.setFullName(fullName);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setPhone(phone);
        user.setActive(active != null ? active : true);
        return user;
    }
}
