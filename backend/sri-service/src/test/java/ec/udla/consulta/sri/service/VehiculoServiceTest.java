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

class VehiculoServiceTest {

    private SriClient sriClient;
    private VehiculoService service;

    @BeforeEach
    void setUp() {
        sriClient = mock(SriClient.class);
        service = new VehiculoService(sriClient, new ObjectMapper());
    }

    @Test
    void placaInvalidaDevuelve400() {
        for (String placa : new String[] {"AB", "ABC$123", "", "ABCDEFGHIJK1"}) {
            assertThatThrownBy(() -> service.consultar(placa))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(sriClient, never()).obtenerVehiculo(anyString());
    }

    @Test
    void vehiculoExistenteSeDevuelveYLaPlacaSeNormaliza() {
        when(sriClient.obtenerVehiculo("PBA1234")).thenReturn("""
                {"numeroPlaca":"PBA1234","descripcionMarca":"CHEVROLET","descripcionModelo":"AVEO",
                 "anioAuto":2015,"nuevoCampo":"x"}
                """);

        JsonNode vehiculo = service.consultar(" pba-1234 ");

        assertThat(vehiculo.get("descripcionMarca").asText()).isEqualTo("CHEVROLET");
        assertThat(vehiculo.get("anioAuto").asInt()).isEqualTo(2015);
    }

    @Test
    void vehiculoInexistenteDevuelve404() {
        when(sriClient.obtenerVehiculo("XYZ9999")).thenReturn(null);

        assertThatThrownBy(() -> service.consultar("XYZ9999"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void respuestaConCamposNulosSeConsideraInexistente() {
        when(sriClient.obtenerVehiculo("XYZ9998")).thenReturn("{\"numeroPlaca\":null,\"descripcionMarca\":\"\"}");

        assertThatThrownBy(() -> service.consultar("XYZ9998"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
