package com.moments.sicc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.Enums.TipoNotificacao;
import com.moments.sicc.domain.IdentidadeSetor;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.SetorRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sicc-notificacao-idempotencia;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificacaoChegadaServiceTest {

    @Autowired
    private NotificacaoChegadaService service;
    @Autowired
    private UsuarioInternoRepository usuarios;
    @Autowired
    private SetorRepository setores;
    @Autowired
    private ProcessoAdministrativoRepository processos;
    @Autowired
    private MovimentacaoRepository movimentacoes;
    @Autowired
    private NotificacaoRepository notificacoes;

    @Test
    void reprocessarMesmaMovimentacaoNaoDuplicaNotificacao() {
        UsuarioInterno autor = salvarUsuario(
                "Autor do Movimento", "autor@sicc.test", "autor");
        UsuarioInterno responsavel = salvarUsuario(
                "Responsável DIPAC", "responsavel@sicc.test", "responsavel");
        Setor destino = new Setor();
        destino.atualizarIdentidade(IdentidadeSetor.de("DIPAC", "Divisão de Parcerias e Convênios"));
        destino = setores.save(destino);
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setNumero("PROC-NOT-IDEMPOTENTE-008");
        processo.setOrigem("DIPAC");
        processo.setResponsavel(responsavel);
        processo = processos.save(processo);
        Long processoId = processo.getId();
        Movimentacao movimento = movimentacoes.save(new Movimentacao(
                ContextoTramitacao.FORMALIZACAO,
                processoId,
                LocalDate.of(2026, 7, 30),
                1,
                destino,
                autor,
                "Chegada processada novamente",
                LocalDateTime.of(2026, 7, 30, 19, 0)));

        service.processar(processo, movimento);
        service.processar(processo, movimento);

        assertThat(notificacoes.findByDestinatarioIdOrderByCriadaEmDesc(responsavel.getId()))
                .singleElement()
                .satisfies(notificacao -> {
                    assertThat(notificacao.getProcesso().getId()).isEqualTo(processoId);
                    assertThat(notificacao.getTipo()).isEqualTo(TipoNotificacao.CHEGADA_TRAMITACAO);
                    assertThat(notificacao.isLida()).isFalse();
                });
        assertThat(notificacoes.findByDestinatarioIdOrderByCriadaEmDesc(autor.getId())).isEmpty();
    }

    private UsuarioInterno salvarUsuario(String nome, String email, String login) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setLogin(login);
        usuario.setSenhaHash("hash-nao-utilizado-neste-teste");
        usuario.setPerfil(PerfilAcesso.OPERADOR_DIPAC);
        usuario.setSenhaTemporaria(false);
        return usuarios.save(usuario);
    }
}
