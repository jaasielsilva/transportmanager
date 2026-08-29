package com.jaasielsilva.transportmanager.features.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenAcessoRepository extends JpaRepository<TokenAcesso, Long> {

    Optional<TokenAcesso> findByTokenHash(String tokenHash);
}
