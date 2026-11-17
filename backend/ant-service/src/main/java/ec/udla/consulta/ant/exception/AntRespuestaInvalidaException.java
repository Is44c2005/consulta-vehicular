package ec.udla.consulta.ant.exception;

/** La ANT respondió, pero el contenido no es una consulta de licencia reconocible. */
public class AntRespuestaInvalidaException extends RuntimeException {

    public AntRespuestaInvalidaException(String message) {
        super(message);
    }
}
