package com.medora.kafka.medoraAI.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConsumerConfig {

	@Bean
	public ExponentialBackOff exponentialBackOff() {
		ExponentialBackOff backOff = new ExponentialBackOff();
		backOff.setInitialInterval(1000);
		backOff.setMultiplier(2.0);
		backOff.setMaxInterval(10000);
		return backOff;
	}
}
