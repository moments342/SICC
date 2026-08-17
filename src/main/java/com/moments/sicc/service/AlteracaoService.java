package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.AlteracaoCampo;
import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.EstadoAlteracao;
import com.moments.sicc.domain.Enums.OperacaoAlteracao;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.AlteracaoCampoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlteracaoService {
    private final AlteracaoContratualRepository alteracoes;
    private final AlteracaoCampoRepository campos;
    private final InstrumentoContratualRepository instrumentos;
    private final DocumentoRepository documentos;
    private final VersaoDocumentoRepository versoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    @Transactional
    public AlteracaoResponse criar(CriarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        InstrumentoContratual instrumento = instrumentos.findById(request.instrumentoId())
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado."));
        Map<CampoInstrumento, String> mudancas = request.alteracoes() == null ? Map.of() : request.alteracoes();
        if (request.operacao() == OperacaoAlteracao.CANCELAMENTO) {
            if (request.referenciaId() == null) throw new DomainException("Cancelamento exige alteração de referência.");
            if (!mudancas.isEmpty()) throw new DomainException("Cancelamento não define novos valores.");
        } else if (mudancas.isEmpty()) {
            throw new DomainException("A alteração deve informar ao menos um campo.");
        }
        if (request.operacao() != OperacaoAlteracao.ORIGINAL && request.referenciaId() == null) {
            throw new DomainException("Retificação ou cancelamento exige alteração de referência.");
        }
        AlteracaoContratual referencia = request.referenciaId() == null ? null
                : alteracoes.findById(request.referenciaId())
                        .orElseThrow(() -> new NotFoundException("Alteração de referência não encontrada."));
        if (referencia != null && !referencia.getInstrumento().getId().equals(instrumento.getId())) {
            throw new DomainException("A referência pertence a outro Instrumento Contratual.");
        }
        if (referencia != null && referencia.getEstado() != EstadoAlteracao.EFETIVADA) {
            throw new DomainException("A referência deve ser uma alteração já efetivada.");
        }
        if (referencia != null && referencia.getOperacao() == OperacaoAlteracao.CANCELAMENTO) {
            throw new DomainException("Cancelamentos não podem ser retificados ou cancelados.");
        }
        if (referencia != null && !referenciaVigente(referencia)) {
            throw new DomainException("A referência já foi cancelada ou depende de uma alteração cancelada.");
        }
        if (request.tipo() == TipoAlteracao.APOSTILAMENTO
                && mudancas.keySet().stream().anyMatch(c -> !c.permitidoEmApostilamento())) {
            throw new DomainException("Apostilamento contém campo de natureza contratual.");
        }
        AlteracaoContratual alteracao = new AlteracaoContratual();
        alteracao.setInstrumento(instrumento);
        alteracao.setTipo(request.tipo());
        alteracao.setNumeroOficial(request.numeroOficial().trim());
        alteracao.setOperacao(request.operacao());
        alteracao.setReferencia(referencia);
        alteracoes.save(alteracao);
        mudancas.forEach((campo, novoValor) -> {
            AlteracaoCampo item = new AlteracaoCampo();
            item.setAlteracao(alteracao);
            item.setCampo(campo);
            item.setValorAnterior(valorAtual(instrumento, campo));
            item.setValorNovo(novoValor);
            campos.save(item);
        });
        auditoria.registrar(autor, "CRIAR_ALTERACAO", "ALTERACAO_CONTRATUAL", alteracao.getId(), true, null, ip);
        return response(alteracao);
    }

    @Transactional
    public AlteracaoResponse efetivar(Long id, EfetivarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        AlteracaoContratual alteracao = alteracoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        if (alteracao.getEstado() != EstadoAlteracao.RASCUNHO) {
            throw new DomainException("Alteração efetivada é imutável.");
        }
        if (request.dataEfetivacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de efetivação não pode ser futura.");
        }
        Documento documento = documentoAssinado(request.documentoAssinadoId(), alteracao);
        boolean ordemOcupada = alteracoes.findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(
                        alteracao.getInstrumento().getId()).stream()
                .anyMatch(a -> a.getEstado() == EstadoAlteracao.EFETIVADA
                        && request.dataEfetivacao().equals(a.getDataEfetivacao())
                        && request.ordemOficial().equals(a.getOrdemOficial()));
        if (ordemOcupada) throw new DomainException("A ordem oficial já foi usada nesta data.");
        alteracao.setDataEfetivacao(request.dataEfetivacao());
        alteracao.setOrdemOficial(request.ordemOficial());
        alteracao.setDocumentoAssinado(documento);
        alteracao.setEstado(EstadoAlteracao.EFETIVADA);
        recomputar(alteracao.getInstrumento());
        String acaoAuditoria = switch (alteracao.getOperacao()) {
            case ORIGINAL -> "EFETIVAR_ALTERACAO";
            case RETIFICACAO -> "RETIFICAR_ALTERACAO";
            case CANCELAMENTO -> "CANCELAR_ALTERACAO";
        };
        auditoria.registrar(autor, acaoAuditoria, "ALTERACAO_CONTRATUAL", id, true, null, ip);
        return response(alteracao);
    }

    @Transactional(readOnly = true)
    public List<AlteracaoResponse> listar(Long instrumentoId) {
        return alteracoes.findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(instrumentoId)
                .stream().map(this::response).toList();
    }

    private void recomputar(InstrumentoContratual instrumento) {
        List<AlteracaoContratual> efetivadas = alteracoes
                .findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(instrumento.getId()).stream()
                .filter(a -> a.getEstado() == EstadoAlteracao.EFETIVADA)
                .sorted(Comparator.comparing(AlteracaoContratual::getDataEfetivacao)
                        .thenComparing(AlteracaoContratual::getOrdemOficial))
                .toList();
        Map<Long, AlteracaoContratual> porId = new HashMap<>();
        efetivadas.forEach(a -> porId.put(a.getId(), a));
        Set<Long> canceladas = new HashSet<>();
        efetivadas.stream()
                .filter(a -> a.getOperacao() == OperacaoAlteracao.CANCELAMENTO && a.getReferencia() != null)
                .forEach(a -> canceladas.add(a.getReferencia().getId()));
        Map<Long, Boolean> validade = new HashMap<>();
        List<AlteracaoContratual> vigentes = efetivadas.stream()
                .filter(a -> a.getOperacao() != OperacaoAlteracao.CANCELAMENTO)
                .filter(a -> vigente(a, porId, canceladas, validade, new HashSet<>()))
                .toList();
        Map<Long, Set<CampoInstrumento>> camposSubstituidos = new HashMap<>();
        vigentes.stream()
                .filter(a -> a.getOperacao() == OperacaoAlteracao.RETIFICACAO && a.getReferencia() != null)
                .forEach(a -> campos.findByAlteracaoId(a.getId()).forEach(c ->
                        camposSubstituidos.computeIfAbsent(
                                a.getReferencia().getId(), ignored -> new HashSet<>()).add(c.getCampo())));
        EnumMap<CampoInstrumento, String> base = new EnumMap<>(CampoInstrumento.class);
        efetivadas.forEach(a -> campos.findByAlteracaoId(a.getId()).forEach(c ->
                base.putIfAbsent(c.getCampo(), c.getValorAnterior())));
        base.forEach((campo, valor) -> aplicar(instrumento, campo, valor));
        vigentes.stream()
                .forEach(a -> campos.findByAlteracaoId(a.getId())
                        .stream()
                        .filter(c -> !camposSubstituidos
                                .getOrDefault(a.getId(), Set.of()).contains(c.getCampo()))
                        .forEach(c -> aplicar(instrumento, c.getCampo(), c.getValorNovo())));
        instrumento.getProcesso().setStatus(instrumento.getVigenciaContratualFinal().isBefore(LocalDate.now(clock))
                ? StatusProcesso.CONCLUIDO : StatusProcesso.EM_VIGENCIA);
    }

    private boolean referenciaVigente(AlteracaoContratual referencia) {
        List<AlteracaoContratual> efetivadas = alteracoes
                .findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(
                        referencia.getInstrumento().getId()).stream()
                .filter(a -> a.getEstado() == EstadoAlteracao.EFETIVADA)
                .toList();
        Map<Long, AlteracaoContratual> porId = new HashMap<>();
        efetivadas.forEach(a -> porId.put(a.getId(), a));
        Set<Long> canceladas = new HashSet<>();
        efetivadas.stream()
                .filter(a -> a.getOperacao() == OperacaoAlteracao.CANCELAMENTO && a.getReferencia() != null)
                .forEach(a -> canceladas.add(a.getReferencia().getId()));
        return vigente(referencia, porId, canceladas, new HashMap<>(), new HashSet<>());
    }

    private boolean vigente(AlteracaoContratual alteracao, Map<Long, AlteracaoContratual> porId,
            Set<Long> canceladas, Map<Long, Boolean> memo, Set<Long> visitando) {
        if (alteracao == null || alteracao.getEstado() != EstadoAlteracao.EFETIVADA
                || alteracao.getOperacao() == OperacaoAlteracao.CANCELAMENTO
                || canceladas.contains(alteracao.getId())) {
            return false;
        }
        Boolean calculado = memo.get(alteracao.getId());
        if (calculado != null) return calculado;
        if (!visitando.add(alteracao.getId())) return false;
        boolean resultado = alteracao.getOperacao() == OperacaoAlteracao.ORIGINAL
                || vigente(porId.get(alteracao.getReferencia().getId()),
                        porId, canceladas, memo, visitando);
        visitando.remove(alteracao.getId());
        memo.put(alteracao.getId(), resultado);
        return resultado;
    }

    private String valorAtual(InstrumentoContratual i, CampoInstrumento campo) {
        return switch (campo) {
            case OBJETO -> i.getObjeto();
            case DESCRICAO -> i.getDescricao();
            case NATUREZA -> i.getNatureza();
            case COORDENADOR -> i.getCoordenador();
            case PARTICIPES -> i.getParticipes();
            case VALOR_ATUAL -> i.getValorAtual().toPlainString();
            case VIGENCIA_CONTRATUAL_FINAL -> i.getVigenciaContratualFinal().toString();
            case VIGENCIA_TED_FINAL -> i.getVigenciaTedFinal() == null ? null : i.getVigenciaTedFinal().toString();
        };
    }

    private void aplicar(InstrumentoContratual i, CampoInstrumento campo, String valor) {
        switch (campo) {
            case OBJETO -> i.setObjeto(valor);
            case DESCRICAO -> i.setDescricao(valor);
            case NATUREZA -> i.setNatureza(valor);
            case COORDENADOR -> i.setCoordenador(valor);
            case PARTICIPES -> i.setParticipes(valor);
            case VALOR_ATUAL -> i.setValorAtual(new BigDecimal(valor));
            case VIGENCIA_CONTRATUAL_FINAL -> i.setVigenciaContratualFinal(LocalDate.parse(valor));
            case VIGENCIA_TED_FINAL -> i.setVigenciaTedFinal(valor == null || valor.isBlank() ? null : LocalDate.parse(valor));
        }
    }

    private Documento documentoAssinado(Long id, AlteracaoContratual alteracao) {
        Documento d = documentos.findById(id).orElseThrow(() -> new NotFoundException("Documento não encontrado."));
        if (!d.isAtivo() || d.getCategoria() != CategoriaDocumento.ASSINADO) {
            throw new DomainException("Efetivação exige Documento Assinado ativo.");
        }
        ProprietarioDocumento tipoEsperado = alteracao.getTipo() == TipoAlteracao.TERMO_ADITIVO
                ? ProprietarioDocumento.TERMO_ADITIVO : ProprietarioDocumento.APOSTILAMENTO;
        if (d.getProprietarioTipo() != tipoEsperado || !d.getProprietarioId().equals(alteracao.getId())) {
            throw new DomainException("O Documento Assinado pertence a outra alteração.");
        }
        var latest = versoes.findByDocumentoIdOrderByVersaoDesc(id).stream().findFirst()
                .orElseThrow(() -> new DomainException("Documento não possui versão."));
        if (!"application/pdf".equals(latest.getTipoMime())) throw new DomainException("Documento Assinado deve ser PDF.");
        return d;
    }

    private AlteracaoResponse response(AlteracaoContratual a) {
        EnumMap<CampoInstrumento, String> map = new EnumMap<>(CampoInstrumento.class);
        campos.findByAlteracaoId(a.getId()).forEach(c -> map.put(c.getCampo(), c.getValorNovo()));
        return new AlteracaoResponse(a.getId(), a.getInstrumento().getId(), a.getTipo(), a.getEstado(),
                a.getNumeroOficial(), a.getOrdemOficial(), a.getDataEfetivacao(), a.getOperacao(),
                a.getReferencia() == null ? null : a.getReferencia().getId(), map);
    }
}
