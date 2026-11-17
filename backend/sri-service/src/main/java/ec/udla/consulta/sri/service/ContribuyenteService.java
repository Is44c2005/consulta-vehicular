package ec.udla.consulta.sri.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.udla.consulta.sri.client.SriClient;
import ec.udla.consulta.sri.exception.ApiException;

/**
 * Verifica que un RUC sea contribuyente en el SRI y que corresponda a una persona natural.
 */
@Service
public class ContribuyenteService {

    private static final String RUC_REGEX = "\\d{10}001";

    private final SriClient sriClient;
    private final ObjectMapper objectMapper;

    public ContribuyenteService(SriClient sriClient, ObjectMapper objectMapper) {
        this.sriClient = sriClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode consultarPersonaNatural(String ruc) {
        String limpio = ruc == null ? "" : ruc.trim();
        if (!limpio.matches(RUC_REGEX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUC inválido",
                    "El RUC debe tener 13 dígitos numéricos y terminar en 001.");
        }

        String existe = sriClient.existePorNumeroRuc(limpio);
        if (existe == null || !"true".equalsIgnoreCase(existe.trim().replace("\"", ""))) {
            throw noEncontrado(limpio);
        }

        JsonNode respuesta = leer(sriClient.obtenerPorNumeroRuc(limpio));
        JsonNode contribuyente = respuesta.isArray() ? respuesta.path(0) : respuesta;
        if (contribuyente.isMissingNode() || contribuyente.isNull()
                || !contribuyente.isObject() || contribuyente.isEmpty()) {
            throw noEncontrado(limpio);
        }

        if (!esPersonaNatural(contribuyente, limpio)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No es persona natural",
                    "El RUC " + limpio + " corresponde a una sociedad. Esta consulta solo está disponible para personas naturales.");
        }
        return contribuyente;
    }

    /**
     * Persona natural si personaSociedad = PNL o tipoContribuyente contiene NATURAL.
     * Si el SRI no envía ninguno de los dos campos, se usa el tercer dígito del RUC (&lt; 6).
     */
    static boolean esPersonaNatural(JsonNode contribuyente, String ruc) {
        String personaSociedad = texto(contribuyente, "personaSociedad");
        String tipo = texto(contribuyente, "tipoContribuyente");
        if (personaSociedad != null && "PNL".equalsIgnoreCase(personaSociedad.trim())) {
            return true;
        }
        if (tipo != null && tipo.toUpperCase(Locale.ROOT).contains("NATURAL")) {
            return true;
        }
        if (personaSociedad == null && tipo == null) {
            return Character.getNumericValue(ruc.charAt(2)) < 6;
        }
        return false;
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String s = valor.asText();
        return s.isBlank() ? null : s;
    }

    private JsonNode leer(String json) {
        if (json == null) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Respuesta inválida del SRI",
                    "El SRI devolvió una respuesta que no se pudo interpretar.");
        }
    }

    private static ApiException noEncontrado(String ruc) {
        return new ApiException(HttpStatus.NOT_FOUND, "Contribuyente no encontrado",
                "El RUC " + ruc + " no está registrado como contribuyente en el SRI.");
    }
}
