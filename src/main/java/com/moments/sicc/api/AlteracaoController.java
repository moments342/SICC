package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.service.AlteracaoService;
import com.moments.sicc.service.IdentidadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alteracoes")
@RequiredArgsConstructor
public class AlteracaoController {
    private final AlteracaoService service;
    private final IdentidadeService identidade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlteracaoResponse criar(@Valid @RequestBody CriarAlteracaoRequest request, HttpServletRequest http) {
        return service.criar(request, identidade.atual(), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public AlteracaoResponse atualizar(@PathVariable Long id,
            @Valid @RequestBody AtualizarRascunhoAlteracaoRequest request, HttpServletRequest http) {
        return service.atualizar(id, request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/{id}")
    public AlteracaoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/{id}/efetivacao")
    public AlteracaoResponse efetivar(@PathVariable Long id,
            @Valid @RequestBody EfetivarAlteracaoRequest request, HttpServletRequest http) {
        return service.efetivar(id, request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping
    public List<AlteracaoResponse> listar(@RequestParam Long instrumentoId) {
        return service.listar(instrumentoId);
    }
}
