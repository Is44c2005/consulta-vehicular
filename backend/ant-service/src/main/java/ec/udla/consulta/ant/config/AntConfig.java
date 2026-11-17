package ec.udla.consulta.ant.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Configuration
public class AntConfig {

    public static final String CIRCUIT_BREAKER_ANT = "ant";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Circuit breaker programático (sin anotaciones) para la ANT:
     * ventana de 10 llamadas, mínimo 5, umbral de fallos 50 %, 30 s abierto y 2 llamadas de prueba.
     */
    @Bean
    public CircuitBreaker antCircuitBreaker() {
        return CircuitBreakerRegistry.of(circuitBreakerConfig()).circuitBreaker(CIRCUIT_BREAKER_ANT);
    }

    public static CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build();
    }

    @Bean
    public RestClient antRestClient(RestClient.Builder builder, AntProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeout());
        factory.setReadTimeout(props.timeout());
        return builder
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (consulta-vehicular)")
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                .build();
    }
}
