package com.credit.card.fraud.detection.company.entity;

import com.credit.card.fraud.detection.common.entity.BaseEntity;
import com.credit.card.fraud.detection.company.dto.Industry;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 기업명

    @Column(unique = true)
    private String businessNumber; // 사업자등록번호

    @Column(unique = true)
    private String email; // 로그인 아이디

    private String password;

    private String managerName;

    @Enumerated(EnumType.STRING)
    private Industry industry;
}
