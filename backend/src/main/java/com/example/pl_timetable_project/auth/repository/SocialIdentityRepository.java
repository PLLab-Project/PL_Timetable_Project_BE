package com.example.pl_timetable_project.auth.repository;

import com.example.pl_timetable_project.auth.entity.SocialIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, UUID> {

    Optional<SocialIdentity> findByProviderAndProviderSubject(
            String provider,
            String providerSubject
    );
}
