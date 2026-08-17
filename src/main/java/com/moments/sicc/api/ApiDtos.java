package com.moments.sicc.api;

import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.EstadoAlteracao;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.OperacaoAlteracao;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.ResultadoAuditoria;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class ApiDtos {
    private ApiDtos() {}

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {}
    public record LoginResponse(String token, String perfil, boolean trocaSenhaObrigatoria) {}
    public record TrocarSenhaRequest(@NotBlank String senhaAtual, @NotBlank String novaSenha) {}
    public record CriarUsuarioRequest(
            @NotBlank String nome, @Email @NotBlank String email, @NotBlank String login,
            @NotBlank String senhaTemporaria, @NotNull PerfilAcesso perfil) {}
    public record ResetarSenhaRequest(@NotBlank String novaSenhaTemporaria) {}
    public record UsuarioResponse(
            Long id, String nome, String email, String login, PerfilAcesso perfil,
            boolean ativo, boolean senhaTemporaria) {}
    public record CriarSetorRequest(
            @NotBlank @Size(max = 30) String sigla,
            @NotBlank @Size(max = 150) String nome) {}
    public record AtualizarSetorRequest(
            @NotBlank @Size(max = 30) String sigla,
            @NotBlank @Size(max = 150) String nome) {}
    public record SetorResponse(Long id, String sigla, String nome, boolean ativo) {}
    public record CriarProcessoRequest(
            @NotBlank @Size(max = 60) String numero,
            @NotBlank @Size(max = 150) String origem,
            @Size(max = 80) String numeroProjeto,
            Long responsavelId,
            @Null(message = "status não deve ser informado manualmente") StatusProcesso status) {}
    public record AtualizarProcessoRequest(
            @NotBlank @Size(max = 150) String origem,
            @Size(max = 80) String numeroProjeto,
            Long responsavelId) {}
    public record FormalizarInstrumentoRequest(
            @NotBlank @Size(max = 60) String numero,
            @NotNull TipoInstrumento tipo,
            @NotBlank @Size(max = 1000) String objeto,
            @Size(max = 2000) String descricao,
            @NotBlank @Size(max = 150) String natureza,
            @NotBlank @Size(max = 150) String coordenador,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 500) String> participes,
            @NotNull @PositiveOrZero BigDecimal valorAtual,
            @NotNull LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal,
            @NotNull LocalDate dataFormalizacao, @NotNull Long documentoAssinadoId) {}
    public record InstrumentoResponse(
            Long id, Long processoId, String numero, TipoInstrumento tipo, String objeto,
            String descricao, String natureza, String coordenador, List<String> participes,
            BigDecimal valorAtual, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal,
            LocalDate dataFormalizacao, Long documentoAssinadoId,
            SituacaoVigencia situacaoContratual, SituacaoVigencia situacaoTed) {}
    public record ProcessoResponse(
            Long id, String numero, String origem, String numeroProjeto, StatusProcesso status,
            LocalDate dataCadastro, boolean ativo, UsuarioResponse responsavel,
            String setorAtual, InstrumentoResponse instrumento) {}
    public record ResponsavelProcessoResponse(Long id, String nome, PerfilAcesso perfil) {}
    public record ProcessoPublicoResponse(
            String numeroProcesso, String tipoInstrumento, String origem, String coordenador,
            StatusProcesso status, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal) {}
    public record CriarMovimentacaoRequest(
            @NotNull ContextoTramitacao contextoTipo, @NotNull Long contextoId,
            @NotNull LocalDate dataMovimentacao, @NotNull Long setorDestinoId,
            @NotBlank @Size(max = 1000) String observacao) {}
    public record MovimentacaoResponse(
            Long id, ContextoTramitacao contextoTipo, Long contextoId, LocalDate dataMovimentacao,
            int sequenciaDiaria, SetorResponse setorDestino, UsuarioResponse autor,
            String observacao, LocalDateTime inseridoEm) {}
    public record PermanenciaSetorResponse(
            SetorResponse setor, LocalDate dataChegada, LocalDate dataSaida,
            long diasCorridos, boolean aberta) {}
    public record HistoricoTramitacaoResponse(
            SetorResponse setorAtual, List<MovimentacaoResponse> movimentacoes,
            List<PermanenciaSetorResponse> permanencias) {}
    public record NotificacaoResponse(
            Long id, String tipo, String mensagem, Long processoId,
            boolean lida, LocalDateTime criadaEm) {}
    public record CriarDocumentoRequest(
            @NotNull ProprietarioDocumento proprietarioTipo, @NotNull Long proprietarioId,
            @NotNull CategoriaDocumento categoria, @NotBlank String titulo) {}
    public record AutorDocumentoResponse(Long id, String nome) {}
    public record DocumentoResponse(
            Long id, ProprietarioDocumento proprietarioTipo, Long proprietarioId,
            CategoriaDocumento categoria, String titulo, boolean ativo,
            AutorDocumentoResponse criadoPor, LocalDateTime criadoEm,
            List<VersaoDocumentoResponse> versoes) {}
    public record VersaoDocumentoResponse(
            int versao, String nomeArquivo, String tipoMime, long tamanho,
            String checksumSha256, AutorDocumentoResponse criadoPor, LocalDateTime criadoEm) {}
    public record CriarAlteracaoRequest(
            @NotNull Long instrumentoId, @NotNull TipoAlteracao tipo,
            @NotBlank @Size(max = 80) String numeroOficial,
            @NotNull OperacaoAlteracao operacao, Long referenciaId,
            List<@Valid MudancaAlteracaoRequest> mudancas) {}
    public record MudancaAlteracaoRequest(
            @NotNull CampoInstrumento campo, String valorAnterior, String valorNovo) {}
    public record AtualizarRascunhoAlteracaoRequest(
            @NotBlank @Size(max = 80) String numeroOficial,
            @NotEmpty List<@Valid MudancaAlteracaoRequest> mudancas) {}
    public record EfetivarAlteracaoRequest(
            @NotNull LocalDate dataEfetivacao, @NotNull @Positive Integer ordemOficial,
            @NotNull Long documentoAssinadoId) {}
    public record MudancaAlteracaoResponse(
            CampoInstrumento campo, String valorAnterior, String valorNovo) {}
    public record PrecedenciaCampoResponse(LocalDate dataEfetivacao, Integer ordemOficial) {}
    public record EstadoAtualInstrumentoResponse(
            String objeto, String descricao, String natureza, String coordenador,
            List<String> participes, BigDecimal valorAtual,
            LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal,
            StatusProcesso statusProcesso,
            Map<CampoInstrumento, PrecedenciaCampoResponse> precedenciaPorCampo) {}
    public record AlteracaoVinculadaResponse(
            Long id, String numeroOficial, TipoAlteracao tipo, EstadoAlteracao estado,
            OperacaoAlteracao operacao, Long referenciaId, LocalDate dataEfetivacao,
            Integer ordemOficial, boolean produzEfeitoAtual,
            Map<CampoInstrumento, String> valoresProduzidos) {}
    public record AlteracaoResponse(
            Long id, Long instrumentoId, TipoAlteracao tipo, EstadoAlteracao estado,
            String numeroOficial, Integer ordemOficial, LocalDate dataEfetivacao,
            OperacaoAlteracao operacao, Long referenciaId, Long documentoAssinadoId,
            List<MudancaAlteracaoResponse> mudancas,
            EstadoAtualInstrumentoResponse estadoAtualInstrumento,
            HistoricoTramitacaoResponse tramitacao,
            List<AlteracaoVinculadaResponse> cadeia) {}
    public record PermanenciaProcessoDashboardResponse(
            Long processoId, String numeroProcesso, LocalDate dataChegada,
            LocalDate dataSaida, long diasCorridos, boolean aberta) {}
    public record TempoTramitacaoInicialProcessoResponse(
            Long processoId, String numeroProcesso, LocalDate dataCadastro,
            LocalDate dataFormalizacao, long diasCorridos, boolean aberta) {}
    public record DashboardResponse(
            Map<StatusProcesso, Long> processosPorStatus, double percentualConcluidos,
            long alertasContratuais, long alertasTed, BigDecimal valorTotalVigente,
            Map<TipoInstrumento, Long> instrumentosPorTipo, Map<String, Double> permanenciaMediaPorSetor,
            String maiorGargalo,
            Map<String, List<PermanenciaProcessoDashboardResponse>> detalhesPermanenciaPorSetor,
            double tempoMedioTramitacaoInicialDias,
            List<TempoTramitacaoInicialProcessoResponse> detalhesTempoTramitacaoInicial,
            Map<String, Long> formalizacoesMensais, Map<String, Long> conclusoesMensais) {}
    public record GerarRelatorioRequest(
            @NotNull TipoRelatorio tipo, @NotNull FormatoRelatorio formato, Map<String, String> filtros) {}
    public record RelatorioResponse(
            Long id, TipoRelatorio tipo, FormatoRelatorio formato, Map<String, String> filtros,
            AtorAuditoriaResponse criadoPor, LocalDateTime criadoEm, String checksumSha256,
            String chaveArmazenamento, long tamanhoBytes, String nomeArquivo) {}
    public record AtorAuditoriaResponse(Long id, String login, String nome) {}
    public record ObjetoAuditoriaResponse(String tipo, Long id) {}
    public record RegistroAuditoriaResponse(
            Long id, String acao, ResultadoAuditoria resultado, AtorAuditoriaResponse ator,
            ObjetoAuditoriaResponse objeto, String detalhes, String ipOrigem, LocalDateTime criadoEm) {}
    public record ErroResponse(int status, String erro, String mensagem, LocalDateTime instante) {}
    public record PaginaResponse<T>(
            List<T> content, long totalElements, int totalPages, int number, int size) {
        public static <T> PaginaResponse<T> de(Page<T> page) {
            return new PaginaResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                    page.getNumber(), page.getSize());
        }
    }
}
