package ec.udla.consulta.ant.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del cliente de la ANT y de la caché.
 *
 * @param baseUrl  URL base del portal web de la ANT
 * @param timeout  timeout de conexión y de lectura hacia la ANT
 * @param cache    ventanas de la caché
 */
@ConfigurationProperties(prefix = "ant")
public record AntProperties(
        @DefaultValue("https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes") String baseUrl,
        @DefaultValue("8s") Duration timeout,
        @DefaultValue Cache cache) {

    /**
     * @param freshTtl antigüedad máxima para servir la caché sin llamar a la ANT
     * @param staleTtl tiempo de vida en Redis del último dato bueno (respaldo ante caídas)
     */
    public record Cache(
            @DefaultValue("5m") Duration freshTtl,
            @DefaultValue("7d") Duration staleTtl) {
    }
}
