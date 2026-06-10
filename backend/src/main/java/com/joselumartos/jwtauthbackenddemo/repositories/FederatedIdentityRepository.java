package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.FederatedIdentity;
import com.joselumartos.jwtauthbackenddemo.entities.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentity, Long> {

    Optional<FederatedIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
