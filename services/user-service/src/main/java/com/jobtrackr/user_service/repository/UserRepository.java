package com.jobtrackr.user_service.repository;

import com.jobtrackr.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderIdAndProvider(String providerId, User.AuthProvider provider);
}
