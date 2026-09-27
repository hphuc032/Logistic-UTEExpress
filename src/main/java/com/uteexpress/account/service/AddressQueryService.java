package com.uteexpress.account.service;

import com.uteexpress.account.dto.AddressData;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;

public interface AddressQueryService {
    List<AddressData> listOwnedAddresses(@NotNull Long userId);

    AddressData requireOwnedAddress(@NotNull Long userId, @NotNull Long addressId);

    Optional<AddressData> findDefaultAddress(@NotNull Long userId);
}
