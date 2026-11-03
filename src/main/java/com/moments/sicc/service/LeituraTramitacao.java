package com.moments.sicc.service;

import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prepara leituras completas sem expor entidades ou consultar o relogio da operacao. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeituraTramitacao {
    private static final int TAMANHO_LOTE = 500;
    private final MovimentacaoRepository movimentacoes;
    private final AlteracaoContratualRepository alteracoes;
    private final CalculadoraPermanencia calculadora;

    public Map<Long, SetorLido> setoresAtuais(Collection<Long> processosIds) {
        Map<Long, SetorLido> setores = new LinkedHashMap<>();
        emLotes(ids(processosIds), lote -> movimentacoes
                .findUltimasPorContextos(ContextoTramitacao.FORMALIZACAO, lote)
                .forEach(movimento -> setores.put(
                        movimento.getContextoId(), setor(movimento.getSetorDestino()))));
        return Map.copyOf(setores);
    }

    /** Existencia, autorizacao e erros de cada entrada permanecem nos callers atuais. */
    public HistoricoLido historico(Contexto contexto, LocalDate referencia) {
        Objects.requireNonNull(contexto);
        Objects.requireNonNull(referencia);
        Long processoId = contexto.tipo() == ContextoTramitacao.FORMALIZACAO
                ? contexto.id()
                : alteracoes.findHierarquiaById(contexto.id())
                        .map(AlteracaoContratualRepository.HierarquiaAlteracao::getProcessoId)
                        .orElse(null);
        return preparar(contexto, processoId, referencia,
                movimentacoes.findHistoricosPorContextos(contexto.tipo(), List.of(contexto.id())));
    }

    public List<HistoricoLido> historicos(
            Collection<Long> processosIds, Set<ContextoTramitacao> tipos, LocalDate referencia) {
        List<Long> selecionados = ids(processosIds);
        Set<ContextoTramitacao> contextos = Set.copyOf(tipos);
        Objects.requireNonNull(referencia);
        if (selecionados.isEmpty() || contextos.isEmpty()) return List.of();
        List<TipoAlteracao> tiposAlteracao = java.util.Arrays.stream(TipoAlteracao.values())
                .filter(tipo -> contextos.contains(tipo.contextoTramitacao())).toList();
        List<HistoricoLido> resultado = new ArrayList<>();
        emLotes(selecionados, lote -> {
            Map<Contexto, Long> processosPorContexto = new LinkedHashMap<>();
            if (contextos.contains(ContextoTramitacao.FORMALIZACAO)) {
                lote.forEach(id -> processosPorContexto.put(
                        new Contexto(ContextoTramitacao.FORMALIZACAO, id), id));
            }
            if (!tiposAlteracao.isEmpty()) {
                alteracoes.findHierarquiasPorProcessos(lote, tiposAlteracao).forEach(alteracao ->
                        processosPorContexto.put(new Contexto(
                                alteracao.getTipo().contextoTramitacao(), alteracao.getAlteracaoId()),
                                alteracao.getProcessoId()));
            }
            for (ContextoTramitacao tipo : ContextoTramitacao.values()) {
                List<Long> contextoIds = processosPorContexto.keySet().stream()
                        .filter(contexto -> contexto.tipo() == tipo).map(Contexto::id).toList();
                emLotes(contextoIds, contextosDoLote -> {
                    Map<Long, List<Movimentacao>> porContexto = movimentacoes
                            .findHistoricosPorContextos(tipo, contextosDoLote).stream()
                            .collect(Collectors.groupingBy(Movimentacao::getContextoId));
                    contextosDoLote.forEach(id -> {
                        Contexto contexto = new Contexto(tipo, id);
                        resultado.add(preparar(contexto, processosPorContexto.get(contexto), referencia,
                                porContexto.getOrDefault(id, List.of())));
                    });
                });
            }
        });
        // A ordem do enum e a ordem publica atual; ordenar o VARCHAR no banco seria diferente.
        resultado.sort(Comparator.comparing((HistoricoLido h) -> h.contexto().tipo())
                .thenComparing(h -> h.contexto().id()));
        return List.copyOf(resultado);
    }

    private HistoricoLido preparar(
            Contexto contexto, Long processoId, LocalDate referencia, List<Movimentacao> movimentos) {
        List<Movimentacao> ordenados = movimentos.stream()
                .sorted(Comparator.comparing(Movimentacao::getDataMovimentacao)
                        .thenComparingInt(Movimentacao::getSequenciaDiaria)).toList();
        List<PermanenciaLida> permanencias = calculadora.calcular(ordenados, referencia).stream()
                .map(periodo -> new PermanenciaLida(periodo.movimentacaoChegadaId(),
                        setor(periodo.setor()), periodo.dataChegada(), periodo.dataSaida(),
                        periodo.diasCorridos(), periodo.aberta())).toList();
        Map<Long, PermanenciaLida> porChegada = permanencias.stream().collect(
                Collectors.toMap(PermanenciaLida::movimentacaoChegadaId, periodo -> periodo));
        List<MovimentoLido> lidos = ordenados.stream().map(movimento -> new MovimentoLido(
                movimento.getId(), movimento.getDataMovimentacao(), movimento.getSequenciaDiaria(),
                setor(movimento.getSetorDestino()), autor(movimento.getAutor()),
                movimento.getObservacao(), movimento.getInseridoEm(),
                porChegada.get(movimento.getId()))).toList();
        return new HistoricoLido(processoId, contexto, referencia,
                lidos.isEmpty() ? null : lidos.getLast().setorDestino(), lidos, permanencias);
    }

    private static List<Long> ids(Collection<Long> valores) {
        return valores.stream().map(Objects::requireNonNull).distinct().toList();
    }

    private static void emLotes(List<Long> valores, Consumer<List<Long>> operacao) {
        for (int inicio = 0; inicio < valores.size(); inicio += TAMANHO_LOTE) {
            operacao.accept(valores.subList(inicio, Math.min(inicio + TAMANHO_LOTE, valores.size())));
        }
    }

    private static SetorLido setor(Setor setor) {
        return new SetorLido(setor.getId(), setor.getSigla(), setor.getNome(), setor.isAtivo());
    }

    private static AutorLido autor(UsuarioInterno autor) {
        return new AutorLido(autor.getId(), autor.getNome(), autor.getEmail(), autor.getLogin(),
                autor.getPerfil(), autor.isAtivo(), autor.isSenhaTemporaria());
    }

    public record Contexto(ContextoTramitacao tipo, Long id) {
        public Contexto { Objects.requireNonNull(tipo); Objects.requireNonNull(id); }
    }
    public record SetorLido(Long id, String sigla, String nome, boolean ativo) {}
    public record AutorLido(Long id, String nome, String email, String login,
                            PerfilAcesso perfil, boolean ativo, boolean senhaTemporaria) {}
    public record PermanenciaLida(Long movimentacaoChegadaId, SetorLido setor,
                                 LocalDate dataChegada, LocalDate dataSaida,
                                 long diasCorridos, boolean aberta) {}
    public record MovimentoLido(Long id, LocalDate dataMovimentacao, int sequenciaDiaria,
                               SetorLido setorDestino, AutorLido autor, String observacao,
                               LocalDateTime inseridoEm, PermanenciaLida permanenciaIniciada) {}
    public record HistoricoLido(Long processoId, Contexto contexto, LocalDate referencia,
                               SetorLido setorAtual, List<MovimentoLido> movimentacoes,
                               List<PermanenciaLida> permanencias) {
        public HistoricoLido {
            movimentacoes = List.copyOf(movimentacoes);
            permanencias = List.copyOf(permanencias);
        }
    }
}
