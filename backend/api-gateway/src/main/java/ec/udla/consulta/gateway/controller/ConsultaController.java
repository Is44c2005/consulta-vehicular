package ec.udla.consulta.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ec.udla.consulta.gateway.client.MicroservicioClient;
import ec.udla.consulta.gateway.exception.ApiException;
import ec.udla.consulta.gateway.validation.RucValidator;

/** BFF: único punto de entrada del frontend. */
@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private static final Logger log = LoggerFactory.getLogger(ConsultaController.class);
    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    private final RucValidator rucValidator;
    private final MicroservicioClient client;

    public ConsultaController(RucValidator rucValidator, MicroservicioClient client) {
        this.rucValidator = rucValidator;
        this.client = client;
    }

    public record ContribuyenteRequest(String email, String ruc) {
    }

    @PostMapping("/contribuyente")
    public JsonNode contribuyente(@RequestBody(required = false) ContribuyenteRequest request) {
        String email = request == null || request.email() == null ? "" : request.email().trim();
        String ruc = request == null || request.ruc() == null ? "" : request.ruc().trim();

        if (email.length() > 254 || !email.matches(EMAIL_REGEX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo inválido",
                    "Ingrese un correo electrónico válido (por ejemplo nombre@dominio.com).");
        }
        rucValidator.validar(ruc).ifPresent(msg -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUC inválido", msg);
        });

        // El correo no se persiste: solo se registra enmascarado para trazabilidad.
        log.info("Consulta de contribuyente solicitada por {} para RUC {}", enmascararEmail(email), ruc);

        JsonNode contribuyente = client.contribuyente(ruc);
        ObjectNode respuesta = JsonNodeFactory.instance.objectNode();
        respuesta.put("ruc", ruc);
        respuesta.put("cedula", ruc.substring(0, 10));
        respuesta.set("contribuyente", contribuyente);
        return respuesta;
    }

    @GetMapping("/vehiculos/{placa}")
    public JsonNode vehiculo(@PathVariable String placa) {
        return client.vehiculo(placa.trim());
    }

    @GetMapping("/licencias/{cedula}")
    public JsonNode licencia(@PathVariable String cedula) {
        if (!cedula.matches("\\d{10}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cédula inválida",
                    "La cédula debe tener exactamente 10 dígitos numéricos.");
        }
        return client.licencia(cedula);
    }

    static String enmascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
