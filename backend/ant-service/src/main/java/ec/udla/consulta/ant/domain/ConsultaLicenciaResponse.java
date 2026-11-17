package ec.udla.consulta.ant.domain;

import java.time.Instant;

/**
 * Respuesta del servicio.
 *
 * @param datos          información de la licencia
 * @param fuente         {@code ANT} (dato en vivo) o {@code CACHE}
 * @param consultadoEn   momento en que el dato se obtuvo de la ANT
 * @param desactualizado {@code true} si se sirve caché porque la ANT falló
 * @param aviso          mensaje para el usuario (puede ser {@code null})
 */
public record ConsultaLicenciaResponse(
        LicenciaInfo datos,
        Fuente fuente,
        Instant consultadoEn,
        boolean desactualizado,
        String aviso) {

    public enum Fuente { ANT, CACHE }
}
