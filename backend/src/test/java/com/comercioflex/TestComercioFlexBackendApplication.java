package com.comercioflex;

import org.springframework.boot.SpringApplication;

public class TestComercioFlexBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ComercioFlexBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
