package com.uteexpress.account.service;

import com.uteexpress.account.dto.AddressData;
import com.uteexpress.account.dto.AddressForm;
import com.uteexpress.account.dto.AddressView;
import com.uteexpress.account.entity.Address;
import com.uteexpress.account.repository.AddressRepository;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Validated
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class AddressService implements AddressQueryService {
    private final AddressRepository addresses;
    private final CurrentAccountIdProvider accountIds;
    private final AccountIdentityService identities;
    private final Clock clock;

    public AddressService(AddressRepository addresses, CurrentAccountIdProvider accountIds,
            AccountIdentityService identities, Clock clock) {
        this.addresses = addresses;
        this.accountIds = accountIds;
        this.identities = identities;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AddressView> listCurrentAddresses() {
        return addresses.findAllByUserIdOrderByDefaultAddressDescIdAsc(ownerId()).stream()
                .map(AddressService::view)
                .toList();
    }

    @Transactional
    public AddressView create(@NotNull @Valid AddressForm form) {
        Long userId = ownerId();
        lockAccount(userId);
        boolean firstAddress = addresses.countByUserId(userId) == 0;
        Address address = Address.create(userId, form.getReceiverName(), form.getPhone(),
                form.getProvinceCode(), form.getDistrict(), form.getDetail(), firstAddress, now());
        return view(addresses.saveAndFlush(address));
    }

    @Transactional
    public AddressView update(@NotNull Long addressId, @NotNull @Valid AddressForm form) {
        Long userId = ownerId();
        lockAccount(userId);
        Address address = owned(addressId, userId);
        address.updateDetails(form.getReceiverName(), form.getPhone(), form.getProvinceCode(),
                form.getDistrict(), form.getDetail(), now());
        return view(addresses.saveAndFlush(address));
    }

    @Transactional
    public void delete(@NotNull Long addressId) {
        Long userId = ownerId();
        lockAccount(userId);
        Address address = owned(addressId, userId);
        boolean deletedDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (deletedDefault) {
            addresses.findFirstByUserIdOrderByIdAsc(userId).ifPresent(replacement -> {
                replacement.makeDefault(now());
                addresses.saveAndFlush(replacement);
            });
        }
    }

    @Transactional
    public AddressView setDefault(@NotNull Long addressId) {
        Long userId = ownerId();
        lockAccount(userId);
        Address target = owned(addressId, userId);
        if (target.isDefaultAddress()) {
            return view(target);
        }
        Instant changedAt = now();
        addresses.clearDefaultForUser(userId, changedAt);
        target = owned(addressId, userId);
        target.makeDefault(changedAt);
        return view(addresses.saveAndFlush(target));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressData> listOwnedAddresses(Long userId) {
        return addresses.findAllByUserIdOrderByDefaultAddressDescIdAsc(userId).stream()
                .map(AddressService::data)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressData requireOwnedAddress(Long userId, Long addressId) {
        return data(owned(addressId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AddressData> findDefaultAddress(Long userId) {
        return addresses.findByUserIdAndDefaultAddressTrue(userId).map(AddressService::data);
    }

    private void lockAccount(Long userId) {
        identities.requireActiveAccountForUpdate(userId);
    }

    private Address owned(Long addressId, Long userId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Long ownerId() {
        return accountIds.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static AddressView view(Address address) {
        return new AddressView(address.getId(), address.getReceiverName(), address.getPhone(),
                address.getProvinceCode(), address.getDistrict(), address.getDetail(),
                address.isDefaultAddress());
    }

    private static AddressData data(Address address) {
        return new AddressData(address.getId(), address.getReceiverName(), address.getPhone(),
                address.getProvinceCode(), address.getDistrict(), address.getDetail(),
                address.isDefaultAddress());
    }
}
