package ec.udla.consulta.sri.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import ec.udla.consulta.sri.service.ContribuyenteService;
import ec.udla.consulta.sri.service.VehiculoService;

@RestController
@RequestMapping("/api/sri")
public class SriController {

    private final ContribuyenteService contribuyenteService;
    private final VehiculoService vehiculoService;

    public SriController(ContribuyenteService contribuyenteService, VehiculoService vehiculoService) {
        this.contribuyenteService = contribuyenteService;
        this.vehiculoService = vehiculoService;
    }

    @GetMapping("/contribuyentes/{ruc}")
    public JsonNode contribuyente(@PathVariable String ruc) {
        return contribuyenteService.consultarPersonaNatural(ruc);
    }

    @GetMapping("/vehiculos/{placa}")
    public JsonNode vehiculo(@PathVariable String placa) {
        return vehiculoService.consultar(placa);
    }
}
