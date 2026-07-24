package com.example.pl_timetable_project.user.repository;

import com.example.pl_timetable_project.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByPrimaryEmailIgnoreCase(String primaryEmail);
}
