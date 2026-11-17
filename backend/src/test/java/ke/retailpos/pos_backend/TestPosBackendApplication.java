package ke.retailpos.pos_backend;

import org.springframework.boot.SpringApplication;

public class TestPosBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(PosBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
