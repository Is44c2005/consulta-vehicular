package ec.udla.consulta.ant.domain;

import java.util.List;
import java.util.Map;

/**
 * Información de la licencia extraída del portal de la ANT.
 *
 * @param cedula     cédula consultada
 * @param puntos     puntos de la licencia (puede ser {@code null} si la ANT no los muestra)
 * @param citaciones filas de la tabla de citaciones como mapas columna → valor
 */
public record LicenciaInfo(String cedula, Double puntos, List<Map<String, String>> citaciones) {

    public LicenciaInfo {
        citaciones = citaciones == null ? List.of() : List.copyOf(citaciones);
    }
}
