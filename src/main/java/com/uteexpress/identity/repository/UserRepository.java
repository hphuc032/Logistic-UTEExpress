package com.uteexpress.identity.repository;

import com.uteexpress.identity.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedUsername(String normalizedUsername);

    Optional<UserEntity> findByNormalizedEmailOrNormalizedUsername(
            String normalizedEmail, String normalizedUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.normalizedEmail = :normalizedEmail")
    Optional<UserEntity> findByNormalizedEmailForUpdate(
            @Param("normalizedEmail") String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.normalizedUsername = :normalizedUsername")
    Optional<UserEntity> findByNormalizedUsernameForUpdate(
            @Param("normalizedUsername") String normalizedUsername);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update uteexpress.users
               set token_version = token_version + 1,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             where id = :userId
            """, nativeQuery = true)
    int incrementTokenVersion(@Param("userId") Long userId);
}
