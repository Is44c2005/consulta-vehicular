package ec.udla.consulta.sri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ec.udla.consulta.sri.client.SriClient;
import ec.udla.consulta.sri.exception.ApiException;

class ContribuyenteServiceTest {

    private static final String RUC_NATURAL = "1710034065001";
    private static final String RUC_SOCIEDAD = "1790016919001";

    private SriClient sriClient;
    private ContribuyenteService service;

    @BeforeEach
    void setUp() {
        sriClient = mock(SriClient.class);
        service = new ContribuyenteService(sriClient, new ObjectMapper());
    }

    @Test
    void rucConFormatoInvalidoDevuelve400SinLlamarAlSri() {
        for (String ruc : new String[] {"123", "17100340650", "1710034065002", "17100340AB001", ""}) {
            assertThatThrownBy(() -> service.consultarPersonaNatural(ruc))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(sriClient, never()).existePorNumeroRuc(anyString());
    }

    @Test
    void noContribuyenteDevuelve404() {
        when(sriClient.existePorNumeroRuc(RUC_NATURAL)).thenReturn("false");

        assertThatThrownBy(() -> service.consultarPersonaNatural(RUC_NATURAL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(sriClient, never()).obtenerPorNumeroRuc(anyString());
    }

    @Test
    void sociedadDevuelve422() {
        when(sriClient.existePorNumeroRuc(RUC_SOCIEDAD)).thenReturn("true");
        when(sriClient.obtenerPorNumeroRuc(RUC_SOCIEDAD)).thenReturn("""
                [{"numeroRuc":"1790016919001","razonSocial":"EMPRESA S.A.",
                  "personaSociedad":"SCD","tipoContribuyente":"SOCIEDAD"}]
                """);

        assertThatThrownBy(() -> service.consultarPersonaNatural(RUC_SOCIEDAD))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void personaNaturalDevuelveDatosToleraCamposDesconocidos() {
        when(sriClient.existePorNumeroRuc(RUC_NATURAL)).thenReturn("true");
        when(sriClient.obtenerPorNumeroRuc(RUC_NATURAL)).thenReturn("""
                [{"numeroRuc":"1710034065001","razonSocial":"PEREZ JUAN",
                  "estadoContribuyenteRuc":"ACTIVO","personaSociedad":"PNL",
                  "campoNuevoDelSri":{"x":1}}]
                """);

        JsonNode resultado = service.consultarPersonaNatural(RUC_NATURAL);

        assertThat(resultado.get("razonSocial").asText()).isEqualTo("PEREZ JUAN");
        assertThat(resultado.has("campoNuevoDelSri")).isTrue();
    }

    @Test
    void personaNaturalPorTipoContribuyente() {
        when(sriClient.existePorNumeroRuc(RUC_NATURAL)).thenReturn("true");
        when(sriClient.obtenerPorNumeroRuc(RUC_NATURAL))
                .thenReturn("[{\"razonSocial\":\"PEREZ JUAN\",\"tipoContribuyente\":\"PERSONA NATURAL\"}]");

        assertThat(service.consultarPersonaNatural(RUC_NATURAL).get("razonSocial").asText()).isEqualTo("PEREZ JUAN");
    }

    @Test
    void sinCamposDeTipoSeUsaElTercerDigito() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode sinCampos = om.readTree("{\"razonSocial\":\"X\"}");
        assertThat(ContribuyenteService.esPersonaNatural(sinCampos, RUC_NATURAL)).isTrue();
        assertThat(ContribuyenteService.esPersonaNatural(sinCampos, RUC_SOCIEDAD)).isFalse();
    }
}
