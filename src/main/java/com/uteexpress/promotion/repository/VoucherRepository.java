package com.uteexpress.promotion.repository;

import com.uteexpress.promotion.entity.Voucher;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import com.uteexpress.promotion.dto.VoucherScope;
import org.springframework.data.jpa.repository.*;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<Voucher> findAllByShopIdAndScopeOrderByIdDesc(Long shopId, VoucherScope scope);
    Optional<Voucher> findByIdAndShopIdAndScope(Long id, Long shopId, VoucherScope scope);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id and v.shopId = :shopId and v.scope = :scope")
    Optional<Voucher> findOwnedForUpdate(Long id, Long shopId, VoucherScope scope);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.code = :code")
    Optional<Voucher> findByCodeForUpdate(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(Long id);
}
