package ec.udla.consulta.ant.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import ec.udla.consulta.ant.config.AntProperties;
import ec.udla.consulta.ant.domain.ConsultaLicenciaResponse;
import ec.udla.consulta.ant.domain.ConsultaLicenciaResponse.Fuente;
import ec.udla.consulta.ant.domain.LicenciaCacheada;
import ec.udla.consulta.ant.domain.LicenciaInfo;
import ec.udla.consulta.ant.exception.AntRespuestaInvalidaException;
import ec.udla.consulta.ant.exception.ApiException;
import ec.udla.consulta.ant.parser.AntHtmlParser;
import ec.udla.consulta.ant.port.AntClient;
import ec.udla.consulta.ant.port.LicenciaCache;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Patrón de disponibilidad para la ANT:
 * <ol>
 *   <li><b>Cache-Aside con ventana fresca:</b> si hay un dato guardado hace menos de
 *       {@code ant.cache.fresh-ttl}, se devuelve sin llamar a la ANT.</li>
 *   <li><b>Circuit Breaker:</b> la llamada HTTP y el parseo se ejecutan dentro del breaker;
 *       una respuesta inválida cuenta como fallo.</li>
 *   <li>Solo una respuesta adecuada se guarda en caché (TTL {@code ant.cache.stale-ttl}).</li>
 *   <li><b>Fallback:</b> si la ANT falla se sirve el último dato bueno marcado como desactualizado;
 *       si no hay ninguno se responde 503.</li>
 * </ol>
 */
@Service
public class ConsultaLicenciaService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaLicenciaService.class);
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Guayaquil"));

    private final AntClient antClient;
    private final AntHtmlParser parser;
    private final LicenciaCache cache;
    private final CircuitBreaker circuitBreaker;
    private final Duration freshTtl;
    private final Clock clock;

    public ConsultaLicenciaService(AntClient antClient,
                                   AntHtmlParser parser,
                                   LicenciaCache cache,
                                   CircuitBreaker antCircuitBreaker,
                                   AntProperties props,
                                   Clock clock) {
        this.antClient = antClient;
        this.parser = parser;
        this.cache = cache;
        this.circuitBreaker = antCircuitBreaker;
        this.freshTtl = props.cache().freshTtl();
        this.clock = clock;
    }

    public ConsultaLicenciaResponse consultar(String cedula) {
        String limpia = cedula == null ? "" : cedula.trim();
        if (!limpia.matches("\\d{10}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cédula inválida",
                    "La cédula debe tener exactamente 10 dígitos numéricos.");
        }

        Instant ahora = clock.instant();
        Optional<LicenciaCacheada> enCache = cache.obtener(limpia);

        // a) Cache-Aside: dato fresco -> no se molesta a la ANT.
        if (enCache.isPresent() && esFresco(enCache.get(), ahora)) {
            LicenciaCacheada c = enCache.get();
            return new ConsultaLicenciaResponse(c.datos(), Fuente.CACHE, c.guardadoEn(), false,
                    "Dato consultado a la ANT el " + FORMATO_FECHA.format(c.guardadoEn()) + " (caché reciente).");
        }

        // b) Llamada protegida: HTTP + parseo dentro del circuit breaker.
        try {
            LicenciaInfo info = circuitBreaker.executeSupplier(
                    () -> parser.parse(limpia, antClient.consultarCitaciones(limpia)));
            // c) Solo se cachea una respuesta adecuada.
            cache.guardar(limpia, info, ahora);
            return new ConsultaLicenciaResponse(info, Fuente.ANT, ahora, false, null);
        } catch (RuntimeException e) {
            String motivo = describir(e);
            log.warn("Consulta a la ANT fallida para cédula ***{}: {}", limpia.substring(7), motivo);

            // d) Fallback a la caché desactualizada.
            if (enCache.isPresent()) {
                LicenciaCacheada c = enCache.get();
                return new ConsultaLicenciaResponse(c.datos(), Fuente.CACHE, c.guardadoEn(), true,
                        "La ANT no está disponible (" + motivo + "). Se muestra el último dato guardado el "
                                + FORMATO_FECHA.format(c.guardadoEn()) + ", que podría estar desactualizado.");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ANT no disponible",
                    "La ANT no está disponible en este momento (" + motivo
                            + ") y no hay información guardada para esta cédula. Intente nuevamente más tarde.");
        }
    }

    public CircuitBreaker.State estadoCircuito() {
        return circuitBreaker.getState();
    }

    private boolean esFresco(LicenciaCacheada c, Instant ahora) {
        return c.guardadoEn() != null && c.guardadoEn().plus(freshTtl).isAfter(ahora);
    }

    private static String describir(RuntimeException e) {
        if (e instanceof CallNotPermittedException) {
            return "circuito abierto tras fallos recientes";
        }
        if (e instanceof AntRespuestaInvalidaException) {
            return "respuesta con formato inesperado";
        }
        if (e instanceof ResourceAccessException) {
            return "tiempo de espera agotado o sin conexión";
        }
        if (e instanceof RestClientResponseException r) {
            return "respondió con error HTTP " + r.getStatusCode().value();
        }
        return "error de comunicación";
    }
}
