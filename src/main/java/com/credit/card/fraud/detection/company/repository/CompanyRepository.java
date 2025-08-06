package com.credit.card.fraud.detection.company.repository;

import com.credit.card.fraud.detection.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByBusinessNumber(String businessNumber);

    Optional<Company> findByBusinessNumber(String businessNumber);
}