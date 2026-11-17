package ec.udla.consulta.ant.adapter;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ec.udla.consulta.ant.config.AntProperties;
import ec.udla.consulta.ant.port.AntClient;

/**
 * Adaptador HTTP hacia la ANT. No captura errores: timeouts, 4xx/5xx y errores de red
 * se propagan para que el circuit breaker los cuente como fallos.
 */
@Component
public class AntHttpClient implements AntClient {

    private static final String RUTA =
            "/clp_grid_citaciones.jsp?ps_tipo_identificacion=CED&ps_identificacion={cedula}&ps_placa=";

    private final RestClient restClient;
    private final String baseUrl;

    public AntHttpClient(RestClient antRestClient, AntProperties props) {
        this.restClient = antRestClient;
        this.baseUrl = props.baseUrl();
    }

    @Override
    public String consultarCitaciones(String cedula) {
        return restClient.get()
                .uri(baseUrl + RUTA, cedula)
                .retrieve()
                .body(String.class);
    }
}
