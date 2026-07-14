package edu.eci.arsw.pixelplatform.ai.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Timeouts explicitos: sin esto, RestTemplate espera indefinidamente una
     * respuesta. Si el servicio downstream se cuelga, cada hilo que llama aca se
     * queda bloqueado para siempre, lo cual puede agotar todos los hilos
     * disponibles del servidor (fallo en cascada). connectTimeout: tiempo maximo
     * para establecer la conexion TCP. readTimeout: tiempo maximo esperando la
     * respuesta una vez establecida la conexion.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
