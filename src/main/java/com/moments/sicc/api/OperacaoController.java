package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.service.CatalogoSetorService;
import com.moments.sicc.service.IdentidadeService;
import com.moments.sicc.service.SiccService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OperacaoController {
    private final SiccService service;
    private final CatalogoSetorService setores;
    private final IdentidadeService identidade;

    @GetMapping("/setores")
    public List<SetorResponse> listarSetoresAtivos() {
        return setores.listarAtivos();
    }

    @PostMapping("/processos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessoResponse criarProcesso(@Valid @RequestBody CriarProcessoRequest request, HttpServletRequest http) {
        return service.criarProcesso(request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/processos")
    public PaginaResponse<ProcessoResponse> listarProcessos(
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vigencia,
            Pageable pageable) {
        return service.listarProcessos(numero, origem, tipo, status, vigencia, pageable);
    }

    @GetMapping("/processos/responsaveis")
    public List<ResponsavelProcessoResponse> listarResponsaveisAtivos() {
        return service.listarResponsaveisAtivos();
    }

    @GetMapping("/processos/{id}")
    public ProcessoResponse buscarProcesso(@PathVariable Long id) {
        return service.buscarProcesso(id);
    }

    @PutMapping("/processos/{id}")
    public ProcessoResponse atualizarProcesso(@PathVariable Long id,
            @Valid @RequestBody AtualizarProcessoRequest request, HttpServletRequest http) {
        return service.atualizarProcesso(id, request, identidade.atual(), http.getRemoteAddr());
    }

    @DeleteMapping("/processos/{id}")
    public ProcessoResponse desativarProcesso(@PathVariable Long id, HttpServletRequest http) {
        return service.desativarProcesso(id, identidade.atual(), http.getRemoteAddr());
    }

    @PostMapping("/processos/{id}/instrumento")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentoResponse formalizar(@PathVariable Long id,
            @Valid @RequestBody FormalizarInstrumentoRequest request, HttpServletRequest http) {
        return service.formalizar(id, request, identidade.atual(), http.getRemoteAddr());
    }

    @PostMapping("/movimentacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse movimentar(@Valid @RequestBody CriarMovimentacaoRequest request,
            HttpServletRequest http) {
        return service.movimentar(request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/movimentacoes")
    public List<MovimentacaoResponse> listarMovimentacoes(
            @RequestParam ContextoTramitacao contextoTipo, @RequestParam Long contextoId) {
        return service.listarMovimentacoes(contextoTipo, contextoId);
    }

    @GetMapping("/notificacoes")
    public List<NotificacaoResponse> listarNotificacoes() {
        return service.listarNotificacoes(identidade.atual());
    }

    @PatchMapping("/notificacoes/{id}/lida")
    public NotificacaoResponse marcarNotificacaoLida(@PathVariable Long id) {
        return service.marcarNotificacaoLida(id, identidade.atual());
    }
}
