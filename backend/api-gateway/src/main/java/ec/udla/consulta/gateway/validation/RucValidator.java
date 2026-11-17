package ec.udla.consulta.gateway.validation;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Validación local del RUC ecuatoriano antes de ir al SRI:
 * <ul>
 *   <li>13 dígitos numéricos que terminan en 001,</li>
 *   <li>código de provincia 01-24 o 30 (ecuatorianos en el exterior),</li>
 *   <li>si el tercer dígito es menor a 6 (persona natural), los 10 primeros dígitos deben ser
 *       una cédula válida según el algoritmo módulo 10.</li>
 * </ul>
 * Los RUC de sociedades (tercer dígito 6 o 9) solo se validan por formato y provincia.
 */
@Component
public class RucValidator {

    private static final int[] COEFICIENTES_CEDULA = {2, 1, 2, 1, 2, 1, 2, 1, 2};

    /** @return mensaje de error en español, o vacío si el RUC es válido. */
    public Optional<String> validar(String ruc) {
        if (ruc == null || !ruc.matches("\\d{13}")) {
            return Optional.of("El RUC debe tener exactamente 13 dígitos numéricos.");
        }
        if (!ruc.endsWith("001")) {
            return Optional.of("El RUC de una persona natural debe terminar en 001.");
        }
        int provincia = Integer.parseInt(ruc.substring(0, 2));
        if (!((provincia >= 1 && provincia <= 24) || provincia == 30)) {
            return Optional.of("Los dos primeros dígitos del RUC deben ser un código de provincia válido (01-24 o 30).");
        }
        int tercerDigito = ruc.charAt(2) - '0';
        if (tercerDigito < 6 && !cedulaValida(ruc.substring(0, 10))) {
            return Optional.of("Los 10 primeros dígitos del RUC no corresponden a una cédula válida.");
        }
        return Optional.empty();
    }

    public boolean esValido(String ruc) {
        return validar(ruc).isEmpty();
    }

    /** Algoritmo módulo 10 de la cédula ecuatoriana. */
    static boolean cedulaValida(String cedula) {
        int suma = 0;
        for (int i = 0; i < 9; i++) {
            int producto = (cedula.charAt(i) - '0') * COEFICIENTES_CEDULA[i];
            suma += producto >= 10 ? producto - 9 : producto;
        }
        int verificador = (10 - (suma % 10)) % 10;
        return verificador == cedula.charAt(9) - '0';
    }
}
