package com.moments.sicc.consulta.api;

import com.moments.sicc.api.ApiDtos.PaginaResponse;
import com.moments.sicc.api.ApiDtos.ProcessoPublicoResponse;
import com.moments.sicc.service.SiccService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/processos")
@RequiredArgsConstructor
public class ConsultaPublicaController {
    private final SiccService service;

    @GetMapping
    public PaginaResponse<ProcessoPublicoResponse> listar(
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vigencia,
            Pageable pageable) {
        return service.consultaPublica(numero, origem, tipo, status, vigencia, pageable);
    }
}
