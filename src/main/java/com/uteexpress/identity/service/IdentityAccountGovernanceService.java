package com.uteexpress.identity.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.dto.AccountGovernanceData;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
public class IdentityAccountGovernanceService {
    private final UserRepository users;

    public IdentityAccountGovernanceService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public Page<AccountGovernanceData> search(String query, int page) {
        if (page < 0 || page > 100000 || query != null && query.length() > 120) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return users.search(term, PageRequest.of(page, 20, Sort.by("id").descending()))
                .map(IdentityAccountGovernanceService::view);
    }

    @Transactional(readOnly = true)
    public AccountGovernanceData get(Long id) { return view(require(id)); }

    @Transactional
    public AccountGovernanceData setLocked(Long id, Long expectedVersion, boolean locked) {
        if (id == null || id <= 0 || expectedVersion == null || expectedVersion < 0) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        UserEntity user = users.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(expectedVersion, user.getVersion())) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        if (locked && user.getStatus() != UserStatus.ACTIVE
                || !locked && user.getStatus() != UserStatus.LOCKED) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        user.changeLock(locked, Instant.now());
        return view(users.saveAndFlush(user));
    }

    private UserEntity require(Long id) {
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        return users.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static AccountGovernanceData view(UserEntity user) {
        return new AccountGovernanceData(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getPhone(), user.getStatus().name(), user.getVersion());
    }
}
