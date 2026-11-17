package ec.udla.consulta.sri.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.udla.consulta.sri.client.SriClient;
import ec.udla.consulta.sri.exception.ApiException;

/** Consulta los datos de un vehículo por placa, CAMV o CPN en el SRI. */
@Service
public class VehiculoService {

    /** Placas (ABC1234, AB123C), códigos CAMV o CPN: alfanumérico de 5 a 10 caracteres. */
    private static final String PLACA_REGEX = "[A-Z0-9]{5,10}";

    private final SriClient sriClient;
    private final ObjectMapper objectMapper;

    public VehiculoService(SriClient sriClient, ObjectMapper objectMapper) {
        this.sriClient = sriClient;
        this.objectMapper = objectMapper;
    }

    public static String normalizarPlaca(String placa) {
        return placa == null ? "" : placa.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    public JsonNode consultar(String placa) {
        String normalizada = normalizarPlaca(placa);
        if (!normalizada.matches(PLACA_REGEX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Placa inválida",
                    "La placa debe tener entre 5 y 10 caracteres alfanuméricos (por ejemplo ABC1234).");
        }

        String json = sriClient.obtenerVehiculo(normalizada);
        JsonNode nodo = leer(json);
        JsonNode vehiculo = nodo.isArray() ? nodo.path(0) : nodo;
        if (vehiculo.isMissingNode() || vehiculo.isNull() || !vehiculo.isObject() || sinDatos(vehiculo)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vehículo no encontrado",
                    "No se encontró un vehículo con la placa " + normalizada + " en el SRI.");
        }
        return vehiculo;
    }

    /** Un objeto vacío o con todos sus campos nulos se considera "sin datos". */
    private static boolean sinDatos(JsonNode vehiculo) {
        for (JsonNode valor : vehiculo) {
            if (!valor.isNull() && !(valor.isTextual() && valor.asText().isBlank())) {
                return false;
            }
        }
        return true;
    }

    private JsonNode leer(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Respuesta inválida del SRI",
                    "El SRI devolvió una respuesta que no se pudo interpretar.");
        }
    }
}
