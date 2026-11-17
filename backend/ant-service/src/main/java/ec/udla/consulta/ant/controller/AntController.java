package ec.udla.consulta.ant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ec.udla.consulta.ant.domain.ConsultaLicenciaResponse;
import ec.udla.consulta.ant.service.ConsultaLicenciaService;

@RestController
@RequestMapping("/api/ant")
public class AntController {

    private final ConsultaLicenciaService service;

    public AntController(ConsultaLicenciaService service) {
        this.service = service;
    }

    @GetMapping("/licencias/{cedula}")
    public ConsultaLicenciaResponse licencia(@PathVariable String cedula) {
        return service.consultar(cedula);
    }
}
