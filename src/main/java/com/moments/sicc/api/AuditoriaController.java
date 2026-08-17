package com.moments.sicc.api;

import com.moments.sicc.api.ApiDtos.PaginaResponse;
import com.moments.sicc.api.ApiDtos.RegistroAuditoriaResponse;
import com.moments.sicc.domain.Enums.ResultadoAuditoria;
import com.moments.sicc.service.AuditoriaService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR_DIPAC')")
public class AuditoriaController {
    private final AuditoriaService auditoria;

    @GetMapping
    public PaginaResponse<RegistroAuditoriaResponse> consultar(
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) ResultadoAuditoria resultado,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PaginaResponse.de(auditoria.consultar(
                acao, resultado, usuario, dataInicial, dataFinal, page, size));
    }
}
