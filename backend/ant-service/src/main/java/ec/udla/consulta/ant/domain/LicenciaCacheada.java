package ec.udla.consulta.ant.domain;

import java.time.Instant;

/** Último dato válido de la ANT guardado en caché, con su fecha de obtención. */
public record LicenciaCacheada(LicenciaInfo datos, Instant guardadoEn) {
}
