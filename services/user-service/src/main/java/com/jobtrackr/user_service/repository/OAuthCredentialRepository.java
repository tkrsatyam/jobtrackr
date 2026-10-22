package com.jobtrackr.user_service.repository;

import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthCredentialRepository extends JpaRepository<OAuthCredential, UUID> {

    Optional<OAuthCredential> findByUserIdAndProvider(UUID userId, User.AuthProvider provider);

    Optional<OAuthCredential> findByProviderAndProviderUserId(User.AuthProvider provider, String providerUserId);

    Optional<OAuthCredential> findByProviderAndEmail(User.AuthProvider provider, String email);

    List<OAuthCredential> findAllByProvider(User.AuthProvider provider);

    void deleteByUserIdAndProvider(UUID userId, User.AuthProvider provider);
}
