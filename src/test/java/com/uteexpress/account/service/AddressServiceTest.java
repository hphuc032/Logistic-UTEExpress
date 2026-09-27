package com.uteexpress.account.service;

import com.uteexpress.account.dto.AddressForm;
import com.uteexpress.account.entity.Address;
import com.uteexpress.account.repository.AddressRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    private static final Long USER_ID = 41L;
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Mock AddressRepository addresses;
    @Mock CurrentAccountIdProvider accountIds;
    @Mock AccountIdentityService identities;
    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(addresses, accountIds, identities,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountIds.currentAccountId()).thenReturn(Optional.of(USER_ID));
        lenient().when(addresses.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstAddressIsDefaultAndLaterAddressIsNotClientControlled() {
        when(addresses.countByUserId(USER_ID)).thenReturn(0L, 1L);

        var first = service.create(form("First"));
        var second = service.create(form("Second"));

        assertThat(first.defaultAddress()).isTrue();
        assertThat(second.defaultAddress()).isFalse();
        verify(identities, org.mockito.Mockito.times(2)).requireActiveAccountForUpdate(USER_ID);
    }

    @Test
    void updatePreservesDefaultAndOwnership() {
        Address current = address("Before", true);
        when(addresses.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(current));

        var updated = service.update(9L, form("After"));

        assertThat(updated.receiverName()).isEqualTo("After");
        assertThat(updated.defaultAddress()).isTrue();
        assertThat(current.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void setDefaultClearsPreviousAndIsIdempotent() {
        Address target = address("Target", false);
        when(addresses.findByIdAndUserId(8L, USER_ID)).thenReturn(Optional.of(target));

        assertThat(service.setDefault(8L).defaultAddress()).isTrue();
        verify(addresses).clearDefaultForUser(USER_ID, NOW);

        assertThat(service.setDefault(8L).defaultAddress()).isTrue();
        verify(addresses, org.mockito.Mockito.times(1)).clearDefaultForUser(USER_ID, NOW);
    }

    @Test
    void deletingDefaultPromotesDeterministicLowestRemainingAddress() {
        Address deleted = address("Default", true);
        Address replacement = address("Lowest remaining", false);
        when(addresses.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(deleted));
        when(addresses.findFirstByUserIdOrderByIdAsc(USER_ID)).thenReturn(Optional.of(replacement));

        service.delete(7L);

        verify(addresses).delete(deleted);
        verify(addresses).flush();
        verify(addresses).saveAndFlush(replacement);
        assertThat(replacement.isDefaultAddress()).isTrue();
    }

    @Test
    void deletingLastAddressLeavesZeroDefaults() {
        Address only = address("Only", true);
        when(addresses.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(only));
        when(addresses.findFirstByUserIdOrderByIdAsc(USER_ID)).thenReturn(Optional.empty());

        service.delete(1L);

        verify(addresses, never()).saveAndFlush(any(Address.class));
    }

    @Test
    void crossUserIdentifiersFailClosedAndQueryContractReturnsOnlyScopedRows() {
        when(addresses.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, form("Attack")))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        Address owned = address("Owned", true);
        when(addresses.findAllByUserIdOrderByDefaultAddressDescIdAsc(USER_ID))
                .thenReturn(List.of(owned));
        assertThat(service.listOwnedAddresses(USER_ID)).singleElement()
                .satisfies(data -> assertThat(data.receiverName()).isEqualTo("Owned"));
    }

    private static AddressForm form(String receiver) {
        AddressForm form = new AddressForm();
        form.setReceiverName(receiver);
        form.setPhone("+84 900 000 000");
        form.setProvinceCode("79");
        form.setDistrict("Thủ Đức");
        form.setDetail("01 Võ Văn Ngân");
        return form;
    }

    private static Address address(String receiver, boolean defaultAddress) {
        return Address.create(USER_ID, receiver, "+84 900 000 000", "79", "Thủ Đức",
                "01 Võ Văn Ngân", defaultAddress, NOW);
    }
}
