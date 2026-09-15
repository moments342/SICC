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
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.AlteracaoCampoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.service.ProcessoMutacaoGuard.ContextoAlteracao;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final SiccService siccService;
    private final ProcessoMutacaoGuard processoMutacao;
    private final CatalogoCamposInstrumento catalogoCampos;
    private final EstadoAtualInstrumento estadoAtualInstrumento;

    @Transactional
    public AlteracaoResponse criar(CriarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        InstrumentoContratual instrumento = processoMutacao
                .instrumentoEmProcessoAtivo(request.instrumentoId())
                .instrumento();
        List<MudancaAlteracaoRequest> mudancas = request.mudancas() == null
                ? List.of() : request.mudancas();
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
        if (referencia != null && referencia.getTipo() != request.tipo()) {
            throw new DomainException("A referência deve possuir o mesmo tipo da alteração.");
        }
        if (referencia != null && referencia.getEstado() != EstadoAlteracao.EFETIVADA) {
            throw new DomainException("A referência deve ser uma alteração já efetivada.");
        }
        if (referencia != null && referencia.getOperacao() == OperacaoAlteracao.CANCELAMENTO) {
            throw new DomainException("Cancelamentos não podem ser retificados ou cancelados.");
        }
        EstadoAtualInstrumento.Avaliacao avaliacao = estadoAtualInstrumento.avaliar(instrumento);
        if (referencia != null && !avaliacao.referenciaVigente(referencia)) {
            throw new DomainException("A referência já foi cancelada ou depende de uma alteração cancelada.");
        }
        if (request.operacao() != OperacaoAlteracao.CANCELAMENTO) {
            validarMudancas(instrumento, request.tipo(), mudancas);
        }
        AlteracaoContratual alteracao = new AlteracaoContratual();
        alteracao.setInstrumento(instrumento);
        alteracao.setTipo(request.tipo());
        alteracao.setNumeroOficial(request.numeroOficial().trim());
        alteracao.setOperacao(request.operacao());
        alteracao.setReferencia(referencia);
        alteracao.setCriadoEm(LocalDateTime.now(clock));
        alteracoes.save(alteracao);
        List<AlteracaoCampo> camposSalvos = salvarMudancas(alteracao, mudancas);
        avaliacao.incluir(alteracao, camposSalvos);
        String entidade = request.tipo().entidadeAuditoria();
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_" + entidade, entidade, alteracao.getId(), true, null, ip);
        return response(alteracao, avaliacao);
    }

    @Transactional
    public AlteracaoResponse atualizar(
            Long id, AtualizarRascunhoAlteracaoRequest request, UsuarioInterno autor, String ip) {
        ContextoAlteracao contexto = processoMutacao.alteracaoEmProcessoAtivo(id);
        AlteracaoContratual alteracao = contexto.alteracao();
        if (alteracao.getEstado() != EstadoAlteracao.RASCUNHO) {
            throw new DomainException("Alteração efetivada é imutável.");
        }
        if (alteracao.getOperacao() == OperacaoAlteracao.CANCELAMENTO) {
            throw new DomainException("Rascunho de cancelamento não possui mudanças editáveis.");
        }
        InstrumentoContratual instrumento = contexto.instrumento();
        EstadoAtualInstrumento.Avaliacao avaliacao = estadoAtualInstrumento.avaliar(instrumento);
        validarMudancas(instrumento, alteracao.getTipo(), request.mudancas());
        alteracao.setNumeroOficial(request.numeroOficial().trim());
        campos.deleteAll(campos.findByAlteracaoId(id));
        campos.flush();
        List<AlteracaoCampo> camposSalvos = salvarMudancas(alteracao, request.mudancas());
        avaliacao.substituirMudancas(alteracao, camposSalvos);
        String entidade = alteracao.getTipo().entidadeAuditoria();
        auditoria.registrarNaTransacaoAtual(
                autor, "EDITAR_" + entidade, entidade, id, true, null, ip);
        return response(alteracao, avaliacao);
    }

    @Transactional
    public AlteracaoResponse efetivar(Long id, EfetivarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        ContextoAlteracao contexto = processoMutacao.alteracaoEmProcessoAtivo(id);
        AlteracaoContratual alteracao = contexto.alteracao();
        if (alteracao.getEstado() != EstadoAlteracao.RASCUNHO) {
            throw new DomainException("Alteração efetivada é imutável.");
        }
        InstrumentoContratual instrumento = contexto.instrumento();
        AlteracaoContratual referenciaBloqueada = null;
        if (alteracao.getReferencia() != null) {
            referenciaBloqueada = alteracoes.findByIdForUpdate(alteracao.getReferencia().getId())
                    .orElseThrow(() -> new NotFoundException("Alteração de referência não encontrada."));
        }
        EstadoAtualInstrumento.Avaliacao avaliacao = estadoAtualInstrumento.avaliar(instrumento);
        if (request.dataEfetivacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de efetivação não pode ser futura.");
        }
        Documento documento = documentoAssinado(request.documentoAssinadoId(), alteracao);
        avaliacao.efetivar(
                alteracao,
                referenciaBloqueada,
                request.dataEfetivacao(),
                request.ordemOficial(),
                documento);
        String acaoAuditoria = switch (alteracao.getOperacao()) {
            case ORIGINAL -> "EFETIVAR_ALTERACAO";
            case RETIFICACAO -> "RETIFICAR_ALTERACAO";
            case CANCELAMENTO -> "CANCELAR_ALTERACAO";
        };
        auditoria.registrarNaTransacaoAtual(
                autor, acaoAuditoria, "ALTERACAO_CONTRATUAL", id, true, null, ip);
        auditoria.registrarNaTransacaoAtual(
                autor, "RECOMPUTAR_ESTADO_INSTRUMENTO", "INSTRUMENTO_CONTRATUAL",
                alteracao.getInstrumento().getId(), true,
                "alteracaoId=" + id + "; operacao=" + alteracao.getOperacao(), ip);
        return response(alteracao, avaliacao);
    }

    @Transactional(readOnly = true)
    public List<AlteracaoResponse> listar(Long instrumentoId) {
        InstrumentoContratual instrumento = instrumentos.findById(instrumentoId)
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado."));
        EstadoAtualInstrumento.Avaliacao avaliacao = estadoAtualInstrumento.avaliar(instrumento);
        return avaliacao.alteracoes().stream()
                .map(alteracao -> response(alteracao, avaliacao))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlteracaoResponse buscar(Long id) {
        AlteracaoContratual alteracao = alteracoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        EstadoAtualInstrumento.Avaliacao avaliacao =
                estadoAtualInstrumento.avaliar(alteracao.getInstrumento());
        return response(alteracao, avaliacao);
    }

    private void validarMudancas(
            InstrumentoContratual instrumento,
            TipoAlteracao tipo,
            List<MudancaAlteracaoRequest> mudancas) {
        if (mudancas.isEmpty()) {
            throw new DomainException("A alteração deve informar ao menos um campo.");
        }
        if (tipo == TipoAlteracao.APOSTILAMENTO
                && mudancas.stream().map(MudancaAlteracaoRequest::campo)
                        .anyMatch(campo -> !campo.permitidoEmApostilamento())) {
            throw new DomainException("Apostilamento contém campo de natureza contratual.");
        }
        Set<CampoInstrumento> camposInformados = new HashSet<>();
        mudancas.forEach(mudanca -> {
            if (!camposInformados.add(mudanca.campo())) {
                String nomeAlteracao = tipo.nome();
                throw new DomainException(
                        "Cada campo pode aparecer uma única vez no %s.".formatted(nomeAlteracao));
            }
            String valorAtual = catalogoCampos.valorAtual(instrumento, mudanca.campo());
            if (!Objects.equals(valorAtual, mudanca.valorAnterior())) {
                throw new DomainException(
                        "O valor anterior de %s não corresponde ao estado atual do Instrumento Contratual."
                                .formatted(mudanca.campo()));
            }
            catalogoCampos.validarNovoValor(mudanca.campo(), mudanca.valorNovo());
        });
    }

    private List<AlteracaoCampo> salvarMudancas(
            AlteracaoContratual alteracao, List<MudancaAlteracaoRequest> mudancas) {
        List<AlteracaoCampo> salvas = new ArrayList<>();
        mudancas.forEach(mudanca -> {
            AlteracaoCampo item = new AlteracaoCampo();
            item.setAlteracao(alteracao);
            item.setCampo(mudanca.campo());
            item.setValorAnterior(mudanca.valorAnterior());
            item.setValorNovo(mudanca.valorNovo());
            campos.save(item);
            salvas.add(item);
        });
        return salvas;
    }


    private Documento documentoAssinado(Long id, AlteracaoContratual alteracao) {
        Documento d = documentos.findByIdComBloqueio(id)
                .orElseThrow(() -> new NotFoundException("Documento não encontrado."));
        if (!d.isAtivo() || d.getCategoria() != CategoriaDocumento.ASSINADO) {
            throw new DomainException("Efetivação exige Documento Assinado ativo.");
        }
        ProprietarioDocumento tipoEsperado = alteracao.getTipo().proprietarioDocumento();
        if (d.getProprietarioTipo() != tipoEsperado || !d.getProprietarioId().equals(alteracao.getId())) {
            throw new DomainException("O Documento Assinado pertence a outra alteração.");
        }
        var latest = versoes.findByDocumentoIdOrderByVersaoDesc(id).stream().findFirst()
                .orElseThrow(() -> new DomainException("Documento não possui versão."));
        if (!"application/pdf".equals(latest.getTipoMime())) throw new DomainException("Documento Assinado deve ser PDF.");
        return d;
    }

    private AlteracaoResponse response(
            AlteracaoContratual a,
            EstadoAtualInstrumento.Avaliacao avaliacao) {
        List<MudancaAlteracaoResponse> mudancas = avaliacao.mudancas(a).stream()
                .map(c -> new MudancaAlteracaoResponse(
                        c.getCampo(), c.getValorAnterior(), c.getValorNovo()))
                .toList();
        InstrumentoContratual instrumento = a.getInstrumento();
        EstadoAtualInstrumento.Estado estado = avaliacao.estadoAtual();
        Map<CampoInstrumento, PrecedenciaCampoResponse> precedenciaPorCampo =
                new EnumMap<>(CampoInstrumento.class);
        estado.precedenciaPorCampo().forEach((campo, precedencia) ->
                precedenciaPorCampo.put(campo, new PrecedenciaCampoResponse(
                        precedencia.dataEfetivacao(), precedencia.ordemOficial())));
        Map<CampoInstrumento, String> valores = estado.valores();
        EstadoAtualInstrumentoResponse estadoAtual = new EstadoAtualInstrumentoResponse(
                valores.get(CampoInstrumento.OBJETO),
                valores.get(CampoInstrumento.DESCRICAO),
                valores.get(CampoInstrumento.NATUREZA),
                valores.get(CampoInstrumento.COORDENADOR),
                List.of(valores.get(CampoInstrumento.PARTICIPES).split("\\n")),
                new BigDecimal(valores.get(CampoInstrumento.VALOR_ATUAL)),
                LocalDate.parse(valores.get(CampoInstrumento.VIGENCIA_CONTRATUAL_FINAL)),
                dataOpcional(valores.get(CampoInstrumento.VIGENCIA_TED_FINAL)),
                estado.statusProcesso(),
                precedenciaPorCampo);
        return new AlteracaoResponse(a.getId(), instrumento.getId(), a.getTipo(), a.getEstado(),
                a.getNumeroOficial(), a.getOrdemOficial(), a.getDataEfetivacao(), a.getOperacao(),
                a.getReferencia() == null ? null : a.getReferencia().getId(),
                a.getDocumentoAssinado() == null ? null : a.getDocumentoAssinado().getId(), mudancas,
                estadoAtual,
                siccService.consultarTramitacaoAlteracao(a.getTipo(), a.getId()),
                cadeia(avaliacao, a));
    }

    private List<AlteracaoVinculadaResponse> cadeia(
            EstadoAtualInstrumento.Avaliacao avaliacao,
            AlteracaoContratual selecionada) {
        return avaliacao.cadeia(selecionada).stream().map(registro -> {
            AlteracaoContratual item = registro.alteracao();
            return new AlteracaoVinculadaResponse(
                    item.getId(), item.getNumeroOficial(), item.getTipo(), item.getEstado(),
                    item.getOperacao(), item.getReferencia() == null
                            ? null : item.getReferencia().getId(),
                    item.getDataEfetivacao(), item.getOrdemOficial(),
                    registro.produzEfeitoAtual(), registro.valoresProduzidos());
        })
                .toList();
    }

    private LocalDate dataOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : LocalDate.parse(valor);
    }
}
