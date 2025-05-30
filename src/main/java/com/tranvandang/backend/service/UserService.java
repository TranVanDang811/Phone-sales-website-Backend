package com.tranvandang.backend.service;


import com.tranvandang.backend.constant.PredefinedRole;
import com.tranvandang.backend.dto.request.UserCreationRequest;
import com.tranvandang.backend.dto.request.UserUpdateRequest;
import com.tranvandang.backend.dto.response.UserResponse;
import com.tranvandang.backend.mapper.AddressMapper;
import com.tranvandang.backend.repository.AddressRepository;
import com.tranvandang.backend.entity.Role;
import com.tranvandang.backend.entity.User;
import com.tranvandang.backend.exception.AppException;
import com.tranvandang.backend.exception.ErrorCode;
import com.tranvandang.backend.mapper.UserMapper;
import com.tranvandang.backend.repository.RoleRepository;
import com.tranvandang.backend.repository.UserRepository;
import com.tranvandang.backend.util.UserStatus;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    private final AddressRepository addressRepository;
    private final RoleRepository roleRepository;
    UserRepository userRepository;

    UserMapper userMapper;

    AddressMapper addressMapper;

    PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(UserCreationRequest request) {

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Gán user cho mỗi address
        if (user.getAddresses() != null) {
            User finalUser = user;
            user.getAddresses().forEach(address -> address.setUser(finalUser));
        }

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);

        user.setRoles(roles);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        return userMapper.toUserResponse(user);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        // Lấy thông tin người dùng từ SecurityContextHolder
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Kiểm tra nếu người dùng đang cố gắng sửa chính họ
        if (!user.getUsername().equals(authentication.getName()) && !authentication.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AppException(ErrorCode.FORBIDDEN);  // Nếu không phải ADMIN hoặc chính mình, từ chối
        }

        // Cập nhật thông tin người dùng từ request
        userMapper.updateUser(user, request);

        // Mã hóa lại mật khẩu nếu có thay đổi mật khẩu
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }


        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changerStatus(String userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setStatus(status);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserResponse(user);
    }



    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(pageable)
                .map(userMapper::toUserResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<User> searchUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findByFirstNameContainingIgnoreCase(keyword, pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }



    public boolean isOwner(String authenticatedUsername, String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return user.getUsername().equals(authenticatedUsername);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public UserResponse changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // Kiểm tra nếu yêu cầu là thay đổi mật khẩu của chính mình, kiểm tra mật khẩu cũ
        if (user.getUsername().equals(authentication.getName())) {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new AppException(ErrorCode.INVALID_PASSWORD);
            }
        }

        // Mã hóa mật khẩu mới và lưu
        user.setPassword(passwordEncoder.encode(newPassword));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse updateRole(String userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        Set<Role> newRoles = new HashSet<>();
        newRoles.add(role);
        user.setRoles(newRoles);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public boolean isUsernameExist(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean isEmailExist(String email) {
        return userRepository.existsByEmail(email);
    }



}
