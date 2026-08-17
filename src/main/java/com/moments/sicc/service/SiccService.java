package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
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
    private final MovimentacaoRepository movimentacoes;
    private final NotificacaoRepository notificacoes;
    private final DocumentoRepository documentos;
    private final AlteracaoContratualRepository alteracoes;
    private final VersaoDocumentoRepository versoes;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final RegrasDeVigencia regrasDeVigencia;

    @Transactional
    public SetorResponse criarSetor(CriarSetorRequest request, UsuarioInterno autor, String ip) {
        if (setores.existsBySiglaIgnoreCase(request.sigla())) throw new DomainException("Já existe setor com esta sigla.");
        Setor setor = new Setor();
        setor.setSigla(request.sigla().trim().toUpperCase(Locale.ROOT));
        setor.setNome(request.nome().trim());
        setores.save(setor);
        auditoria.registrar(autor, "CRIAR_SETOR", "SETOR", setor.getId(), true, null, ip);
        return setorResponse(setor);
    }

    @Transactional(readOnly = true)
    public List<SetorResponse> listarSetores(boolean somenteAtivos) {
        List<Setor> resultado = somenteAtivos ? setores.findByAtivoTrueOrderBySigla() : setores.findAll();
        return resultado.stream().map(this::setorResponse).toList();
    }

    @Transactional
    public SetorResponse definirAtivoSetor(Long id, boolean ativo, UsuarioInterno autor, String ip) {
        Setor setor = setor(id);
        setor.setAtivo(ativo);
        auditoria.registrar(autor, ativo ? "REATIVAR_SETOR" : "DESATIVAR_SETOR", "SETOR", id, true, null, ip);
        return setorResponse(setor);
    }

    @Transactional
    public ProcessoResponse criarProcesso(CriarProcessoRequest request, UsuarioInterno autor, String ip) {
        if (processos.existsByNumeroIgnoreCase(request.numero())) {
            throw new DomainException("Já existe Processo Administrativo com este número.");
        }
        ProcessoAdministrativo processo = new ProcessoAdministrativo();
        processo.setNumero(request.numero().trim());
        processo.setOrigem(request.origem().trim());
        processo.setNumeroProjeto(normalizarOpcional(request.numeroProjeto()));
        processo.setResponsavel(request.responsavelId() == null ? null : usuarioAtivo(request.responsavelId()));
        processos.save(processo);
        auditoria.registrar(autor, "CRIAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", processo.getId(), true, null, ip);
        return processoResponse(processo);
    }

    @Transactional
    public ProcessoResponse atualizarProcesso(Long id, AtualizarProcessoRequest request,
            UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processo(id);
        processo.setOrigem(request.origem().trim());
        processo.setNumeroProjeto(normalizarOpcional(request.numeroProjeto()));
        processo.setResponsavel(request.responsavelId() == null ? null : usuarioAtivo(request.responsavelId()));
        auditoria.registrar(autor, "ALTERAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional
    public ProcessoResponse desativarProcesso(Long id, UsuarioInterno autor, String ip) {
        ProcessoAdministrativo processo = processo(id);
        processo.setAtivo(false);
        auditoria.registrar(autor, "DESATIVAR_PROCESSO", "PROCESSO_ADMINISTRATIVO", id, true, null, ip);
        return processoResponse(processo);
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscarProcesso(Long id) {
        return processoResponse(processo(id));
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
        ProcessoAdministrativo processo = processo(processoId);
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
        instrumento.setParticipes(String.join("\n", request.participes()));
        instrumento.setValorAtual(request.valorAtual());
        instrumento.setVigenciaContratualFinal(request.vigenciaContratualFinal());
        instrumento.setVigenciaTedFinal(request.vigenciaTedFinal());
        instrumento.setDataFormalizacao(request.dataFormalizacao());
        instrumentos.save(instrumento);
        documentoAssinado.setProprietarioTipo(ProprietarioDocumento.INSTRUMENTO);
        documentoAssinado.setProprietarioId(instrumento.getId());
        atualizarStatus(processo, instrumento);
        auditoria.registrar(autor, "FORMALIZAR_INSTRUMENTO", "INSTRUMENTO_CONTRATUAL",
                instrumento.getId(), true, null, ip);
        return instrumentoResponse(instrumento);
    }

    @Transactional
    public MovimentacaoResponse movimentar(CriarMovimentacaoRequest request, UsuarioInterno autor, String ip) {
        if (request.dataMovimentacao().isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data da movimentação não pode ser futura.");
        }
        Setor destino = setor(request.setorDestinoId());
        if (!destino.isAtivo()) throw new DomainException("O setor de destino está inativo.");
        ProcessoAdministrativo processo = processoDoContexto(request.contextoTipo(), request.contextoId());
        int sequencia = Math.toIntExact(movimentacoes.countByContextoTipoAndContextoIdAndDataMovimentacao(
                request.contextoTipo(), request.contextoId(), request.dataMovimentacao()) + 1);
        Movimentacao movimento = new Movimentacao();
        movimento.setContextoTipo(request.contextoTipo());
        movimento.setContextoId(request.contextoId());
        movimento.setDataMovimentacao(request.dataMovimentacao());
        movimento.setSequenciaDiaria(sequencia);
        movimento.setSetorDestino(destino);
        movimento.setAutor(autor);
        movimento.setObservacao(normalizarOpcional(request.observacao()));
        movimentacoes.save(movimento);
        notificarChegada(processo, movimento);
        auditoria.registrar(autor, "CRIAR_MOVIMENTACAO", "MOVIMENTACAO", movimento.getId(), true, null, ip);
        return movimentacaoResponse(movimento);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> listarMovimentacoes(ContextoTramitacao tipo, Long contextoId) {
        return movimentacoes.findByContextoTipoAndContextoIdOrderByDataMovimentacaoAscSequenciaDiariaAsc(
                tipo, contextoId).stream().map(this::movimentacaoResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarNotificacoes(UsuarioInterno usuario) {
        return notificacoes.findByDestinatarioIdOrderByCriadaEmDesc(usuario.getId()).stream()
                .map(n -> new NotificacaoResponse(n.getId(), n.getTipo(), n.getMensagem(), n.isLida(), n.getCriadaEm()))
                .toList();
    }

    @Transactional
    public NotificacaoResponse marcarNotificacaoLida(Long id, UsuarioInterno usuario) {
        Notificacao n = notificacoes.findById(id).orElseThrow(() -> new NotFoundException("Notificação não encontrada."));
        if (!Objects.equals(n.getDestinatario().getId(), usuario.getId())) {
            throw new DomainException("A notificação pertence a outro usuário.");
        }
        n.setLida(true);
        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getMensagem(), true, n.getCriadaEm());
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

    private void notificarChegada(ProcessoAdministrativo processo, Movimentacao movimento) {
        List<UsuarioInterno> destinatarios = new ArrayList<>();
        if (processo.getResponsavel() != null) destinatarios.add(processo.getResponsavel());
        else destinatarios.addAll(usuarios.findByAtivoTrue());
        destinatarios.stream()
                .filter(u -> !Objects.equals(u.getId(), movimento.getAutor().getId()))
                .forEach(u -> {
                    String chave = "CHEGADA:" + movimento.getId() + ":" + u.getId();
                    if (notificacoes.existsByChaveIdempotencia(chave)) return;
                    Notificacao n = new Notificacao();
                    n.setDestinatario(u);
                    n.setTipo("CHEGADA_TRAMITACAO");
                    n.setChaveIdempotencia(chave);
                    n.setMensagem("O Processo Administrativo " + processo.getNumero() + " chegou ao setor "
                            + movimento.getSetorDestino().getSigla() + ".");
                    notificacoes.save(n);
                });
    }

    private ProcessoAdministrativo processoDoContexto(ContextoTramitacao tipo, Long contextoId) {
        if (tipo == ContextoTramitacao.FORMALIZACAO) return processo(contextoId);
        var alteracao = alteracoes.findById(contextoId)
                .orElseThrow(() -> new NotFoundException("Alteração contratual não encontrada."));
        ContextoTramitacao esperado = alteracao.getTipo() == com.moments.sicc.domain.Enums.TipoAlteracao.TERMO_ADITIVO
                ? ContextoTramitacao.TERMO_ADITIVO : ContextoTramitacao.APOSTILAMENTO;
        if (tipo != esperado) throw new DomainException("O contexto não corresponde ao tipo da alteração.");
        return processo(alteracao.getInstrumento().getProcesso().getId());
    }

    private Documento validarDocumentoAssinado(Long documentoId, ProprietarioDocumento tipo, Long proprietarioId) {
        Documento documento = documentos.findById(documentoId)
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
        if (i != null) atualizarStatus(p, i);
        String setorAtual = movimentacoes
                .findFirstByContextoTipoAndContextoIdOrderByDataMovimentacaoDescSequenciaDiariaDesc(
                        ContextoTramitacao.FORMALIZACAO, p.getId())
                .map(m -> m.getSetorDestino().getSigla()).orElse(null);
        return new ProcessoResponse(p.getId(), p.getNumero(), p.getOrigem(), p.getNumeroProjeto(), p.getStatus(),
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
        atualizarStatus(p, i);
        return new ProjecaoPublica(new ProcessoPublicoResponse(
                p.getNumero(), i.getTipo().name(), p.getOrigem(), i.getCoordenador(),
                p.getStatus(), i.getVigenciaContratualFinal(), i.getVigenciaTedFinal()),
                situacao(i.getVigenciaContratualFinal()), situacao(i.getVigenciaTedFinal()));
    }

    private InstrumentoResponse instrumentoResponse(InstrumentoContratual i) {
        return new InstrumentoResponse(i.getId(), i.getProcesso().getId(), i.getNumero(), i.getTipo(),
                i.getObjeto(), i.getDescricao(), i.getNatureza(), i.getCoordenador(),
                List.of(i.getParticipes().split("\\n")), i.getValorAtual(), i.getVigenciaContratualFinal(),
                i.getVigenciaTedFinal(), i.getDataFormalizacao(), situacao(i.getVigenciaContratualFinal()),
                situacao(i.getVigenciaTedFinal()));
    }

    private MovimentacaoResponse movimentacaoResponse(Movimentacao m) {
        return new MovimentacaoResponse(m.getId(), m.getContextoTipo(), m.getContextoId(), m.getDataMovimentacao(),
                m.getSequenciaDiaria(), setorResponse(m.getSetorDestino()), usuarioResponse(m.getAutor()), m.getObservacao());
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
