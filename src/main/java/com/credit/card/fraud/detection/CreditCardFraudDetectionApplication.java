package com.credit.card.fraud.detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CreditCardFraudDetectionApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditCardFraudDetectionApplication.class, args);
	}

}
