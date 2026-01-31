package com.depth.deokive.domain.oauth2.repository;

import com.depth.deokive.domain.oauth2.entity.OAuth2Account;
import com.depth.deokive.domain.oauth2.entity.enums.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OAuth2AccountRepository extends JpaRepository<OAuth2Account, Long> {

    @Query("SELECT oa FROM OAuth2Account oa JOIN FETCH oa.user " +
            "WHERE oa.providerId = :providerId AND oa.providerType = :providerType")
    Optional<OAuth2Account> findByProviderIdAndProviderType(
            @Param("providerId") String providerId,
            @Param("providerType") ProviderType providerType);

    @Modifying
    @Query("DELETE FROM OAuth2Account oa WHERE oa.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
