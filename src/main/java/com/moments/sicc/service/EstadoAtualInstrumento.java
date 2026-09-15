package com.moments.sicc.service;

import com.moments.sicc.domain.AlteracaoCampo;
import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.Enums.EstadoAlteracao;
import com.moments.sicc.domain.Enums.OperacaoAlteracao;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.shared.exception.DomainException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EstadoAtualInstrumento {
    private static final Comparator<AlteracaoContratual> ORDEM_CRONOLOGICA = Comparator
            .comparing(AlteracaoContratual::getDataEfetivacao,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlteracaoContratual::getOrdemOficial,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlteracaoContratual::getId);

    private final EntityManager entityManager;
    private final CatalogoCamposInstrumento catalogoCampos;
    private final RegrasDeVigencia regrasDeVigencia;

    public Avaliacao avaliar(InstrumentoContratual instrumento) {
        Long instrumentoId = Objects.requireNonNull(instrumento.getId());
        List<AlteracaoContratual> alteracoes = entityManager.createQuery("""
                        select a
                        from AlteracaoContratual a
                        left join fetch a.referencia
                        where a.instrumento.id = :instrumentoId
                        """, AlteracaoContratual.class)
                .setParameter("instrumentoId", instrumentoId)
                .getResultList();
        List<AlteracaoCampo> campos = entityManager.createQuery("""
                        select c
                        from AlteracaoCampo c
                        join fetch c.alteracao a
                        where a.instrumento.id = :instrumentoId
                        order by c.id
                        """, AlteracaoCampo.class)
                .setParameter("instrumentoId", instrumentoId)
                .getResultList();
        InstrumentoEstadoInicial estadoInicial = entityManager.find(
                InstrumentoEstadoInicial.class, instrumentoId);
        if (estadoInicial == null) {
            throw new DomainException(
                    "O estado inicial do Instrumento Contratual não foi preservado.");
        }
        return new Avaliacao(
                instrumento,
                estadoInicial,
                alteracoes,
                campos,
                regrasDeVigencia.referenciaAtual());
    }

    public final class Avaliacao {
        private final InstrumentoContratual instrumento;
        private final InstrumentoEstadoInicial estadoInicial;
        private final List<AlteracaoContratual> alteracoes;
        private final Map<Long, List<AlteracaoCampo>> camposPorAlteracao;
        private final RegrasDeVigencia.ReferenciaDeVigencia referenciaTemporal;
        private final Map<Long, List<RegistroCadeia>> cadeias = new HashMap<>();
        private final Map<PosicaoCronologia, Estado> estadosHistoricos = new HashMap<>();
        private Contexto contextoAtual;
        private Estado estadoAtual;

        private Avaliacao(
                InstrumentoContratual instrumento,
                InstrumentoEstadoInicial estadoInicial,
                List<AlteracaoContratual> alteracoes,
                List<AlteracaoCampo> campos,
                RegrasDeVigencia.ReferenciaDeVigencia referenciaTemporal) {
            this.instrumento = instrumento;
            this.estadoInicial = estadoInicial;
            this.alteracoes = new ArrayList<>(alteracoes);
            this.alteracoes.sort(ORDEM_CRONOLOGICA);
            this.referenciaTemporal = referenciaTemporal;
            this.camposPorAlteracao = new LinkedHashMap<>();
            campos.forEach(campo -> camposPorAlteracao
                    .computeIfAbsent(campo.getAlteracao().getId(), ignored -> new ArrayList<>())
                    .add(campo));
        }

        public List<AlteracaoContratual> alteracoes() {
            return List.copyOf(alteracoes);
        }

        public List<AlteracaoCampo> mudancas(AlteracaoContratual alteracao) {
            return List.copyOf(camposPorAlteracao.getOrDefault(alteracao.getId(), List.of()));
        }

        public void incluir(
                AlteracaoContratual alteracao, List<AlteracaoCampo> mudancas) {
            alteracoes.add(alteracao);
            alteracoes.sort(ORDEM_CRONOLOGICA);
            camposPorAlteracao.put(alteracao.getId(), new ArrayList<>(mudancas));
            invalidarResultados();
        }

        public void substituirMudancas(
                AlteracaoContratual alteracao, List<AlteracaoCampo> mudancas) {
            camposPorAlteracao.put(alteracao.getId(), new ArrayList<>(mudancas));
            invalidarResultados();
        }

        public boolean referenciaVigente(AlteracaoContratual referencia) {
            return contextoAtual().vigentes().stream()
                    .anyMatch(item -> item.getId().equals(referencia.getId()));
        }

        public void efetivar(
                AlteracaoContratual alteracao,
                AlteracaoContratual referenciaBloqueada,
                LocalDate dataEfetivacao,
                Integer ordemOficial,
                Documento documentoAssinado) {
            PosicaoCronologia posicao = new PosicaoCronologia(
                    dataEfetivacao, ordemOficial);
            if (alteracao.getReferencia() != null) {
                if (referenciaBloqueada == null
                        || !alteracao.getReferencia().getId().equals(
                                referenciaBloqueada.getId())) {
                    throw new IllegalArgumentException(
                            "A referência bloqueada deve corresponder à alteração.");
                }
                validarReferenciaParaEfetivacao(referenciaBloqueada, posicao);
            }
            boolean ordemOcupada = efetivadas(ignored -> true).stream()
                    .anyMatch(item -> posicao(item).equals(posicao));
            if (ordemOcupada) {
                throw new DomainException("A ordem oficial já foi usada nesta data.");
            }
            if (alteracao.getOperacao() != OperacaoAlteracao.CANCELAMENTO) {
                validarBaseQuePrevalecera(alteracao, posicao);
                mudancas(alteracao).forEach(mudanca -> mudanca.setValorAnterior(
                        estadoAntes(posicao).valores().get(mudanca.getCampo())));
            }
            alteracao.setDataEfetivacao(dataEfetivacao);
            alteracao.setOrdemOficial(ordemOficial);
            alteracao.setDocumentoAssinado(documentoAssinado);
            alteracao.setEstado(EstadoAlteracao.EFETIVADA);
            aplicarEstadoAtual();
        }

        public Estado estadoAtual() {
            if (estadoAtual == null) {
                estadoAtual = estado(contextoAtual());
            }
            return estadoAtual;
        }

        private void aplicarEstadoAtual() {
            invalidarResultados();
            Estado estado = estadoAtual();
            estado.valores().forEach((campo, valor) ->
                    catalogoCampos.aplicar(instrumento, campo, valor));
            instrumento.getProcesso().setStatus(estado.statusProcesso());
        }

        private void validarReferenciaParaEfetivacao(
                AlteracaoContratual referencia, PosicaoCronologia posicao) {
            if (!referenciaVigente(referencia)) {
                throw new DomainException(
                        "A referência já foi cancelada ou depende de uma alteração cancelada.");
            }
            if (posicao(referencia).compareTo(posicao) >= 0) {
                throw new DomainException(
                        "A operação deve ser posterior à alteração de referência na cronologia oficial.");
            }
        }

        private void validarBaseQuePrevalecera(
                AlteracaoContratual alteracao, PosicaoCronologia posicao) {
            Map<CampoInstrumento, AlteracaoContratual> prevalentes =
                    prevalentes(contextoAtual());
            mudancas(alteracao).forEach(mudanca -> {
                AlteracaoContratual atual = prevalentes.get(mudanca.getCampo());
                boolean candidatoPrevalece = atual == null
                        || posicao(atual).compareTo(posicao) < 0;
                if (candidatoPrevalece
                        && !Objects.equals(
                                catalogoCampos.valorAtual(instrumento, mudanca.getCampo()),
                                mudanca.getValorAnterior())) {
                    throw new DomainException(
                            "O valor anterior de %s ficou desatualizado; revise o rascunho antes de efetivar."
                                    .formatted(mudanca.getCampo()));
                }
            });
        }

        public List<RegistroCadeia> cadeia(AlteracaoContratual selecionada) {
            Long raizId = raiz(selecionada).getId();
            return cadeias.computeIfAbsent(raizId, ignored -> calcularCadeia(raizId));
        }

        private List<RegistroCadeia> calcularCadeia(Long raizId) {
            Set<Long> produtorasAtuais = estadoAtual().precedenciaPorCampo().values().stream()
                    .map(Precedencia::alteracaoId)
                    .collect(java.util.stream.Collectors.toSet());
            List<AlteracaoContratual> registros = alteracoes.stream()
                    .filter(item -> raiz(item).getId().equals(raizId))
                    .sorted(ORDEM_CRONOLOGICA)
                    .toList();
            return registros.stream()
                    .map(item -> new RegistroCadeia(
                            item,
                            produtorasAtuais.contains(item.getId()),
                            valoresProduzidos(item, registros)))
                    .toList();
        }

        private Map<CampoInstrumento, String> valoresProduzidos(
                AlteracaoContratual alteracao, List<AlteracaoContratual> cadeia) {
            EnumMap<CampoInstrumento, String> valores = new EnumMap<>(CampoInstrumento.class);
            if (alteracao.getOperacao() != OperacaoAlteracao.CANCELAMENTO) {
                mudancas(alteracao).forEach(campo ->
                        valores.put(campo.getCampo(), campo.getValorNovo()));
                return Collections.unmodifiableMap(new EnumMap<>(valores));
            }
            if (alteracao.getEstado() != EstadoAlteracao.EFETIVADA) {
                return Map.of();
            }
            AlteracaoContratual referencia = alteracao.getReferencia();
            Estado estadoNoCancelamento = estadoAte(posicao(alteracao));
            cadeia.stream()
                    .filter(item -> item.getOperacao() != OperacaoAlteracao.CANCELAMENTO)
                    .filter(item -> descendeDe(item, referencia))
                    .flatMap(item -> mudancas(item).stream())
                    .map(AlteracaoCampo::getCampo)
                    .distinct()
                    .forEach(campo -> valores.put(
                            campo, estadoNoCancelamento.valores().get(campo)));
            return Collections.unmodifiableMap(new EnumMap<>(valores));
        }

        private Estado estadoAntes(PosicaoCronologia limite) {
            return estado(posicaoAnteriorA(limite));
        }

        private Estado estadoAte(PosicaoCronologia limite) {
            return estadosHistoricos.computeIfAbsent(
                    limite, posicao -> estado(posicaoAte(posicao)));
        }

        private Estado estado(Predicate<PosicaoCronologia> incluir) {
            return estado(contexto(efetivadas(incluir)));
        }

        private Estado estado(Contexto contexto) {
            Map<CampoInstrumento, AlteracaoContratual> prevalentes = prevalentes(contexto);
            EnumMap<CampoInstrumento, String> valores = new EnumMap<>(CampoInstrumento.class);
            EnumMap<CampoInstrumento, Precedencia> precedencia =
                    new EnumMap<>(CampoInstrumento.class);
            for (CampoInstrumento campo : CampoInstrumento.values()) {
                valores.put(campo, catalogoCampos.valorInicial(estadoInicial, campo));
            }
            prevalentes.forEach((campo, alteracao) -> {
                AlteracaoCampo mudancaPrevalente = mudancas(alteracao).stream()
                        .filter(mudanca -> mudanca.getCampo() == campo)
                        .findFirst()
                        .orElseThrow();
                valores.put(campo, mudancaPrevalente.getValorNovo());
                precedencia.put(campo, new Precedencia(
                        alteracao.getId(), alteracao.getDataEfetivacao(),
                        alteracao.getOrdemOficial()));
            });
            LocalDate vigencia = LocalDate.parse(valores.get(
                    CampoInstrumento.VIGENCIA_CONTRATUAL_FINAL));
            StatusProcesso status = referenciaTemporal.status(vigencia);
            return new Estado(
                    Collections.unmodifiableMap(new EnumMap<>(valores)),
                    status,
                    Collections.unmodifiableMap(new EnumMap<>(precedencia)));
        }

        private Contexto contextoAtual() {
            if (contextoAtual == null) {
                contextoAtual = contexto(efetivadas(ignored -> true));
            }
            return contextoAtual;
        }

        private void invalidarResultados() {
            contextoAtual = null;
            estadoAtual = null;
            cadeias.clear();
            estadosHistoricos.clear();
        }

        private List<AlteracaoContratual> efetivadas(
                Predicate<PosicaoCronologia> incluir) {
            return alteracoes.stream()
                    .filter(item -> item.getEstado() == EstadoAlteracao.EFETIVADA)
                    .filter(item -> incluir.test(posicao(item)))
                    .sorted(ORDEM_CRONOLOGICA)
                    .toList();
        }

        private Contexto contexto(List<AlteracaoContratual> efetivadas) {
            Map<Long, AlteracaoContratual> porId = new HashMap<>();
            efetivadas.forEach(item -> porId.put(item.getId(), item));
            Set<Long> canceladas = new HashSet<>();
            efetivadas.stream()
                    .filter(item -> item.getOperacao() == OperacaoAlteracao.CANCELAMENTO)
                    .filter(item -> item.getReferencia() != null)
                    .forEach(item -> canceladas.add(item.getReferencia().getId()));
            Map<Long, Boolean> validade = new HashMap<>();
            List<AlteracaoContratual> vigentes = efetivadas.stream()
                    .filter(item -> item.getOperacao() != OperacaoAlteracao.CANCELAMENTO)
                    .filter(item -> vigente(
                            item, porId, canceladas, validade, new HashSet<>()))
                    .toList();
            Map<Long, Set<CampoInstrumento>> camposSubstituidos = new HashMap<>();
            vigentes.stream()
                    .filter(item -> item.getOperacao() == OperacaoAlteracao.RETIFICACAO)
                    .filter(item -> item.getReferencia() != null)
                    .forEach(item -> mudancas(item).forEach(campo -> camposSubstituidos
                            .computeIfAbsent(
                                    item.getReferencia().getId(), ignored -> new HashSet<>())
                            .add(campo.getCampo())));
            return new Contexto(vigentes, camposSubstituidos);
        }

        private Map<CampoInstrumento, AlteracaoContratual> prevalentes(Contexto contexto) {
            EnumMap<CampoInstrumento, AlteracaoContratual> prevalentes =
                    new EnumMap<>(CampoInstrumento.class);
            contexto.vigentes().forEach(item -> mudancas(item).stream()
                    .filter(campo -> !contexto.camposSubstituidos()
                            .getOrDefault(item.getId(), Set.of()).contains(campo.getCampo()))
                    .forEach(campo -> prevalentes.put(campo.getCampo(), item)));
            return prevalentes;
        }

        private boolean vigente(
                AlteracaoContratual alteracao,
                Map<Long, AlteracaoContratual> porId,
                Set<Long> canceladas,
                Map<Long, Boolean> memo,
                Set<Long> visitando) {
            if (alteracao == null
                    || alteracao.getEstado() != EstadoAlteracao.EFETIVADA
                    || alteracao.getOperacao() == OperacaoAlteracao.CANCELAMENTO
                    || canceladas.contains(alteracao.getId())) {
                return false;
            }
            Boolean calculado = memo.get(alteracao.getId());
            if (calculado != null) return calculado;
            if (!visitando.add(alteracao.getId())) return false;
            boolean resultado = alteracao.getOperacao() == OperacaoAlteracao.ORIGINAL
                    || vigente(
                            porId.get(alteracao.getReferencia().getId()),
                            porId, canceladas, memo, visitando);
            visitando.remove(alteracao.getId());
            memo.put(alteracao.getId(), resultado);
            return resultado;
        }

        private AlteracaoContratual raiz(AlteracaoContratual alteracao) {
            AlteracaoContratual atual = alteracao;
            Set<Long> visitados = new HashSet<>();
            while (atual.getReferencia() != null && visitados.add(atual.getId())) {
                atual = atual.getReferencia();
            }
            return atual;
        }

        private boolean descendeDe(
                AlteracaoContratual candidata, AlteracaoContratual ancestral) {
            AlteracaoContratual atual = candidata;
            Set<Long> visitados = new HashSet<>();
            while (atual != null && visitados.add(atual.getId())) {
                if (atual.getId().equals(ancestral.getId())) return true;
                atual = atual.getReferencia();
            }
            return false;
        }
    }

    private static PosicaoCronologia posicao(AlteracaoContratual alteracao) {
        return new PosicaoCronologia(
                alteracao.getDataEfetivacao(), alteracao.getOrdemOficial());
    }

    private static Predicate<PosicaoCronologia> posicaoAnteriorA(
            PosicaoCronologia limite) {
        return posicao -> posicao.compareTo(limite) < 0;
    }

    private static Predicate<PosicaoCronologia> posicaoAte(
            PosicaoCronologia limite) {
        return posicao -> posicao.compareTo(limite) <= 0;
    }

    public record PosicaoCronologia(LocalDate dataEfetivacao, Integer ordemOficial)
            implements Comparable<PosicaoCronologia> {
        @Override
        public int compareTo(PosicaoCronologia outra) {
            int porData = dataEfetivacao.compareTo(outra.dataEfetivacao);
            return porData != 0 ? porData : ordemOficial.compareTo(outra.ordemOficial);
        }
    }

    public record Precedencia(
            Long alteracaoId, LocalDate dataEfetivacao, Integer ordemOficial) {}

    public record Estado(
            Map<CampoInstrumento, String> valores,
            StatusProcesso statusProcesso,
            Map<CampoInstrumento, Precedencia> precedenciaPorCampo) {}

    public record RegistroCadeia(
            AlteracaoContratual alteracao,
            boolean produzEfeitoAtual,
            Map<CampoInstrumento, String> valoresProduzidos) {}

    private record Contexto(
            List<AlteracaoContratual> vigentes,
            Map<Long, Set<CampoInstrumento>> camposSubstituidos) {}
}
