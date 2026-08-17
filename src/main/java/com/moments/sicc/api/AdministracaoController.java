package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.service.AdministracaoUsuarioService;
import com.moments.sicc.service.IdentidadeService;
import com.moments.sicc.service.SiccService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR_DIPAC')")
public class AdministracaoController {
    private final SiccService service;
    private final AdministracaoUsuarioService usuarios;
    private final IdentidadeService identidade;

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criarUsuario(@Valid @RequestBody CriarUsuarioRequest request, HttpServletRequest http) {
        return usuarios.criar(request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/usuarios")
    public List<UsuarioResponse> listarUsuarios() {
        return usuarios.listar();
    }

    @GetMapping("/usuarios/{id}")
    public UsuarioResponse detalharUsuario(@PathVariable Long id) {
        return usuarios.detalhar(id);
    }

    @PatchMapping("/usuarios/{id}/senha")
    public UsuarioResponse redefinirSenha(@PathVariable Long id,
            @Valid @RequestBody ResetarSenhaRequest request, HttpServletRequest http) {
        return usuarios.redefinirSenha(
                id, request.novaSenhaTemporaria(), identidade.atual(), http.getRemoteAddr());
    }

    @PatchMapping("/usuarios/{id}/ativo")
    public UsuarioResponse definirAtivoUsuario(@PathVariable Long id, @RequestParam boolean ativo,
            HttpServletRequest http) {
        return usuarios.definirAtivo(id, ativo, identidade.atual(), http.getRemoteAddr());
    }

    @PatchMapping("/usuarios/{id}/perfil")
    public UsuarioResponse definirPerfil(@PathVariable Long id, @RequestParam PerfilAcesso perfil,
            HttpServletRequest http) {
        return usuarios.definirPerfil(id, perfil, identidade.atual(), http.getRemoteAddr());
    }

    @PostMapping("/setores")
    @ResponseStatus(HttpStatus.CREATED)
    public SetorResponse criarSetor(@Valid @RequestBody CriarSetorRequest request, HttpServletRequest http) {
        return service.criarSetor(request, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/setores")
    public List<SetorResponse> listarSetores(@RequestParam(defaultValue = "false") boolean somenteAtivos) {
        return service.listarSetores(somenteAtivos);
    }

    @PatchMapping("/setores/{id}/ativo")
    public SetorResponse definirAtivoSetor(@PathVariable Long id, @RequestParam boolean ativo,
            HttpServletRequest http) {
        return service.definirAtivoSetor(id, ativo, identidade.atual(), http.getRemoteAddr());
    }
}
