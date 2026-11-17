package ec.udla.consulta.ant.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ec.udla.consulta.ant.domain.LicenciaInfo;
import ec.udla.consulta.ant.exception.AntRespuestaInvalidaException;

class AntHtmlParserTest {

    private static final String CEDULA = "1710034065";
    private final AntHtmlParser parser = new AntHtmlParser();

    @Test
    void extraePuntosDeTextoEtiquetado() {
        LicenciaInfo info = parser.parse(CEDULA, "<html><body><p>Puntos de licencia: 26,5</p></body></html>");
        assertThat(info.puntos()).isEqualTo(26.5);
        assertThat(info.cedula()).isEqualTo(CEDULA);
    }

    @Test
    void extraePuntosDeElementoConIdPuntos() {
        LicenciaInfo info = parser.parse(CEDULA,
                "<html><body><div class='titulo'>Saldo</div><span id='txtPuntos'> 30 </span></body></html>");
        assertThat(info.puntos()).isEqualTo(30.0);
    }

    @Test
    void extraeTablaDeCitacionesConThYTd() {
        String html = """
                <html><body>
                  <div>Usted tiene 24 puntos</div>
                  <table>
                    <thead><tr><th>Placa</th><th>Fecha emisión</th><th>Puntos</th><th>Valor</th></tr></thead>
                    <tbody>
                      <tr><td>PBA1234</td><td>10/01/2026</td><td>3</td><td>$ 46,00</td></tr>
                      <tr><td>PBA1234</td><td>15/03/2026</td><td>3</td><td>$ 92,00</td></tr>
                      <tr><td></td><td></td><td></td><td></td></tr>
                    </tbody>
                  </table>
                </body></html>
                """;

        LicenciaInfo info = parser.parse(CEDULA, html);

        assertThat(info.puntos()).isEqualTo(24.0);
        assertThat(info.citaciones()).hasSize(2);
        assertThat(info.citaciones().get(0))
                .containsEntry("Placa", "PBA1234")
                .containsEntry("Fecha emisión", "10/01/2026")
                .containsEntry("Valor", "$ 46,00");
        assertThat(info.citaciones().get(0).keySet()).containsExactly("Placa", "Fecha emisión", "Puntos", "Valor");
    }

    @Test
    void laColumnaPuntosDeLaTablaNoSeConfundeConLosPuntosDeLaLicencia() {
        String html = """
                <table><tr><th>Placa</th><th>Puntos</th></tr><tr><td>ABC1234</td><td>6</td></tr></table>
                """;
        LicenciaInfo info = parser.parse(CEDULA, html);
        assertThat(info.puntos()).isNull();
        assertThat(info.citaciones()).hasSize(1);
    }

    @Test
    void reconoceNoRegistraCitaciones() {
        LicenciaInfo info = parser.parse(CEDULA,
                "<html><body><table><tr><td>El usuario NO REGISTRA CITACIONES pendientes</td></tr></table></body></html>");
        assertThat(info.citaciones()).isEmpty();
        assertThat(info.puntos()).isNull();
    }

    @Test
    void htmlVacioEsInvalido() {
        assertThatThrownBy(() -> parser.parse(CEDULA, "")).isInstanceOf(AntRespuestaInvalidaException.class);
        assertThatThrownBy(() -> parser.parse(CEDULA, null)).isInstanceOf(AntRespuestaInvalidaException.class);
        assertThatThrownBy(() -> parser.parse(CEDULA, "<html><body></body></html>"))
                .isInstanceOf(AntRespuestaInvalidaException.class);
    }

    @Test
    void paginaDeMantenimientoEsInvalida() {
        String html = """
                <html><head><title>Mantenimiento</title></head>
                <body><h1>Sitio en mantenimiento</h1><p>Disculpe las molestias, vuelva más tarde.</p></body></html>
                """;
        assertThatThrownBy(() -> parser.parse(CEDULA, html)).isInstanceOf(AntRespuestaInvalidaException.class);
    }
}
