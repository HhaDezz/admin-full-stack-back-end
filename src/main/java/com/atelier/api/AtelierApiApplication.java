package com.atelier.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AtelierApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtelierApiApplication.class, args);
	}

}
