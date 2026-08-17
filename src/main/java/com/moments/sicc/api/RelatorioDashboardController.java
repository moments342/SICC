package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.service.IdentidadeService;
import com.moments.sicc.service.RelatorioDashboardService;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RelatorioDashboardController {
    private final RelatorioDashboardService service;
    private final IdentidadeService identidade;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) TipoInstrumento tipo,
            @RequestParam(required = false) StatusProcesso status) {
        return service.dashboard(origem, tipo, status);
    }

    @PostMapping("/relatorios")
    @ResponseStatus(HttpStatus.CREATED)
    public RelatorioResponse gerar(@Valid @RequestBody GerarRelatorioRequest request, HttpServletRequest http) {
        return service.gerar(request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/relatorios")
    public List<RelatorioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/relatorios/{id}/arquivo")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id, HttpServletRequest http) {
        RelatorioDashboardService.Download download = service.download(
                id, identidade.atual(), http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .body(download.resource());
    }
}
