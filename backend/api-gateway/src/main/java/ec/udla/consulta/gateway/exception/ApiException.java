package ec.udla.consulta.gateway.exception;

import org.springframework.http.HttpStatusCode;

/** Error que se devuelve al frontend como ProblemDetail con el estado indicado. */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String title;

    public ApiException(HttpStatusCode status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
