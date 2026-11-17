package ec.udla.consulta.ant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import ec.udla.consulta.ant.config.AntConfig;
import ec.udla.consulta.ant.config.AntProperties;
import ec.udla.consulta.ant.domain.ConsultaLicenciaResponse;
import ec.udla.consulta.ant.domain.ConsultaLicenciaResponse.Fuente;
import ec.udla.consulta.ant.domain.LicenciaCacheada;
import ec.udla.consulta.ant.domain.LicenciaInfo;
import ec.udla.consulta.ant.exception.ApiException;
import ec.udla.consulta.ant.parser.AntHtmlParser;
import ec.udla.consulta.ant.port.AntClient;
import ec.udla.consulta.ant.port.LicenciaCache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class ConsultaLicenciaServiceTest {

    private static final String CEDULA = "1710034065";
    private static final Instant AHORA = Instant.parse("2026-11-17T15:00:00Z");
    private static final String HTML_OK = """
            <html><body>
              <span id="lblPuntos">28</span>
              <table>
                <tr><th>Placa</th><th>Fecha</th><th>Valor</th></tr>
                <tr><td>PBA1234</td><td>2026-01-10</td><td>$ 46,00</td></tr>
              </table>
            </body></html>
            """;

    private InMemoryCache cache;
    private FakeAnt ant;
    private CircuitBreaker circuitBreaker;
    private ConsultaLicenciaService service;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCache();
        ant = new FakeAnt();
        circuitBreaker = CircuitBreaker.of("ant-test", AntConfig.circuitBreakerConfig());
        AntProperties props = new AntProperties("http://ant.test", Duration.ofSeconds(8),
                new AntProperties.Cache(Duration.ofMinutes(5), Duration.ofDays(7)));
        service = new ConsultaLicenciaService(ant, new AntHtmlParser(), cache, circuitBreaker, props,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void antOkDevuelveDatoEnVivoYLoGuardaEnCache() {
        ant.respuesta = c -> HTML_OK;

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(r.fuente()).isEqualTo(Fuente.ANT);
        assertThat(r.desactualizado()).isFalse();
        assertThat(r.consultadoEn()).isEqualTo(AHORA);
        assertThat(r.datos().puntos()).isEqualTo(28.0);
        assertThat(r.datos().citaciones()).hasSize(1);
        assertThat(cache.datos.get(CEDULA)).isNotNull();
        assertThat(cache.datos.get(CEDULA).guardadoEn()).isEqualTo(AHORA);
    }

    @Test
    void datoFrescoNoLlamaALaAnt() {
        Instant hace2Min = AHORA.minus(Duration.ofMinutes(2));
        cache.datos.put(CEDULA, new LicenciaCacheada(info(25.0), hace2Min));

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(ant.llamadas.get()).isZero();
        assertThat(r.fuente()).isEqualTo(Fuente.CACHE);
        assertThat(r.desactualizado()).isFalse();
        assertThat(r.consultadoEn()).isEqualTo(hace2Min);
        assertThat(r.datos().puntos()).isEqualTo(25.0);
    }

    @Test
    void datoViejoSeRefrescaDesdeLaAnt() {
        cache.datos.put(CEDULA, new LicenciaCacheada(info(25.0), AHORA.minus(Duration.ofMinutes(10))));
        ant.respuesta = c -> HTML_OK;

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(ant.llamadas.get()).isEqualTo(1);
        assertThat(r.fuente()).isEqualTo(Fuente.ANT);
        assertThat(cache.datos.get(CEDULA).datos().puntos()).isEqualTo(28.0);
    }

    @Test
    void antFallaDevuelveCacheDesactualizadaConAviso() {
        Instant hace2Dias = AHORA.minus(Duration.ofDays(2));
        cache.datos.put(CEDULA, new LicenciaCacheada(info(20.0), hace2Dias));
        ant.respuesta = c -> {
            throw new ResourceAccessException("Read timed out");
        };

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(r.fuente()).isEqualTo(Fuente.CACHE);
        assertThat(r.desactualizado()).isTrue();
        assertThat(r.consultadoEn()).isEqualTo(hace2Dias);
        assertThat(r.aviso()).contains("no está disponible");
        assertThat(r.datos().puntos()).isEqualTo(20.0);
    }

    @Test
    void htmlInvalidoNoPisaLaCache() {
        LicenciaCacheada anterior = new LicenciaCacheada(info(20.0), AHORA.minus(Duration.ofHours(3)));
        cache.datos.put(CEDULA, anterior);
        ant.respuesta = c -> "<html><body><h1>Sitio en mantenimiento</h1></body></html>";

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(cache.guardados.get()).isZero();
        assertThat(cache.datos.get(CEDULA)).isEqualTo(anterior);
        assertThat(r.fuente()).isEqualTo(Fuente.CACHE);
        assertThat(r.desactualizado()).isTrue();
        assertThat(r.aviso()).contains("formato inesperado");
    }

    @Test
    void sinAntNiCacheLanza503() {
        ant.respuesta = c -> {
            throw new ResourceAccessException("Connection refused");
        };

        assertThatThrownBy(() -> service.consultar(CEDULA))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void circuitoAbiertoNoLlamaALaAnt() {
        cache.datos.put(CEDULA, new LicenciaCacheada(info(20.0), AHORA.minus(Duration.ofHours(1))));
        ant.respuesta = c -> HTML_OK;
        circuitBreaker.transitionToForcedOpenState();

        ConsultaLicenciaResponse r = service.consultar(CEDULA);

        assertThat(ant.llamadas.get()).isZero();
        assertThat(r.fuente()).isEqualTo(Fuente.CACHE);
        assertThat(r.desactualizado()).isTrue();
        assertThat(r.aviso()).contains("circuito abierto");
    }

    @Test
    void fallosRepetidosAbrenElCircuito() {
        ant.respuesta = c -> {
            throw new ResourceAccessException("timeout");
        };
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.consultar(CEDULA)).isInstanceOf(ApiException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> service.consultar(CEDULA)).isInstanceOf(ApiException.class);
        assertThat(ant.llamadas.get()).isEqualTo(5);
    }

    @Test
    void cedulaInvalidaDevuelve400() {
        assertThatThrownBy(() -> service.consultar("12345"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ant.llamadas.get()).isZero();
    }

    private static LicenciaInfo info(Double puntos) {
        return new LicenciaInfo(CEDULA, puntos, List.of(Map.of("Placa", "ABC1234")));
    }

    /** Caché en memoria para los tests (sin Redis). */
    static class InMemoryCache implements LicenciaCache {
        final Map<String, LicenciaCacheada> datos = new HashMap<>();
        final AtomicInteger guardados = new AtomicInteger();

        @Override
        public Optional<LicenciaCacheada> obtener(String cedula) {
            return Optional.ofNullable(datos.get(cedula));
        }

        @Override
        public void guardar(String cedula, LicenciaInfo info, Instant guardadoEn) {
            guardados.incrementAndGet();
            datos.put(cedula, new LicenciaCacheada(info, guardadoEn));
        }
    }

    /** ANT simulada que cuenta las llamadas. */
    static class FakeAnt implements AntClient {
        final AtomicInteger llamadas = new AtomicInteger();
        Function<String, String> respuesta = c -> {
            throw new IllegalStateException("respuesta no configurada");
        };

        @Override
        public String consultarCitaciones(String cedula) {
            llamadas.incrementAndGet();
            return respuesta.apply(cedula);
        }
    }
}
