package com.moments.sicc.api;

import com.moments.sicc.domain.Enums.CampoInstrumento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ContextoTramitacao;
import com.moments.sicc.domain.Enums.EstadoAlteracao;
import com.moments.sicc.domain.Enums.FormatoRelatorio;
import com.moments.sicc.domain.Enums.OperacaoAlteracao;
import com.moments.sicc.domain.Enums.PerfilAcesso;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.SituacaoVigencia;
import com.moments.sicc.domain.Enums.StatusProcesso;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.Enums.TipoInstrumento;
import com.moments.sicc.domain.Enums.TipoRelatorio;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
    public record CriarSetorRequest(@NotBlank String sigla, @NotBlank String nome) {}
    public record SetorResponse(Long id, String sigla, String nome, boolean ativo) {}
    public record CriarProcessoRequest(
            @NotBlank String numero, @NotBlank String origem, String numeroProjeto, Long responsavelId) {}
    public record AtualizarProcessoRequest(@NotBlank String origem, String numeroProjeto, Long responsavelId) {}
    public record FormalizarInstrumentoRequest(
            @NotBlank String numero, @NotNull TipoInstrumento tipo, @NotBlank String objeto,
            String descricao, @NotBlank String natureza, @NotBlank String coordenador,
            @NotEmpty List<String> participes, @NotNull @PositiveOrZero BigDecimal valorAtual,
            @NotNull LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal,
            @NotNull LocalDate dataFormalizacao, @NotNull Long documentoAssinadoId) {}
    public record InstrumentoResponse(
            Long id, Long processoId, String numero, TipoInstrumento tipo, String objeto,
            String descricao, String natureza, String coordenador, List<String> participes,
            BigDecimal valorAtual, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal,
            LocalDate dataFormalizacao, SituacaoVigencia situacaoContratual, SituacaoVigencia situacaoTed) {}
    public record ProcessoResponse(
            Long id, String numero, String origem, String numeroProjeto, StatusProcesso status,
            LocalDate dataCadastro, boolean ativo, UsuarioResponse responsavel,
            String setorAtual, InstrumentoResponse instrumento) {}
    public record ProcessoPublicoResponse(
            String numeroProcesso, String tipoInstrumento, String origem, String coordenador,
            StatusProcesso status, LocalDate vigenciaContratualFinal, LocalDate vigenciaTedFinal) {}
    public record CriarMovimentacaoRequest(
            @NotNull ContextoTramitacao contextoTipo, @NotNull Long contextoId,
            @NotNull LocalDate dataMovimentacao, @NotNull Long setorDestinoId, String observacao) {}
    public record MovimentacaoResponse(
            Long id, ContextoTramitacao contextoTipo, Long contextoId, LocalDate dataMovimentacao,
            int sequenciaDiaria, SetorResponse setorDestino, UsuarioResponse autor, String observacao) {}
    public record NotificacaoResponse(
            Long id, String tipo, String mensagem, boolean lida, LocalDateTime criadaEm) {}
    public record CriarDocumentoRequest(
            @NotNull ProprietarioDocumento proprietarioTipo, @NotNull Long proprietarioId,
            @NotNull CategoriaDocumento categoria, @NotBlank String titulo) {}
    public record DocumentoResponse(
            Long id, ProprietarioDocumento proprietarioTipo, Long proprietarioId,
            CategoriaDocumento categoria, String titulo, boolean ativo, List<VersaoDocumentoResponse> versoes) {}
    public record VersaoDocumentoResponse(
            int versao, String nomeArquivo, String tipoMime, long tamanho,
            String checksumSha256, LocalDateTime criadoEm) {}
    public record CriarAlteracaoRequest(
            @NotNull Long instrumentoId, @NotNull TipoAlteracao tipo, @NotBlank String numeroOficial,
            @NotNull OperacaoAlteracao operacao, Long referenciaId, Map<CampoInstrumento, String> alteracoes) {}
    public record EfetivarAlteracaoRequest(
            @NotNull LocalDate dataEfetivacao, @NotNull Integer ordemOficial, @NotNull Long documentoAssinadoId) {}
    public record AlteracaoResponse(
            Long id, Long instrumentoId, TipoAlteracao tipo, EstadoAlteracao estado,
            String numeroOficial, Integer ordemOficial, LocalDate dataEfetivacao,
            OperacaoAlteracao operacao, Long referenciaId, Map<CampoInstrumento, String> alteracoes) {}
    public record DashboardResponse(
            Map<StatusProcesso, Long> processosPorStatus, double percentualConcluidos,
            long alertasContratuais, long alertasTed, BigDecimal valorTotalVigente,
            Map<TipoInstrumento, Long> instrumentosPorTipo, Map<String, Double> permanenciaMediaPorSetor,
            String maiorGargalo, double tempoMedioTramitacaoInicialDias,
            Map<String, Long> formalizacoesMensais, Map<String, Long> conclusoesMensais) {}
    public record GerarRelatorioRequest(
            @NotNull TipoRelatorio tipo, @NotNull FormatoRelatorio formato, Map<String, String> filtros) {}
    public record RelatorioResponse(
            Long id, TipoRelatorio tipo, FormatoRelatorio formato, String filtros, LocalDateTime criadoEm) {}
    public record ErroResponse(int status, String erro, String mensagem, LocalDateTime instante) {}
    public record PaginaResponse<T>(
            List<T> content, long totalElements, int totalPages, int number, int size) {
        public static <T> PaginaResponse<T> de(Page<T> page) {
            return new PaginaResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                    page.getNumber(), page.getSize());
        }
    }
}
