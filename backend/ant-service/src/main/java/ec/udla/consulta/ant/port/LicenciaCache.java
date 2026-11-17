package ec.udla.consulta.ant.port;

import java.time.Instant;
import java.util.Optional;

import ec.udla.consulta.ant.domain.LicenciaCacheada;
import ec.udla.consulta.ant.domain.LicenciaInfo;

/**
 * Puerto de la caché de licencias. Las implementaciones son "best-effort":
 * nunca lanzan excepciones; si el almacenamiento falla, {@link #obtener} devuelve vacío
 * y {@link #guardar} no hace nada.
 */
public interface LicenciaCache {

    Optional<LicenciaCacheada> obtener(String cedula);

    void guardar(String cedula, LicenciaInfo datos, Instant guardadoEn);
}
