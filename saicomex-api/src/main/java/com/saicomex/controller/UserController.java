package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.UserDtos.CreatedUser;
import com.saicomex.dto.UserDtos.PasswordResetRequest;
import com.saicomex.dto.UserDtos.UserDetail;
import com.saicomex.dto.UserDtos.UserRequest;
import com.saicomex.dto.UserDtos.UserStatusRequest;
import com.saicomex.dto.UserDtos.UserSummary;
import com.saicomex.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SRS §36 — {@code /api/users}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResponse<UserSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(status, roleId, search, pageable);
    }

    @GetMapping("/{id}")
    public UserDetail get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDetail update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserDetail updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return userService.updateStatus(id, request);
    }

    @PostMapping("/{id}/reset-password")
    public UserDetail resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        return userService.resetPassword(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        userService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
