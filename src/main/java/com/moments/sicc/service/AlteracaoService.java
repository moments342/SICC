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
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.AlteracaoCampoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.InstrumentoEstadoInicialRepository;
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
    private final InstrumentoEstadoInicialRepository estadosIniciais;
    private final DocumentoRepository documentos;
    private final VersaoDocumentoRepository versoes;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final RegrasDeVigencia regrasDeVigencia;
    private final SiccService siccService;

    @Transactional
    public AlteracaoResponse criar(CriarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        InstrumentoContratual instrumento = instrumentos.findByIdForUpdate(request.instrumentoId())
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado."));
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
        if (referencia != null && !referenciaVigente(referencia)) {
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
        alteracoes.save(alteracao);
        salvarMudancas(alteracao, mudancas);
        String entidade = request.tipo() == TipoAlteracao.TERMO_ADITIVO
                ? "TERMO_ADITIVO" : "APOSTILAMENTO";
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_" + entidade, entidade, alteracao.getId(), true, null, ip);
        return response(alteracao);
    }

    @Transactional
    public AlteracaoResponse atualizar(
            Long id, AtualizarRascunhoAlteracaoRequest request, UsuarioInterno autor, String ip) {
        AlteracaoContratual alteracao = alteracoes.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        if (alteracao.getEstado() != EstadoAlteracao.RASCUNHO) {
            throw new DomainException("Alteração efetivada é imutável.");
        }
        if (alteracao.getOperacao() == OperacaoAlteracao.CANCELAMENTO) {
            throw new DomainException("Rascunho de cancelamento não possui mudanças editáveis.");
        }
        InstrumentoContratual instrumento = instrumentos.findByIdForUpdate(alteracao.getInstrumento().getId())
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado."));
        validarMudancas(instrumento, alteracao.getTipo(), request.mudancas());
        alteracao.setNumeroOficial(request.numeroOficial().trim());
        campos.deleteAll(campos.findByAlteracaoId(id));
        campos.flush();
        salvarMudancas(alteracao, request.mudancas());
        String entidade = alteracao.getTipo() == TipoAlteracao.TERMO_ADITIVO
                ? "TERMO_ADITIVO" : "APOSTILAMENTO";
        auditoria.registrarNaTransacaoAtual(
                autor, "EDITAR_" + entidade, entidade, id, true, null, ip);
        return response(alteracao);
    }

    @Transactional
    public AlteracaoResponse efetivar(Long id, EfetivarAlteracaoRequest request, UsuarioInterno autor, String ip) {
        AlteracaoContratual alteracao = alteracoes.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        if (alteracao.getEstado() != EstadoAlteracao.RASCUNHO) {
            throw new DomainException("Alteração efetivada é imutável.");
        }
        instrumentos.findByIdForUpdate(alteracao.getInstrumento().getId())
                .orElseThrow(() -> new NotFoundException("Instrumento Contratual não encontrado."));
        if (alteracao.getReferencia() != null) {
            AlteracaoContratual referencia = alteracoes.findByIdForUpdate(alteracao.getReferencia().getId())
                    .orElseThrow(() -> new NotFoundException("Alteração de referência não encontrada."));
            if (!referenciaVigente(referencia)) {
                throw new DomainException(
                        "A referência já foi cancelada ou depende de uma alteração cancelada.");
            }
            if (compararPrevalencia(
                    referencia.getDataEfetivacao(), referencia.getOrdemOficial(),
                    request.dataEfetivacao(), request.ordemOficial()) >= 0) {
                throw new DomainException(
                        "A operação deve ser posterior à alteração de referência na cronologia oficial.");
            }
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
        validarBaseDosCamposQuePrevalecerao(alteracao, request);
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
        auditoria.registrarNaTransacaoAtual(
                autor, acaoAuditoria, "ALTERACAO_CONTRATUAL", id, true, null, ip);
        auditoria.registrarNaTransacaoAtual(
                autor, "RECOMPUTAR_ESTADO_INSTRUMENTO", "INSTRUMENTO_CONTRATUAL",
                alteracao.getInstrumento().getId(), true,
                "alteracaoId=" + id + "; operacao=" + alteracao.getOperacao(), ip);
        return response(alteracao);
    }

    private void validarBaseDosCamposQuePrevalecerao(
            AlteracaoContratual alteracao, EfetivarAlteracaoRequest request) {
        if (alteracao.getOperacao() == OperacaoAlteracao.CANCELAMENTO) return;
        Map<CampoInstrumento, AlteracaoContratual> prevalentes = alteracoesPrevalentes(
                contextoPrevalencia(alteracao.getInstrumento()));
        campos.findByAlteracaoId(alteracao.getId()).forEach(mudanca -> {
            AlteracaoContratual atual = prevalentes.get(mudanca.getCampo());
            boolean candidatoPrevalece = atual == null || compararPrevalencia(
                    atual.getDataEfetivacao(), atual.getOrdemOficial(),
                    request.dataEfetivacao(), request.ordemOficial()) < 0;
            if (candidatoPrevalece
                    && !Objects.equals(
                            valorAtual(alteracao.getInstrumento(), mudanca.getCampo()),
                            mudanca.getValorAnterior())) {
                throw new DomainException(
                        "O valor anterior de %s ficou desatualizado; revise o rascunho antes de efetivar."
                                .formatted(mudanca.getCampo()));
            }
        });
    }

    private int compararPrevalencia(
            LocalDate dataA, Integer ordemA, LocalDate dataB, Integer ordemB) {
        int porData = dataA.compareTo(dataB);
        return porData != 0 ? porData : ordemA.compareTo(ordemB);
    }

    @Transactional(readOnly = true)
    public List<AlteracaoResponse> listar(Long instrumentoId) {
        return alteracoes.findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(instrumentoId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public AlteracaoResponse buscar(Long id) {
        return response(alteracoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada.")));
    }

    private void recomputar(InstrumentoContratual instrumento) {
        ContextoPrevalencia contexto = contextoPrevalencia(instrumento);
        List<AlteracaoContratual> efetivadas = contexto.efetivadas();
        List<AlteracaoContratual> vigentes = contexto.vigentes();
        Map<Long, Set<CampoInstrumento>> camposSubstituidos = contexto.camposSubstituidos();
        InstrumentoEstadoInicial estadoInicial = estadosIniciais.findById(instrumento.getId())
                .orElseThrow(() -> new DomainException(
                        "O estado inicial do Instrumento Contratual não foi preservado."));
        for (CampoInstrumento campo : CampoInstrumento.values()) {
            aplicar(instrumento, campo, valorInicial(estadoInicial, campo));
        }
        vigentes.forEach(a -> campos.findByAlteracaoId(a.getId())
                .stream()
                .filter(c -> !camposSubstituidos
                        .getOrDefault(a.getId(), Set.of()).contains(c.getCampo()))
                .forEach(c -> aplicar(instrumento, c.getCampo(), c.getValorNovo())));
        instrumento.getProcesso().setStatus(
                regrasDeVigencia.status(instrumento.getVigenciaContratualFinal()));
    }

    private ContextoPrevalencia contextoPrevalencia(InstrumentoContratual instrumento) {
        List<AlteracaoContratual> efetivadas = alteracoes
                .findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(instrumento.getId()).stream()
                .filter(a -> a.getEstado() == EstadoAlteracao.EFETIVADA)
                .sorted(Comparator.comparing(AlteracaoContratual::getDataEfetivacao)
                        .thenComparing(AlteracaoContratual::getOrdemOficial))
                .toList();
        return contextoPrevalencia(efetivadas);
    }

    private ContextoPrevalencia contextoPrevalencia(List<AlteracaoContratual> efetivadas) {
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
        return new ContextoPrevalencia(efetivadas, vigentes, camposSubstituidos);
    }

    private Map<CampoInstrumento, AlteracaoContratual> alteracoesPrevalentes(
            ContextoPrevalencia contexto) {
        EnumMap<CampoInstrumento, AlteracaoContratual> prevalentes =
                new EnumMap<>(CampoInstrumento.class);
        contexto.vigentes().forEach(a -> campos.findByAlteracaoId(a.getId()).stream()
                .filter(c -> !contexto.camposSubstituidos()
                        .getOrDefault(a.getId(), Set.of()).contains(c.getCampo()))
                .forEach(c -> prevalentes.put(c.getCampo(), a)));
        return prevalentes;
    }

    private boolean referenciaVigente(AlteracaoContratual referencia) {
        return contextoPrevalencia(referencia.getInstrumento()).vigentes().stream()
                .anyMatch(a -> a.getId().equals(referencia.getId()));
    }

    private record ContextoPrevalencia(
            List<AlteracaoContratual> efetivadas,
            List<AlteracaoContratual> vigentes,
            Map<Long, Set<CampoInstrumento>> camposSubstituidos) {}

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

    private String valorInicial(InstrumentoEstadoInicial i, CampoInstrumento campo) {
        return switch (campo) {
            case OBJETO -> i.getObjeto();
            case DESCRICAO -> i.getDescricao();
            case NATUREZA -> i.getNatureza();
            case COORDENADOR -> i.getCoordenador();
            case PARTICIPES -> i.getParticipes();
            case VALOR_ATUAL -> i.getValorAtual().toPlainString();
            case VIGENCIA_CONTRATUAL_FINAL -> i.getVigenciaContratualFinal().toString();
            case VIGENCIA_TED_FINAL -> i.getVigenciaTedFinal() == null
                    ? null : i.getVigenciaTedFinal().toString();
        };
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
                String nomeAlteracao = tipo == TipoAlteracao.APOSTILAMENTO
                        ? "Apostilamento" : "Termo Aditivo";
                throw new DomainException(
                        "Cada campo pode aparecer uma única vez no %s.".formatted(nomeAlteracao));
            }
            String valorAtual = valorAtual(instrumento, mudanca.campo());
            if (!Objects.equals(valorAtual, mudanca.valorAnterior())) {
                throw new DomainException(
                        "O valor anterior de %s não corresponde ao estado atual do Instrumento Contratual."
                                .formatted(mudanca.campo()));
            }
            validarNovoValor(mudanca);
        });
    }

    private void validarNovoValor(MudancaAlteracaoRequest mudanca) {
        String valor = mudanca.valorNovo();
        switch (mudanca.campo()) {
            case OBJETO -> validarTextoObrigatorio(mudanca.campo(), valor, 1000);
            case DESCRICAO -> validarTextoOpcional(mudanca.campo(), valor, 2000);
            case NATUREZA, COORDENADOR -> validarTextoObrigatorio(mudanca.campo(), valor, 150);
            case PARTICIPES -> validarTextoObrigatorio(mudanca.campo(), valor, 2000);
            case VALOR_ATUAL -> {
                try {
                    BigDecimal numero = new BigDecimal(valor);
                    if (numero.signum() < 0 || numero.scale() > 2 || numero.precision() > 19) {
                        throw new NumberFormatException();
                    }
                } catch (NullPointerException | NumberFormatException e) {
                    throw new DomainException(
                            "O novo valor de VALOR_ATUAL deve ser um número decimal não negativo.");
                }
            }
            case VIGENCIA_CONTRATUAL_FINAL -> validarData(mudanca.campo(), valor, false);
            case VIGENCIA_TED_FINAL -> validarData(mudanca.campo(), valor, true);
        }
    }

    private void salvarMudancas(
            AlteracaoContratual alteracao, List<MudancaAlteracaoRequest> mudancas) {
        mudancas.forEach(mudanca -> {
            AlteracaoCampo item = new AlteracaoCampo();
            item.setAlteracao(alteracao);
            item.setCampo(mudanca.campo());
            item.setValorAnterior(mudanca.valorAnterior());
            item.setValorNovo(mudanca.valorNovo());
            campos.save(item);
        });
    }

    private void validarTextoObrigatorio(CampoInstrumento campo, String valor, int limite) {
        if (valor == null || valor.isBlank() || valor.length() > limite) {
            throw new DomainException(
                    "O novo valor de %s deve ser preenchido e ter no máximo %d caracteres."
                            .formatted(campo, limite));
        }
    }

    private void validarTextoOpcional(CampoInstrumento campo, String valor, int limite) {
        if (valor != null && valor.length() > limite) {
            throw new DomainException(
                    "O novo valor de %s deve ter no máximo %d caracteres."
                            .formatted(campo, limite));
        }
    }

    private void validarData(CampoInstrumento campo, String valor, boolean opcional) {
        if (opcional && (valor == null || valor.isBlank())) return;
        try {
            LocalDate.parse(valor);
        } catch (RuntimeException e) {
            throw new DomainException(
                    "O novo valor de %s deve ser uma data no formato AAAA-MM-DD."
                            .formatted(campo));
        }
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
        Documento d = documentos.findByIdComBloqueio(id)
                .orElseThrow(() -> new NotFoundException("Documento não encontrado."));
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
        List<MudancaAlteracaoResponse> mudancas = campos.findByAlteracaoId(a.getId()).stream()
                .map(c -> new MudancaAlteracaoResponse(
                        c.getCampo(), c.getValorAnterior(), c.getValorNovo()))
                .toList();
        InstrumentoContratual instrumento = a.getInstrumento();
        Map<CampoInstrumento, PrecedenciaCampoResponse> precedenciaPorCampo = new EnumMap<>(CampoInstrumento.class);
        ContextoPrevalencia contexto = contextoPrevalencia(instrumento);
        alteracoesPrevalentes(contexto).forEach((campo, alteracao) ->
                precedenciaPorCampo.put(campo, new PrecedenciaCampoResponse(
                        alteracao.getDataEfetivacao(), alteracao.getOrdemOficial())));
        EstadoAtualInstrumentoResponse estadoAtual = new EstadoAtualInstrumentoResponse(
                instrumento.getObjeto(), instrumento.getDescricao(), instrumento.getNatureza(),
                instrumento.getCoordenador(), List.of(instrumento.getParticipes().split("\\n")),
                instrumento.getValorAtual(), instrumento.getVigenciaContratualFinal(),
                instrumento.getVigenciaTedFinal(), instrumento.getProcesso().getStatus(),
                precedenciaPorCampo);
        return new AlteracaoResponse(a.getId(), instrumento.getId(), a.getTipo(), a.getEstado(),
                a.getNumeroOficial(), a.getOrdemOficial(), a.getDataEfetivacao(), a.getOperacao(),
                a.getReferencia() == null ? null : a.getReferencia().getId(),
                a.getDocumentoAssinado() == null ? null : a.getDocumentoAssinado().getId(), mudancas,
                estadoAtual,
                siccService.consultarTramitacaoAlteracao(a.getTipo(), a.getId()),
                cadeia(a, contexto));
    }

    private List<AlteracaoVinculadaResponse> cadeia(
            AlteracaoContratual selecionada, ContextoPrevalencia contexto) {
        Long raizId = raiz(selecionada).getId();
        Set<Long> produtorasAtuais = alteracoesPrevalentes(contexto).values().stream()
                .map(AlteracaoContratual::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<AlteracaoContratual> registros = alteracoes
                .findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(
                        selecionada.getInstrumento().getId()).stream()
                .filter(item -> raiz(item).getId().equals(raizId))
                .sorted(Comparator
                        .comparing(AlteracaoContratual::getDataEfetivacao,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AlteracaoContratual::getOrdemOficial,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AlteracaoContratual::getId))
                .toList();
        return registros.stream().map(item -> new AlteracaoVinculadaResponse(
                item.getId(), item.getNumeroOficial(), item.getTipo(), item.getEstado(),
                item.getOperacao(), item.getReferencia() == null ? null : item.getReferencia().getId(),
                item.getDataEfetivacao(), item.getOrdemOficial(),
                produtorasAtuais.contains(item.getId()),
                valoresProduzidos(item, registros)))
                .toList();
    }

    private AlteracaoContratual raiz(AlteracaoContratual alteracao) {
        AlteracaoContratual atual = alteracao;
        Set<Long> visitados = new HashSet<>();
        while (atual.getReferencia() != null && visitados.add(atual.getId())) {
            atual = atual.getReferencia();
        }
        return atual;
    }

    private Map<CampoInstrumento, String> valoresProduzidos(
            AlteracaoContratual alteracao, List<AlteracaoContratual> cadeia) {
        EnumMap<CampoInstrumento, String> valores = new EnumMap<>(CampoInstrumento.class);
        if (alteracao.getOperacao() != OperacaoAlteracao.CANCELAMENTO) {
            campos.findByAlteracaoId(alteracao.getId())
                    .forEach(campo -> valores.put(campo.getCampo(), campo.getValorNovo()));
            return valores;
        }
        if (alteracao.getEstado() != EstadoAlteracao.EFETIVADA) {
            return valores;
        }
        AlteracaoContratual referencia = alteracao.getReferencia();
        cadeia.stream()
                .filter(item -> item.getOperacao() != OperacaoAlteracao.CANCELAMENTO)
                .filter(item -> descendeDe(item, referencia))
                .flatMap(item -> campos.findByAlteracaoId(item.getId()).stream())
                .map(AlteracaoCampo::getCampo)
                .distinct()
                .forEach(campo -> valores.put(
                        campo, valorReconstruidoNoMomento(alteracao, campo)));
        return valores;
    }

    private String valorReconstruidoNoMomento(
            AlteracaoContratual alteracao, CampoInstrumento campo) {
        List<AlteracaoContratual> efetivadasAteAlteracao = alteracoes
                .findByInstrumentoIdOrderByDataEfetivacaoAscOrdemOficialAsc(
                        alteracao.getInstrumento().getId()).stream()
                .filter(item -> item.getEstado() == EstadoAlteracao.EFETIVADA)
                .filter(item -> compararPrevalencia(
                        item.getDataEfetivacao(), item.getOrdemOficial(),
                        alteracao.getDataEfetivacao(), alteracao.getOrdemOficial()) <= 0)
                .sorted(Comparator.comparing(AlteracaoContratual::getDataEfetivacao)
                        .thenComparing(AlteracaoContratual::getOrdemOficial))
                .toList();
        AlteracaoContratual prevalente = alteracoesPrevalentes(
                contextoPrevalencia(efetivadasAteAlteracao)).get(campo);
        if (prevalente != null) {
            return campos.findByAlteracaoId(prevalente.getId()).stream()
                    .filter(mudanca -> mudanca.getCampo() == campo)
                    .map(AlteracaoCampo::getValorNovo)
                    .findFirst()
                    .orElseThrow();
        }
        InstrumentoEstadoInicial estadoInicial = estadosIniciais
                .findById(alteracao.getInstrumento().getId())
                .orElseThrow(() -> new DomainException(
                        "O estado inicial do Instrumento Contratual não foi preservado."));
        return valorInicial(estadoInicial, campo);
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
