package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.consulta.ConsultaProcessos;
import com.moments.sicc.consulta.ConsultaProcessos.Filtros;
import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.domain.VersaoDocumento;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.InstrumentoEstadoInicialRepository;
import com.moments.sicc.repository.MovimentacaoRepository;
import com.moments.sicc.repository.NotificacaoRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.SetorRepository;
import com.moments.sicc.repository.UsuarioInternoRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.service.ProcessoMutacaoGuard.ContextoAlteracao;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import com.moments.sicc.shared.PaginacaoSegura;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiccService {
    private final UsuarioInternoRepository usuarios;
    private final SetorRepository setores;
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final InstrumentoEstadoInicialRepository estadosIniciais;
    private final MovimentacaoRepository movimentacoes;
    private final NotificacaoRepository notificacoes;
    private final NotificacaoChegadaService notificacaoChegada;
    private final DocumentoRepository documentos;
    private final AlteracaoContratualRepository alteracoes;
    private final VersaoDocumentoRepository versoes;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final RegrasDeVigencia regrasDeVigencia;
    private final ProcessoMutacaoGuard processoMutacao;
    private final ConsultaProcessos consultaProcessos;
    private final CalculadoraPermanencia calculadoraPermanencia;
    private final ProjecoesSicc projecoes;

    @Transactional
    public ProcessoResponse criarProcesso(CriarProcessoRequest request, UsuarioInterno autor, String ip) {
        String numero = request.numero().trim().toUpperCase(Locale.ROOT);
        if (processos.existsByNumeroIgnoreCase(numero)) {
            throw new DomainException("Já existe Processo Administrativo com este número.");
        }
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setNumero(numero);
        processo.setOrigem(request.origem().trim());
        processo.setNumeroProjeto(normalizarOpcional(request.numeroProjeto()));
        processo.setResponsavel(request.responsavelId() == null ? null : usuarioAtivo(request.responsavelId()));
        processo.setDataCadastro(LocalDate.now(clock));
        try {
            processos.saveAndFlush(processo);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("Já existe Processo Administrativo com este número.");
        }
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", processo.getId(), true, null, ip);
        return processoResponse(processo);
    }

    @Transactional
    public ProcessoResponse atualizarProcesso(Long id, AtualizarProcessoRequest request,
            UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processoMutacao.processoAtivo(id).processo();
        processo.setOrigem(request.origem().trim());
        processo.setNumeroProjeto(normalizarOpcional(request.numeroProjeto()));
        processo.setResponsavel(request.responsavelId() == null ? null : usuarioAtivo(request.responsavelId()));
        auditoria.registrarNaTransacaoAtual(
                autor, "ALTERAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional
    public ProcessoResponse desativarProcesso(Long id, UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processoMutacao.processoAtivo(id).processo();
        processo.setAtivo(false);
        auditoria.registrarNaTransacaoAtual(
                autor, "DESATIVAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscarProcesso(Long id) {
        return processoResponse(processoExistente(id));
    }

    @Transactional(readOnly = true)
    public List<ResponsavelProcessoResponse> listarResponsaveisAtivos() {
        return usuarios.findByAtivoTrueOrderByNomeAsc().stream()
                .map(projecoes::responsavel)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProcessoResponse> listarProcessos(String numero, String origem,
            String tipo, String status, String vigencia, String objeto, String coordenador,
            boolean incluirInativos, int pagina, int tamanho) {
        ReferenciaDeVigencia referenciaDeVigencia = regrasDeVigencia.referenciaAtual();
        var pageable = PaginacaoSegura.criar(
                pagina, tamanho, Sort.by(Sort.Order.asc("id")));
        var processosPaginados = consultaProcessos.interna(
                new Filtros(numero, origem, tipo, status, vigencia, objeto, coordenador),
                pageable,
                referenciaDeVigencia,
                incluirInativos);
        Map<Long, String> setoresAtuais = setoresAtuais(processosPaginados.getContent());
        return PaginaResponse.de(processosPaginados.map(processo -> projecoes.processo(
                processo,
                processo.getInstrumento(),
                setoresAtuais.get(processo.getId()),
                referenciaDeVigencia)));
    }

    @Transactional
    public InstrumentoResponse formalizar(Long processoId, FormalizarInstrumentoRequest request,
            UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processoMutacao
                .processoAtivo(processoId)
                .processo();
        if (instrumentos.findByProcessoId(processoId).isPresent()) {
            throw new DomainException("O Processo Administrativo já possui Instrumento Contratual.");
        }
        DocumentoAssinado evidenciaAssinada = validarDocumentoAssinado(
                request.documentoAssinadoId(), ProprietarioDocumento.PROCESSO, processoId);
        Documento documentoAssinado = evidenciaAssinada.documento();
        if (request.dataFormalizacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de formalização não pode ser futura.");
        }
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setProcesso(processo);
        processo.setInstrumento(instrumento);
        instrumento.setNumero(request.numero().trim());
        instrumento.setTipo(request.tipo());
        instrumento.setObjeto(request.objeto().trim());
        instrumento.setDescricao(normalizarOpcional(request.descricao()));
        instrumento.setNatureza(request.natureza().trim());
        instrumento.setCoordenador(request.coordenador().trim());
        String participes = String.join("\n", request.participes().stream()
                .map(String::trim)
                .toList());
        if (participes.length() > 2000) {
            throw new DomainException("Os partícipes excedem o limite de 2000 caracteres.");
        }
        instrumento.setParticipes(participes);
        instrumento.setValorAtual(request.valorAtual());
        instrumento.setVigenciaContratualFinal(request.vigenciaContratualFinal());
        instrumento.setVigenciaTedFinal(request.vigenciaTedFinal());
        instrumento.setDataFormalizacao(request.dataFormalizacao());
        instrumento.setDocumentoAssinado(documentoAssinado);
        instrumento.setDocumentoAssinadoVersao(evidenciaAssinada.versao());
        try {
            instrumentos.saveAndFlush(instrumento);
            estadosIniciais.save(InstrumentoEstadoInicial.copiarDe(instrumento));
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("O Processo Administrativo já possui Instrumento Contratual.");
        }
        documentoAssinado.setProprietarioTipo(ProprietarioDocumento.INSTRUMENTO);
        documentoAssinado.setProprietarioId(instrumento.getId());
        atualizarStatus(processo, instrumento);
        auditoria.registrarNaTransacaoAtual(
                autor, "FORMALIZAR_INSTRUMENTO", "INSTRUMENTO_CONTRATUAL",
                instrumento.getId(), true,
                "documentoAssinadoId=" + documentoAssinado.getId()
                        + "; versao=" + evidenciaAssinada.versao().getVersao()
                        + "; checksumSha256=" + evidenciaAssinada.versao().getChecksumSha256(),
                ip);
        auditoria.registrarNaTransacaoAtual(
                autor, "VINCULAR_DOCUMENTO_ASSINADO", "DOCUMENTO",
                documentoAssinado.getId(), true,
                "instrumentoId=" + instrumento.getId(), ip);
        return projecoes.instrumento(instrumento, regrasDeVigencia.referenciaAtual());
    }

    @Transactional
    public MovimentacaoResponse movimentar(CriarMovimentacaoRequest request, UsuarioInterno autor, String ip) {
        if (request.dataMovimentacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data da movimentação não pode ser futura.");
        }
        Setor destino = setor(request.setorDestinoId());
        if (!destino.isAtivo()) throw new DomainException("O setor de destino está inativo.");
        ProcessoAdministrativo processo =
                processoDoContextoComBloqueio(request.contextoTipo(), request.contextoId());
        var predecessorCronologico = movimentacoes
                .findFirstByContextoTipoAndContextoIdAndDataMovimentacaoLessThanEqualOrderByDataMovimentacaoDescSequenciaDiariaDesc(
                        request.contextoTipo(), request.contextoId(), request.dataMovimentacao());
        int sequencia = predecessorCronologico
                .filter(ultimo -> ultimo.getDataMovimentacao().equals(request.dataMovimentacao()))
                .map(ultimo -> ultimo.getSequenciaDiaria() + 1)
                .orElse(1);
        Movimentacao movimento = new Movimentacao(
                request.contextoTipo(),
                request.contextoId(),
                request.dataMovimentacao(),
                sequencia,
                destino,
                autor,
                normalizarOpcional(request.observacao()),
                LocalDateTime.now(clock));
        movimentacoes.save(movimento);
        if (predecessorCronologico.isEmpty()
                || !Objects.equals(
                        predecessorCronologico.get().getSetorDestino().getId(), destino.getId())) {
            notificacaoChegada.processar(processo, movimento);
        }
        auditoria.registrarNaTransacaoAtual(
                autor,
                "CRIAR_MOVIMENTACAO",
                "MOVIMENTACAO",
                movimento.getId(),
                true,
                "contexto=" + request.contextoTipo()
                        + "; contextoId=" + request.contextoId()
                        + "; dataMovimentacao=" + request.dataMovimentacao()
                        + "; sequenciaDiaria=" + sequencia,
                ip);
        return projecoes.movimentacao(movimento);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> listarMovimentacoes(ContextoTramitacao tipo, Long contextoId) {
        return movimentacoes.findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                tipo, contextoId).stream().map(projecoes::movimentacao).toList();
    }

    @Transactional(readOnly = true)
    public HistoricoTramitacaoResponse consultarTramitacaoFormalizacao(Long processoId) {
        processoExistente(processoId);
        return historicoTramitacao(ContextoTramitacao.FORMALIZACAO, processoId);
    }

    @Transactional(readOnly = true)
    public HistoricoTramitacaoResponse consultarTramitacaoAlteracao(
            TipoAlteracao tipoAlteracao, Long alteracaoId) {
        var alteracao = alteracoes.findById(alteracaoId)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        if (alteracao.getTipo() != tipoAlteracao) {
            throw new DomainException("O tipo informado não corresponde à alteração contratual.");
        }
        return historicoTramitacao(tipoAlteracao.contextoTramitacao(), alteracaoId);
    }

    private HistoricoTramitacaoResponse historicoTramitacao(
            ContextoTramitacao contexto, Long contextoId) {
        List<Movimentacao> movimentos = movimentacoes
                .findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                        contexto, contextoId);
        List<MovimentacaoResponse> historico = movimentos.stream()
                .map(projecoes::movimentacao)
                .toList();
        SetorResponse setorAtual = movimentos.isEmpty()
                ? null
                : projecoes.setor(movimentos.getLast().getSetorDestino());
        return new HistoricoTramitacaoResponse(
                setorAtual,
                historico,
                projecoes.permanencias(calculadoraPermanencia.calcular(
                        movimentos, LocalDate.now(clock))));
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarNotificacoes(UsuarioInterno usuario) {
        return notificacoes.findByDestinatarioIdOrderByCriadaEmDesc(usuario.getId()).stream()
                .map(projecoes::notificacao)
                .toList();
    }

    @Transactional
    public NotificacaoResponse marcarNotificacaoLida(Long id, UsuarioInterno usuario) {
        Notificacao n = notificacaoDoUsuario(id, usuario);
        n.setLida(true);
        return projecoes.notificacao(n);
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscarProcessoDaNotificacao(Long id, UsuarioInterno usuario) {
        Notificacao notificacao = notificacaoDoUsuario(id, usuario);
        if (notificacao.getProcesso() == null) {
            throw new NotFoundException(
                    "A Notificação Interna não está vinculada a um Processo Administrativo.");
        }
        return processoResponse(notificacao.getProcesso());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProcessoPublicoResponse> consultaPublica(String numero, String origem,
            String tipo, String status, String vigencia, int pagina, int tamanho) {
        ReferenciaDeVigencia referenciaDeVigencia = regrasDeVigencia.referenciaAtual();
        var pageable = PaginacaoSegura.criar(
                pagina, tamanho, Sort.by(Sort.Order.asc("id")));
        var processosPaginados = consultaProcessos.publica(
                new Filtros(numero, origem, tipo, status, vigencia, null, null),
                pageable,
                referenciaDeVigencia);
        return PaginaResponse.de(processosPaginados.map(
                        processo -> projecoes.processoPublico(
                        processo, processo.getInstrumento(), referenciaDeVigencia)));
    }

    public SituacaoVigencia situacao(LocalDate data) {
        return regrasDeVigencia.situacao(data);
    }

    private void atualizarStatus(ProcessoAdministrativo processo, InstrumentoContratual instrumento) {
        processo.setStatus(regrasDeVigencia.status(instrumento.getVigenciaContratualFinal()));
    }

    private Notificacao notificacaoDoUsuario(Long id, UsuarioInterno usuario) {
        Notificacao notificacao = notificacoes.findById(id)
                .orElseThrow(() -> new NotFoundException("Notificação Interna não encontrada."));
        if (!Objects.equals(notificacao.getDestinatario().getId(), usuario.getId())) {
            throw new DomainException("A Notificação Interna pertence a outro usuário.");
        }
        return notificacao;
    }

    private ProcessoAdministrativo processoDoContextoComBloqueio(
            ContextoTramitacao tipo, Long contextoId) {
        if (tipo == ContextoTramitacao.FORMALIZACAO) {
            return processoMutacao.processoAtivo(contextoId).processo();
        }
        ContextoAlteracao contexto = processoMutacao.alteracaoEmProcessoAtivo(contextoId);
        ContextoTramitacao esperado = contexto.alteracao().getTipo().contextoTramitacao();
        if (tipo != esperado) throw new DomainException("O contexto não corresponde ao tipo da alteração.");
        return contexto.processo();
    }

    private DocumentoAssinado validarDocumentoAssinado(
            Long documentoId, ProprietarioDocumento tipo, Long proprietarioId) {
        Documento documento = documentos.findByIdComBloqueio(documentoId)
                .orElseThrow(() -> new NotFoundException("Documento assinado não encontrado."));
        if (!documento.isAtivo() || documento.getCategoria() != CategoriaDocumento.ASSINADO) {
            throw new DomainException("A formalização exige um Documento Assinado ativo.");
        }
        if (documento.getProprietarioTipo() != tipo || !Objects.equals(documento.getProprietarioId(), proprietarioId)) {
            throw new DomainException("O Documento Assinado pertence a outro objeto.");
        }
        VersaoDocumento latest = versoes.findFirstByDocumentoIdOrderByVersaoDesc(documentoId)
                .orElseThrow(() -> new DomainException("O Documento Assinado não possui versão."));
        if (!"application/pdf".equals(latest.getTipoMime())) {
            throw new DomainException("O Documento Assinado deve ser PDF.");
        }
        return new DocumentoAssinado(documento, latest);
    }

    private ProcessoResponse processoResponse(ProcessoAdministrativo p) {
        InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
        String setorAtual = movimentacoes
                .findFirstByContextoTipoAndContextoIdOrderByDataMovimentacaoDescSequenciaDiariaDesc(
                        ContextoTramitacao.FORMALIZACAO, p.getId())
                .map(m -> m.getSetorDestino().getSigla()).orElse(null);
        return projecoes.processo(
                p, i, setorAtual, regrasDeVigencia.referenciaAtual());
    }

    private Map<Long, String> setoresAtuais(List<ProcessoAdministrativo> processosPaginados) {
        if (processosPaginados.isEmpty()) return Map.of();
        List<Long> ids = processosPaginados.stream()
                .map(ProcessoAdministrativo::getId)
                .toList();
        return movimentacoes.findUltimasPorContextos(ContextoTramitacao.FORMALIZACAO, ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        Movimentacao::getContextoId,
                        movimentacao -> movimentacao.getSetorDestino().getSigla(),
                        (primeiro, ignorado) -> primeiro));
    }

    private record DocumentoAssinado(Documento documento, VersaoDocumento versao) {}

    private UsuarioInterno usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    private UsuarioInterno usuarioAtivo(Long id) {
        UsuarioInterno usuario = usuario(id);
        if (!usuario.isAtivo()) throw new DomainException("O usuário responsável está inativo.");
        return usuario;
    }

    private Setor setor(Long id) {
        return setores.findById(id).orElseThrow(() -> new NotFoundException("Setor não encontrado."));
    }

    private ProcessoAdministrativo processoExistente(Long id) {
        return processos.findById(id)
                .orElseThrow(() -> new NotFoundException("Processo Administrativo não encontrado."));
    }

    private String normalizarOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean contem(String value, String filtro) {
        return filtro == null || filtro.isBlank()
                || value.toLowerCase(Locale.ROOT).contains(filtro.toLowerCase(Locale.ROOT));
    }

}
