package com.example.pl_timetable_project.user.repository;

import com.example.pl_timetable_project.user.entity.PrivacyConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

    List<PrivacyConsent> findAllByUserIdOrderByAgreedAtDesc(UUID userId);

    Optional<PrivacyConsent> findByUserIdAndConsentTypeAndConsentVersion(
            UUID userId,
            String consentType,
            String consentVersion
    );
}
