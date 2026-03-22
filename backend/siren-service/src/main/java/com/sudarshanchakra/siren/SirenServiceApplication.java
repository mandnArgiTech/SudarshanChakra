package com.sudarshanchakra.siren;

import com.sudarshanchakra.jwt.JwtResourceServerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JwtResourceServerConfiguration.class)
public class SirenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SirenServiceApplication.class, args);
    }
}
