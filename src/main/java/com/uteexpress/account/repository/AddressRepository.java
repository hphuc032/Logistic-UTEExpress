package com.uteexpress.account.repository;

import com.uteexpress.account.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    Optional<Address> findByUserIdAndDefaultAddressTrue(Long userId);

    Optional<Address> findFirstByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Address a
               set a.defaultAddress = false,
                   a.updatedAt = :updatedAt,
                   a.version = a.version + 1
             where a.userId = :userId
               and a.defaultAddress = true
            """)
    int clearDefaultForUser(@Param("userId") Long userId, @Param("updatedAt") Instant updatedAt);
}
