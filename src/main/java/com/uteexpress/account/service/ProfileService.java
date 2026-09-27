package com.uteexpress.account.service;

import com.uteexpress.account.dto.AvatarUpload;
import com.uteexpress.account.dto.ProfileForm;
import com.uteexpress.account.dto.ProfileView;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.ImageStoragePolicy;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.common.storage.StoredFile;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.identity.dto.AccountProfileData;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class ProfileService {
    public static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;
    public static final int AVATAR_MAX_DIMENSION = 4096;
    private static final ImageStoragePolicy AVATAR_POLICY =
            new ImageStoragePolicy(AVATAR_MAX_BYTES, AVATAR_MAX_DIMENSION, AVATAR_MAX_DIMENSION);

    private final CurrentAccountIdProvider accountIds;
    private final AccountIdentityService identities;
    private final FileStorageService storage;

    public ProfileService(CurrentAccountIdProvider accountIds, AccountIdentityService identities,
            FileStorageService storage) {
        this.accountIds = accountIds;
        this.identities = identities;
        this.storage = storage;
    }

    public ProfileView currentProfile() {
        return view(identities.getProfile(ownerId()));
    }

    public ProfileView updateProfile(ProfileForm form) {
        return view(identities.updateProfile(ownerId(), form.getFullName(), form.getPhone()));
    }

    public void updateAvatar(AvatarUpload avatar) {
        StoredFile stored = storage.storeImage("avatars",
                new UploadContent(avatar.bytes(), avatar.originalFilename(), avatar.contentType()),
                AVATAR_POLICY);
        final String previous;
        try {
            previous = identities.replaceAvatarKey(ownerId(), stored.key());
        } catch (RuntimeException exception) {
            storage.delete(stored.key());
            throw exception;
        }
        storage.delete(previous);
    }

    public Optional<StoredContent> currentAvatar() {
        AccountProfileData profile = identities.getProfile(ownerId());
        if (profile.avatarKey() == null || profile.avatarKey().isBlank()) return Optional.empty();
        return Optional.of(storage.read(profile.avatarKey()));
    }

    public void changePassword(String currentPassword, String newPassword) {
        identities.changePassword(ownerId(), currentPassword, newPassword);
    }

    private Long ownerId() {
        return accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static ProfileView view(AccountProfileData profile) {
        return new ProfileView(profile.username(), profile.email(), profile.fullName(), profile.phone(),
                profile.avatarKey() != null && !profile.avatarKey().isBlank());
    }
}
