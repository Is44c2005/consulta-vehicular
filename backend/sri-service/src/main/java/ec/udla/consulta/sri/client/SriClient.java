package ec.udla.consulta.sri.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ec.udla.consulta.sri.config.CacheConfig;
import ec.udla.consulta.sri.exception.ApiException;

/**
 * Cliente HTTP de los servicios públicos del SRI. Devuelve el cuerpo crudo (String) para que
 * se pueda cachear tal cual en Redis; la interpretación del JSON la hacen los servicios.
 * Devuelve {@code null} cuando el SRI responde 404 (y {@code null} nunca se cachea).
 */
@Component
public class SriClient {

    private static final Logger log = LoggerFactory.getLogger(SriClient.class);

    private final RestClient restClient;
    private final String catastroUrl;
    private final String matriculacionUrl;

    public SriClient(RestClient sriRestClient,
                     @Value("${sri.catastro-url}") String catastroUrl,
                     @Value("${sri.matriculacion-url}") String matriculacionUrl) {
        this.restClient = sriRestClient;
        this.catastroUrl = catastroUrl;
        this.matriculacionUrl = matriculacionUrl;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_EXISTE_RUC, key = "#ruc", unless = "#result == null")
    public String existePorNumeroRuc(String ruc) {
        return get(catastroUrl + "/ConsolidadoContribuyente/existePorNumeroRuc?numeroRuc={ruc}", ruc);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_CONTRIBUYENTE, key = "#ruc", unless = "#result == null")
    public String obtenerPorNumeroRuc(String ruc) {
        return get(catastroUrl + "/ConsolidadoContribuyente/obtenerPorNumerosRuc?&ruc={ruc}", ruc);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_VEHICULO, key = "#placa", unless = "#result == null")
    public String obtenerVehiculo(String placa) {
        return get(matriculacionUrl
                + "/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn={placa}", placa);
    }

    private String get(String uriTemplate, String valor) {
        try {
            String body = restClient.get().uri(uriTemplate, valor).retrieve().body(String.class);
            return (body == null || body.isBlank()) ? null : body;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            log.warn("Error al consultar el SRI ({}): {}", uriTemplate, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SRI no disponible",
                    "No fue posible obtener respuesta del SRI. Intente nuevamente en unos minutos.");
        }
    }
}
