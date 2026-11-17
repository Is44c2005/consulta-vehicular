package ec.udla.consulta.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsultaControllerTest {

    @Test
    void enmascaraElCorreoParaLosLogs() {
        assertThat(ConsultaController.enmascararEmail("juan.perez@gmail.com")).isEqualTo("j***@gmail.com");
        assertThat(ConsultaController.enmascararEmail("sinarroba")).isEqualTo("***");
    }
}
