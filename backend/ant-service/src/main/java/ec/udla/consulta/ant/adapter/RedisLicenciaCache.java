package ec.udla.consulta.ant.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.udla.consulta.ant.config.AntProperties;
import ec.udla.consulta.ant.domain.LicenciaCacheada;
import ec.udla.consulta.ant.domain.LicenciaInfo;
import ec.udla.consulta.ant.port.LicenciaCache;

/**
 * Caché de licencias en Redis (p. ej. Upstash). Guarda el JSON de {@link LicenciaCacheada}
 * con TTL {@code ant.cache.stale-ttl}. Es best-effort: cualquier error se registra y se ignora.
 */
@Component
public class RedisLicenciaCache implements LicenciaCache {

    private static final Logger log = LoggerFactory.getLogger(RedisLicenciaCache.class);
    private static final String PREFIJO = "ant:licencia:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration staleTtl;

    public RedisLicenciaCache(StringRedisTemplate redis, ObjectMapper objectMapper, AntProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.staleTtl = props.cache().staleTtl();
    }

    @Override
    public Optional<LicenciaCacheada> obtener(String cedula) {
        try {
            String json = redis.opsForValue().get(PREFIJO + cedula);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, LicenciaCacheada.class));
        } catch (Exception e) {
            log.warn("No se pudo leer la caché de licencias (Redis): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void guardar(String cedula, LicenciaInfo datos, Instant guardadoEn) {
        try {
            String json = objectMapper.writeValueAsString(new LicenciaCacheada(datos, guardadoEn));
            redis.opsForValue().set(PREFIJO + cedula, json, staleTtl);
        } catch (Exception e) {
            log.warn("No se pudo guardar en la caché de licencias (Redis): {}", e.getMessage());
        }
    }
}
