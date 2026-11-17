package ec.udla.consulta.gateway.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.udla.consulta.gateway.exception.ApiException;

/**
 * Llamadas a los microservicios internos. Si un microservicio responde con error, se propaga
 * su estado HTTP y el {@code detail} de su ProblemDetail hacia el frontend.
 */
@Component
public class MicroservicioClient {

    private static final Logger log = LoggerFactory.getLogger(MicroservicioClient.class);

    private final RestClient sri;
    private final RestClient ant;
    private final ObjectMapper objectMapper;

    public MicroservicioClient(@Qualifier("sriServiceClient") RestClient sri,
                               @Qualifier("antServiceClient") RestClient ant,
                               ObjectMapper objectMapper) {
        this.sri = sri;
        this.ant = ant;
        this.objectMapper = objectMapper;
    }

    public JsonNode contribuyente(String ruc) {
        return get(sri, "SRI", "/api/sri/contribuyentes/{ruc}", ruc);
    }

    public JsonNode vehiculo(String placa) {
        return get(sri, "SRI", "/api/sri/vehiculos/{placa}", placa);
    }

    public JsonNode licencia(String cedula) {
        return get(ant, "ANT", "/api/ant/licencias/{cedula}", cedula);
    }

    private JsonNode get(RestClient client, String servicio, String uri, String valor) {
        try {
            return client.get().uri(uri, valor).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw traducir(servicio, e);
        } catch (ResourceAccessException e) {
            log.warn("Servicio {} no disponible: {}", servicio, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                    "El servicio de consultas " + servicio + " no responde. Intente nuevamente en unos minutos.");
        }
    }

    private ApiException traducir(String servicio, RestClientResponseException e) {
        String title = "Error en el servicio " + servicio;
        String detail = "El servicio " + servicio + " respondió con un error (" + e.getStatusCode().value() + ").";
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            if (body != null && body.hasNonNull("detail")) {
                detail = body.get("detail").asText();
            }
            if (body != null && body.hasNonNull("title")) {
                title = body.get("title").asText();
            }
        } catch (Exception ignored) {
            // el cuerpo no era JSON: se usa el mensaje genérico
        }
        return new ApiException(e.getStatusCode(), title, detail);
    }
}
