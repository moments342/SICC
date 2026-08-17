package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.moments.sicc.api.ApiDtos.CriarUsuarioRequest;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.UsuarioInternoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-admin-atomicidade;MODE=PostgreSQL")
@DirtiesContext
class AdministracaoUsuarioAtomicidadeTest {

    @Autowired
    private AdministracaoUsuarioService service;
    @Autowired
    private UsuarioInternoRepository usuarios;
    @MockitoBean
    private AuditoriaService auditoria;

    @Test
    void falhaDaAuditoriaReverteTodasAsMutacoesDeUsuario() {
        UsuarioInterno administrador = usuarios.findByLoginIgnoreCase("admin").orElseThrow();
        DataIntegrityViolationException falhaAuditoria =
                new DataIntegrityViolationException("Falha de auditoria simulada.");
        doThrow(falhaAuditoria).when(auditoria).registrarNaTransacaoAtual(
                any(), anyString(), anyString(), any(), anyBoolean(), any(), anyString());

        CriarUsuarioRequest novoUsuario = new CriarUsuarioRequest(
                "Novo Operador", "novo@ufgd.edu.br", "novo.operador",
                "Operador123!", PerfilAcesso.OPERADOR_DIPAC);
        assertThatThrownBy(() -> service.criar(novoUsuario, administrador, "127.0.0.1"))
                .isSameAs(falhaAuditoria);
        assertThat(usuarios.findByLoginIgnoreCase("novo.operador")).isEmpty();

        UsuarioInterno operador = new UsuarioInterno();
        operador.setNome("Operador");
        operador.setEmail("operador@ufgd.edu.br");
        operador.setLogin("operador");
        operador.setSenhaHash("hash-anterior");
        operador.setPerfil(PerfilAcesso.OPERADOR_DIPAC);
        operador.setSenhaTemporaria(false);
        operador = usuarios.saveAndFlush(operador);
        long operadorId = operador.getId();

        assertThatThrownBy(() -> service.redefinirSenha(
                        operadorId, "Operador456!", administrador, "127.0.0.1"))
                .isSameAs(falhaAuditoria);
        UsuarioInterno aposSenha = usuarios.findById(operadorId).orElseThrow();
        assertThat(aposSenha.getSenhaHash()).isEqualTo("hash-anterior");
        assertThat(aposSenha.isSenhaTemporaria()).isFalse();
        assertThat(aposSenha.getVersaoAcesso()).isZero();

        assertThatThrownBy(() -> service.definirPerfil(
                        operadorId, PerfilAcesso.ADMINISTRADOR_DIPAC, administrador, "127.0.0.1"))
                .isSameAs(falhaAuditoria);
        UsuarioInterno aposPerfil = usuarios.findById(operadorId).orElseThrow();
        assertThat(aposPerfil.getPerfil()).isEqualTo(PerfilAcesso.OPERADOR_DIPAC);
        assertThat(aposPerfil.getVersaoAcesso()).isZero();

        assertThatThrownBy(() -> service.definirAtivo(
                        operadorId, false, administrador, "127.0.0.1"))
                .isSameAs(falhaAuditoria);
        UsuarioInterno aposAtivacao = usuarios.findById(operadorId).orElseThrow();
        assertThat(aposAtivacao.isAtivo()).isTrue();
        assertThat(aposAtivacao.getVersaoAcesso()).isZero();
    }
}
