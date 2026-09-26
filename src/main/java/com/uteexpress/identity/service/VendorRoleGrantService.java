package com.uteexpress.identity.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.entity.UserEntity;
import com.uteexpress.identity.entity.UserStatus;
import com.uteexpress.identity.repository.UserRepository;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorRoleGrantService {
    private final UserRepository users;
    private final UserRoleRepository roles;

    public VendorRoleGrantService(UserRepository users, UserRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    /** Must be called from the shop approval transaction so role, token and shop move together. */
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantToActiveOwner(Long ownerId) {
        UserEntity owner = users.findById(ownerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONFLICT));
        if (owner.getStatus() != UserStatus.ACTIVE) throw new ApplicationException(ErrorCode.CONFLICT);
        roles.assignVendorIfAbsent(ownerId);
        // The previous JWT is invalidated; the owner signs in again for VENDOR authority.
        if (users.incrementTokenVersion(ownerId) != 1) throw new ApplicationException(ErrorCode.CONFLICT);
    }
}
