package com.uteexpress.account.service;

import com.uteexpress.account.dto.AvatarUpload;
import com.uteexpress.account.dto.ProfileForm;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.StoredFile;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.identity.dto.AccountProfileData;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock CurrentAccountIdProvider accountIds;
    @Mock AccountIdentityService identities;
    @Mock FileStorageService storage;
    private ProfileService profiles;

    @BeforeEach
    void setUp() {
        profiles = new ProfileService(accountIds, identities, storage);
        when(accountIds.currentAccountId()).thenReturn(Optional.of(42L));
    }

    @Test
    void resolvesOwnershipOnlyFromCurrentPrincipalForProfileUpdates() {
        ProfileForm form = new ProfileForm();
        form.setFullName("Nguyễn Văn A");
        form.setPhone("+84 900 000 000");
        when(identities.updateProfile(42L, form.getFullName(), form.getPhone()))
                .thenReturn(data(null));

        profiles.updateProfile(form);

        verify(identities).updateProfile(42L, "Nguyễn Văn A", "+84 900 000 000");
    }

    @Test
    void avatarReplacementStoresNewKeyBeforeDatabaseUpdateThenDeletesOldKey() {
        byte[] bytes = TestImages.png();
        when(storage.storeImage(eq("avatars"), any(), any()))
                .thenReturn(new StoredFile("avatars/new.png", "image/png", 2, 2));
        when(identities.replaceAvatarKey(42L, "avatars/new.png"))
                .thenReturn("avatars/old.png");

        profiles.updateAvatar(new AvatarUpload(bytes, "client.png", "image/png"));

        ArgumentCaptor<UploadContent> upload = ArgumentCaptor.forClass(UploadContent.class);
        verify(storage).storeImage(eq("avatars"), upload.capture(), any());
        assertThat(upload.getValue().bytes()).isEqualTo(bytes);
        verify(storage).delete("avatars/old.png");
    }

    @Test
    void databaseFailureDeletesNewAvatarAndPreservesOldReference() {
        when(storage.storeImage(eq("avatars"), any(), any()))
                .thenReturn(new StoredFile("avatars/new.png", "image/png", 2, 2));
        when(identities.replaceAvatarKey(42L, "avatars/new.png"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> profiles.updateAvatar(
                new AvatarUpload(TestImages.png(), "client.png", "image/png")))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("avatars/new.png");
        verify(storage, never()).delete("avatars/old.png");
    }

    @Test
    void passwordChangeDelegatesForCurrentAccountOnly() {
        profiles.changePassword("OldSecret1", "NewSecret2");
        verify(identities).changePassword(42L, "OldSecret1", "NewSecret2");
    }

    private static AccountProfileData data(String avatarKey) {
        return new AccountProfileData(42L, "profile-user", "user@example.com",
                "Nguyễn Văn A", "+84 900 000 000", avatarKey);
    }
}
