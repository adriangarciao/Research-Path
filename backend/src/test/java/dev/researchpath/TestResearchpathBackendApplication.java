package dev.researchpath;

import org.springframework.boot.SpringApplication;

public class TestResearchpathBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ResearchpathBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
