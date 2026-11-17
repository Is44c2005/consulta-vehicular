package ec.udla.consulta.gateway.config;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public GatewayConfig(@Value("${cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Bean
    public RestClient sriServiceClient(RestClient.Builder builder,
                                       @Value("${services.sri.url}") String url,
                                       @Value("${services.sri.timeout}") Duration timeout) {
        return builder.clone().baseUrl(url).requestFactory(factory(timeout)).build();
    }

    @Bean
    public RestClient antServiceClient(RestClient.Builder builder,
                                       @Value("${services.ant.url}") String url,
                                       @Value("${services.ant.timeout}") Duration timeout) {
        return builder.clone().baseUrl(url).requestFactory(factory(timeout)).build();
    }

    private static SimpleClientHttpRequestFactory factory(Duration readTimeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(readTimeout);
        return f;
    }
}
