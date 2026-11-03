package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.RelatorioGerado;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.RelatorioGeradoRepository;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import com.moments.sicc.shared.ChecksumArquivo;
import com.moments.sicc.shared.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelatorioDashboardService {
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final LeituraTramitacao leituraTramitacao;
    private final RelatorioGeradoRepository relatorios;
    private final ArmazenamentoTransacional storage;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final RegrasDeVigencia regrasDeVigencia;
    private final GeradorArquivoRelatorio geradorArquivo;
    private final CatalogoFiltrosRelatorio catalogoFiltros;
    private final GeracaoRelatorios geracaoRelatorios;
    private final IndicadoresPortfolio indicadores;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(
            String origem, TipoInstrumento tipo, StatusProcesso statusSelecionado) {
        LocalDate hoje = LocalDate.now(clock);
        ReferenciaDeVigencia referencia = regrasDeVigencia.referenciaEm(hoje);
        Map<Long, InstrumentoContratual> instrumentoPorProcesso = instrumentos
                .findAllByProcessoAtivoTrue().stream()
                .collect(java.util.stream.Collectors.toMap(
                        instrumento -> instrumento.getProcesso().getId(),
                        instrumento -> instrumento));
        List<ProcessoAdministrativo> selecionados = processos.findByAtivoTrue().stream()
                .filter(p -> contem(p.getOrigem(), origem))
                .filter(p -> tipo == null || instrumentoPorProcesso.containsKey(p.getId())
                        && instrumentoPorProcesso.get(p.getId()).getTipo() == tipo)
                .filter(p -> statusSelecionado == null || referencia.status(
                        instrumentoPorProcesso.containsKey(p.getId())
                                ? instrumentoPorProcesso.get(p.getId()).getVigenciaContratualFinal() : null)
                        == statusSelecionado).toList();
        return indicadores.calcular(selecionados, instrumentoPorProcesso, hoje, referencia);
    }

    @Transactional
    public RelatorioResponse gerar(GerarRelatorioRequest request, UsuarioInterno autor, String ip) {
        request = new GerarRelatorioRequest(
                request.tipo(), request.formato(),
                catalogoFiltros.normalizar(request.tipo(), request.filtros()));
        EmissaoRelatorio emissao = EmissaoRelatorio.iniciar(
                clock, regrasDeVigencia, autor);
        ConteudoRelatorio conteudo = geracaoRelatorios.gerar(
                request.tipo(),
                new GeracaoRelatorios.Contexto(
                        request.filtros(),
                        processosFiltrados(request.filtros(), emissao.referencia()),
                        emissao));
        byte[] content = geradorArquivo.gerar(request.formato(), conteudo);
        RelatorioGerado relatorio = new RelatorioGerado();
        relatorio.setTipo(request.tipo());
        relatorio.setFormato(request.formato());
        relatorio.setFiltros(catalogoFiltros.serializar(request.filtros()));
        relatorio.setChaveArmazenamento(storage.armazenar(content,
                "relatorios/" + request.tipo().name().toLowerCase(Locale.ROOT)));
        relatorio.setChecksumSha256(ChecksumArquivo.sha256(content));
        relatorio.setTamanhoBytes((long) content.length);
        relatorio.setCriadoPor(autor);
        relatorio.setCriadoEm(emissao.geradoEm());
        relatorios.save(relatorio);
        auditoria.registrarNaTransacaoAtual(
                autor, "GERAR_RELATORIO", "RELATORIO", relatorio.getId(), true,
                request.tipo() + "/" + request.formato(), ip);
        return response(relatorio);
    }

    @Transactional(readOnly = true)
    public List<RelatorioResponse> listar() {
        return relatorios.findAllByOrderByCriadoEmDesc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public FiltrosRelatorioResponse filtros() {
        List<ProcessoAdministrativo> ativos = processos.findByAtivoTrue();
        var setores = leituraTramitacao.setoresAtuais(ativos.stream().map(ProcessoAdministrativo::getId).toList())
                .values().stream().map(s -> new OpcaoFiltroRelatorioResponse(
                        s.id(), s.sigla() + " · " + s.nome() + (s.ativo() ? "" : " (inativo)")))
                .distinct().sorted(Comparator.comparing(OpcaoFiltroRelatorioResponse::rotulo)).toList();
        var responsaveis = ativos.stream().map(ProcessoAdministrativo::getResponsavel)
                .filter(java.util.Objects::nonNull)
                .map(u -> new OpcaoFiltroRelatorioResponse(u.getId(), u.getNome() + " (" + u.getLogin() + ")" + (u.isAtivo() ? "" : " (inativo)")))
                .distinct().sorted(Comparator.comparing(OpcaoFiltroRelatorioResponse::rotulo)).toList();
        return new FiltrosRelatorioResponse(setores, responsaveis);
    }

    @Transactional
    public Download download(Long id, UsuarioInterno autor, String ip) {
        RelatorioGerado r = relatorios.findById(id)
                .orElseThrow(() -> new NotFoundException("Relatório não encontrado."));
        String mime = geradorArquivo.mime(r.getFormato());
        Resource resource = storage.carregar(r.getChaveArmazenamento());
        auditoria.registrarNaTransacaoAtual(
                autor, "DOWNLOAD_RELATORIO", "RELATORIO", id, true, null, ip);
        return new Download(resource, nomeArquivo(r), mime);
    }

    private List<ProcessoAdministrativo> processosFiltrados(
            Map<String, String> filtros, ReferenciaDeVigencia referencia) {
        List<ProcessoAdministrativo> ativos = processos.findByAtivoTrue();
        String setorId = filtro(filtros, "setorId");
        String responsavelId = filtro(filtros, "responsavelId");
        var setores = setorId == null ? Map.<Long, LeituraTramitacao.SetorLido>of()
                : leituraTramitacao.setoresAtuais(ativos.stream().map(ProcessoAdministrativo::getId).toList());
        return ativos.stream()
                .filter(p -> contem(p.getNumero(), filtro(filtros, "numero")))
                .filter(p -> contem(p.getOrigem(), filtro(filtros, "origem")))
                .filter(p -> responsavelId == null || (responsavelId.equals("SEM_RESPONSAVEL")
                        ? p.getResponsavel() == null
                        : p.getResponsavel() != null && p.getResponsavel().getId().toString().equals(responsavelId)))
                .filter(p -> setorId == null || (setorId.equals("SEM_SETOR") ? !setores.containsKey(p.getId())
                        : setores.containsKey(p.getId()) && setores.get(p.getId()).id().toString().equals(setorId)))
                .filter(p -> {
                    InstrumentoContratual i = instrumentos.findByProcessoId(p.getId()).orElse(null);
                    String tipo = filtro(filtros, "tipo");
                    String status = filtro(filtros, "status");
                    String vigenciaContratual = filtro(filtros, "vigenciaContratual");
                    String vigenciaTed = filtro(filtros, "vigenciaTed");
                    return (tipo == null || i != null && i.getTipo().name().equals(tipo))
                            && (status == null || referencia.status(
                                    i == null ? null : i.getVigenciaContratualFinal())
                                    .name().equals(status))
                            && (vigenciaContratual == null || i != null
                            && referencia.situacao(i.getVigenciaContratualFinal()).name()
                            .equals(vigenciaContratual))
                            && (vigenciaTed == null || i != null
                            && referencia.situacao(i.getVigenciaTedFinal()).name()
                                    .equals(vigenciaTed));
                })
                .toList();
    }

    private boolean contem(String valor, String trecho) {
        return trecho == null || valor.toLowerCase(Locale.ROOT).contains(trecho.toLowerCase(Locale.ROOT));
    }

    private String filtro(Map<String, String> filtros, String nome) {
        if (filtros == null) return null;
        String valor = filtros.get(nome);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private RelatorioResponse response(RelatorioGerado r) {
        UsuarioInterno autor = r.getCriadoPor();
        return new RelatorioResponse(
                r.getId(), r.getTipo(), r.getFormato(),
                catalogoFiltros.desserializar(r.getFiltros()),
                new AtorAuditoriaResponse(autor.getId(), autor.getLogin(), autor.getNome()),
                r.getCriadoEm(), r.getChecksumSha256(), r.getChaveArmazenamento(),
                r.getTamanhoBytes() == null ? 0 : r.getTamanhoBytes(), nomeArquivo(r));
    }

    private String nomeArquivo(RelatorioGerado relatorio) {
        String ext = relatorio.getFormato().name().toLowerCase(Locale.ROOT);
        return relatorio.getTipo().name().toLowerCase(Locale.ROOT)
                + "-" + relatorio.getId() + "." + ext;
    }

    public record Download(Resource resource, String filename, String mimeType) {}
}
