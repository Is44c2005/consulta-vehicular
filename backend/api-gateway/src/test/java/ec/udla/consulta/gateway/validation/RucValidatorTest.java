package ec.udla.consulta.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RucValidatorTest {

    private final RucValidator validator = new RucValidator();

    @Test
    void rucDePersonaNaturalValido() {
        assertThat(validator.validar("1710034065001")).isEmpty();
        assertThat(validator.esValido("1710034065001")).isTrue();
    }

    @Test
    void rechazaSufijoDistintoDe001() {
        assertThat(validator.validar("1710034065002")).get().asString().contains("001");
    }

    @Test
    void rechazaLongitudOCaracteresNoNumericos() {
        assertThat(validator.esValido("171003406500")).isFalse();
        assertThat(validator.esValido("17100340650011")).isFalse();
        assertThat(validator.esValido("17100340A5001")).isFalse();
        assertThat(validator.esValido(null)).isFalse();
    }

    @Test
    void rechazaProvinciaInvalida() {
        assertThat(validator.validar("2510034065001")).get().asString().contains("provincia");
        assertThat(validator.validar("0010034065001")).get().asString().contains("provincia");
    }

    @Test
    void aceptaProvincia30() {
        // 301000000?: 3*2 + 1*2 = 8 -> dígito verificador (10 - 8) % 10 = 2
        assertThat(RucValidator.cedulaValida("3010000002")).isTrue();
        assertThat(validator.esValido("3010000002001")).isTrue();
    }

    @Test
    void rechazaCedulaConDigitoVerificadorInvalido() {
        assertThat(validator.validar("1710034066001")).get().asString().contains("cédula");
    }

    @Test
    void rucDeSociedadPasaLaValidacionDeFormato() {
        assertThat(validator.validar("1790016919001")).isEmpty();
    }
}
