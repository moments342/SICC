package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.InstrumentoEstadoInicial;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
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
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
        ProcessoAdministrativo processo = processo(id);
        processo.setOrigem(request.origem().trim());
        processo.setNumeroProjeto(normalizarOpcional(request.numeroProjeto()));
        processo.setResponsavel(request.responsavelId() == null ? null : usuarioAtivo(request.responsavelId()));
        auditoria.registrarNaTransacaoAtual(
                autor, "ALTERAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional
    public ProcessoResponse desativarProcesso(Long id, UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processo(id);
        processo.setAtivo(false);
        auditoria.registrarNaTransacaoAtual(
                autor, "DESATIVAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscarProcesso(Long id) {
        return processoResponse(processo(id));
    }

    @Transactional(readOnly = true)
    public List<ResponsavelProcessoResponse> listarResponsaveisAtivos() {
        return usuarios.findByAtivoTrueOrderByNomeAsc().stream()
                .map(usuario -> new ResponsavelProcessoResponse(
                        usuario.getId(), usuario.getNome(), usuario.getPerfil()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProcessoResponse> listarProcessos(String numero, String origem,
            String tipo, String status, String vigencia, Pageable pageable) {
        List<ProcessoResponse> filtrados = processos.findByAtivoTrue().stream()
                .map(this::processoResponse)
                .filter(p -> contem(p.numero(), numero))
                .filter(p -> contem(p.origem(), origem))
                .filter(p -> tipo == null || tipo.isBlank()
                        || p.instrumento() != null && p.instrumento().tipo().name().equals(tipo))
                .filter(p -> status == null || status.isBlank() || p.status().name().equals(status))
                .filter(p -> vigencia == null || vigencia.isBlank() || p.instrumento() != null
                        && (p.instrumento().situacaoContratual().name().equals(vigencia)
                        || p.instrumento().situacaoTed().name().equals(vigencia)))
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtrados.size());
        int end = Math.min(start + pageable.getPageSize(), filtrados.size());
        return PaginaResponse.de(new PageImpl<>(filtrados.subList(start, end), pageable, filtrados.size()));
    }

    @Transactional
    public InstrumentoResponse formalizar(Long processoId, FormalizarInstrumentoRequest request,
            UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processoComBloqueio(processoId);
        if (instrumentos.findByProcessoId(processoId).isPresent()) {
            throw new DomainException("O Processo Administrativo já possui Instrumento Contratual.");
        }
        Documento documentoAssinado = validarDocumentoAssinado(
                request.documentoAssinadoId(), ProprietarioDocumento.PROCESSO, processoId);
        if (request.dataFormalizacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de formalização não pode ser futura.");
        }
        InstrumentoContratual instrumento = new InstrumentoContratual();
        instrumento.setProcesso(processo);
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
                instrumento.getId(), true, null, ip);
        auditoria.registrarNaTransacaoAtual(
                autor, "VINCULAR_DOCUMENTO_ASSINADO", "DOCUMENTO",
                documentoAssinado.getId(), true,
                "instrumentoId=" + instrumento.getId(), ip);
        return instrumentoResponse(instrumento);
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
        int sequencia = movimentacoes
                .findFirstByContextoTipoAndContextoIdAndDataMovimentacaoOrderBySequenciaDiariaDesc(
                        request.contextoTipo(), request.contextoId(), request.dataMovimentacao())
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
        notificacaoChegada.processar(processo, movimento);
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
        return movimentacaoResponse(movimento);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> listarMovimentacoes(ContextoTramitacao tipo, Long contextoId) {
        return movimentacoes.findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                tipo, contextoId).stream().map(this::movimentacaoResponse).toList();
    }

    @Transactional(readOnly = true)
    public HistoricoTramitacaoResponse consultarTramitacaoFormalizacao(Long processoId) {
        processo(processoId);
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
        ContextoTramitacao contexto = tipoAlteracao == TipoAlteracao.TERMO_ADITIVO
                ? ContextoTramitacao.TERMO_ADITIVO : ContextoTramitacao.APOSTILAMENTO;
        return historicoTramitacao(contexto, alteracaoId);
    }

    private HistoricoTramitacaoResponse historicoTramitacao(
            ContextoTramitacao contexto, Long contextoId) {
        List<Movimentacao> movimentos = movimentacoes
                .findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                        contexto, contextoId);
        List<MovimentacaoResponse> historico = movimentos.stream()
                .map(this::movimentacaoResponse)
                .toList();
        SetorResponse setorAtual = movimentos.isEmpty()
                ? null
                : setorResponse(movimentos.getLast().getSetorDestino());
        return new HistoricoTramitacaoResponse(
                setorAtual, historico, calcularPermanencias(movimentos));
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarNotificacoes(UsuarioInterno usuario) {
        return notificacoes.findByDestinatarioIdOrderByCriadaEmDesc(usuario.getId()).stream()
                .map(this::notificacaoResponse)
                .toList();
    }

    @Transactional
    public NotificacaoResponse marcarNotificacaoLida(Long id, UsuarioInterno usuario) {
        Notificacao n = notificacaoDoUsuario(id, usuario);
        n.setLida(true);
        return notificacaoResponse(n);
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
            String tipo, String status, String vigencia, Pageable pageable) {
        List<ProcessoPublicoResponse> filtrados = processos.findAll().stream()
                .map(this::projecaoPublica)
                .filter(p -> contem(p.response().numeroProcesso(), numero))
                .filter(p -> contem(p.response().origem(), origem))
                .filter(p -> tipo == null || tipo.isBlank()
                        || Objects.equals(p.response().tipoInstrumento(), tipo))
                .filter(p -> status == null || status.isBlank()
                        || p.response().status().name().equals(status))
                .filter(p -> vigencia == null || vigencia.isBlank()
                        || p.situacaoContratual().name().equals(vigencia)
                        || p.situacaoTed().name().equals(vigencia))
                .map(ProjecaoPublica::response)
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtrados.size());
        int end = Math.min(start + pageable.getPageSize(), filtrados.size());
        return PaginaResponse.de(new PageImpl<>(filtrados.subList(start, end), pageable, filtrados.size()));
    }

    public SituacaoVigencia situacao(LocalDate data) {
        return regrasDeVigencia.situacao(data);
    }

    private void atualizarStatus(ProcessoAdministrativo processo, InstrumentoContratual instrumento) {
        processo.setStatus(regrasDeVigencia.status(instrumento.getVigenciaContratualFinal()));
    }

    private NotificacaoResponse notificacaoResponse(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getTipo().name(),
                notificacao.getMensagem(),
                notificacao.getProcesso() == null ? null : notificacao.getProcesso().getId(),
                notificacao.isLida(),
                notificacao.getCriadaEm());
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
            return processoComBloqueio(contextoId);
        }
        var alteracao = alteracoes.findById(contextoId)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        ContextoTramitacao esperado = alteracao.getTipo() == com.moments.sicc.domain.Enums.TipoAlteracao.TERMO_ADITIVO
                ? ContextoTramitacao.TERMO_ADITIVO : ContextoTramitacao.APOSTILAMENTO;
        if (tipo != esperado) throw new DomainException("O contexto não corresponde ao tipo da alteração.");
        return processoComBloqueio(alteracao.getInstrumento().getProcesso().getId());
    }

    private Documento validarDocumentoAssinado(Long documentoId, ProprietarioDocumento tipo, Long proprietarioId) {
        Documento documento = documentos.findByIdComBloqueio(documentoId)
                .orElseThrow(() -> new NotFoundException("Documento assinado não encontrado."));
        if (!documento.isAtivo() || documento.getCategoria() != CategoriaDocumento.ASSINADO) {
            throw new DomainException("A formalização exige um Documento Assinado ativo.");
        }
        if (documento.getProprietarioTipo() != tipo || !Objects.equals(documento.getProprietarioId(), proprietarioId)) {
            throw new DomainException("O Documento Assinado pertence a outro objeto.");
        }
        var latest = versoes.findByDocumentoIdOrderByVersaoDesc(documentoId).stream().findFirst()
                .orElseThrow(() -> new DomainException("O Documento Assinado não possui versão."));
        if (!"application/pdf".equals(latest.getTipoMime())) {
            throw new DomainException("O Documento Assinado deve ser PDF.");
        }
        return documento;
    }

    private ProcessoResponse processoResponse(ProcessoAdministrativo p) {
        InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
        StatusProcesso statusAtual = i == null
                ? StatusProcesso.EM_FORMALIZACAO
                : regrasDeVigencia.status(i.getVigenciaContratualFinal());
        String setorAtual = movimentacoes
                .findFirstByContextoTipoAndContextoIdOrderByDataMovimentacaoDescSequenciaDiariaDesc(
                        ContextoTramitacao.FORMALIZACAO, p.getId())
                .map(m -> m.getSetorDestino().getSigla()).orElse(null);
        return new ProcessoResponse(p.getId(), p.getNumero(), p.getOrigem(), p.getNumeroProjeto(), statusAtual,
                p.getDataCadastro(), p.isAtivo(), p.getResponsavel() == null ? null : usuarioResponse(p.getResponsavel()),
                setorAtual, i == null ? null : instrumentoResponse(i));
    }

    private ProjecaoPublica projecaoPublica(ProcessoAdministrativo p) {
        InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
        if (i == null) {
            return new ProjecaoPublica(new ProcessoPublicoResponse(
                    p.getNumero(), "Ainda não formalizado", p.getOrigem(),
                    "Ainda não formalizado", StatusProcesso.EM_FORMALIZACAO, null, null),
                    SituacaoVigencia.NAO_INFORMADA, SituacaoVigencia.NAO_INFORMADA);
        }
        StatusProcesso statusAtual = regrasDeVigencia.status(i.getVigenciaContratualFinal());
        return new ProjecaoPublica(new ProcessoPublicoResponse(
                p.getNumero(), i.getTipo().name(), p.getOrigem(), i.getCoordenador(),
                statusAtual, i.getVigenciaContratualFinal(), i.getVigenciaTedFinal()),
                situacao(i.getVigenciaContratualFinal()), situacao(i.getVigenciaTedFinal()));
    }

    private InstrumentoResponse instrumentoResponse(InstrumentoContratual i) {
        return new InstrumentoResponse(i.getId(), i.getProcesso().getId(), i.getNumero(), i.getTipo(),
                i.getObjeto(), i.getDescricao(), i.getNatureza(), i.getCoordenador(),
                List.of(i.getParticipes().split("\\n")), i.getValorAtual(), i.getVigenciaContratualFinal(),
                i.getVigenciaTedFinal(), i.getDataFormalizacao(), i.getDocumentoAssinado().getId(),
                situacao(i.getVigenciaContratualFinal()),
                situacao(i.getVigenciaTedFinal()));
    }

    private MovimentacaoResponse movimentacaoResponse(Movimentacao m) {
        return new MovimentacaoResponse(m.getId(), m.getContextoTipo(), m.getContextoId(), m.getDataMovimentacao(),
                m.getSequenciaDiaria(), setorResponse(m.getSetorDestino()), usuarioResponse(m.getAutor()),
                m.getObservacao(), m.getInseridoEm());
    }

    private List<PermanenciaSetorResponse> calcularPermanencias(List<Movimentacao> movimentos) {
        if (movimentos.isEmpty()) return List.of();
        List<PermanenciaSetorResponse> permanencias = new ArrayList<>();
        Movimentacao chegada = movimentos.getFirst();
        for (int indice = 1; indice < movimentos.size(); indice++) {
            Movimentacao seguinte = movimentos.get(indice);
            if (Objects.equals(
                    chegada.getSetorDestino().getId(),
                    seguinte.getSetorDestino().getId())) {
                continue;
            }
            permanencias.add(new PermanenciaSetorResponse(
                    setorResponse(chegada.getSetorDestino()),
                    chegada.getDataMovimentacao(),
                    seguinte.getDataMovimentacao(),
                    ChronoUnit.DAYS.between(
                            chegada.getDataMovimentacao(), seguinte.getDataMovimentacao()),
                    false));
            chegada = seguinte;
        }
        LocalDate hoje = LocalDate.now(clock);
        permanencias.add(new PermanenciaSetorResponse(
                setorResponse(chegada.getSetorDestino()),
                chegada.getDataMovimentacao(),
                null,
                ChronoUnit.DAYS.between(chegada.getDataMovimentacao(), hoje),
                true));
        return List.copyOf(permanencias);
    }

    private UsuarioResponse usuarioResponse(UsuarioInterno u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getLogin(), u.getPerfil(),
                u.isAtivo(), u.isSenhaTemporaria());
    }

    private SetorResponse setorResponse(Setor s) {
        return new SetorResponse(s.getId(), s.getSigla(), s.getNome(), s.isAtivo());
    }

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

    private ProcessoAdministrativo processo(Long id) {
        return processos.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new NotFoundException("Processo Administrativo não encontrado."));
    }

    private ProcessoAdministrativo processoComBloqueio(Long id) {
        return processos.findAtivoByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Processo Administrativo não encontrado."));
    }

    private String normalizarOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean contem(String value, String filtro) {
        return filtro == null || filtro.isBlank()
                || value.toLowerCase(Locale.ROOT).contains(filtro.toLowerCase(Locale.ROOT));
    }

    private record ProjecaoPublica(
            ProcessoPublicoResponse response,
            SituacaoVigencia situacaoContratual,
            SituacaoVigencia situacaoTed) {}
}
