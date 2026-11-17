package ec.udla.consulta.ant.port;

/** Puerto de salida hacia el portal web de la ANT. */
public interface AntClient {

    /**
     * Obtiene el HTML de la consulta de citaciones de una cédula.
     *
     * @throws RuntimeException si la ANT no responde, responde con error o excede el timeout
     */
    String consultarCitaciones(String cedula);
}
