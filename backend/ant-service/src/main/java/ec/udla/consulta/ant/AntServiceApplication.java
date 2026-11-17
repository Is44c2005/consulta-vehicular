package ec.udla.consulta.ant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AntServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntServiceApplication.class, args);
    }
}
