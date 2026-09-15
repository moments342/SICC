package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.Movimentacao;
import com.moments.sicc.domain.Notificacao;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.service.CalculadoraPermanencia.Periodo;
import com.moments.sicc.service.RegrasDeVigencia.ReferenciaDeVigencia;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjecoesSicc {

    public ProcessoResponse processo(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            String setorAtual,
            ReferenciaDeVigencia referencia) {
        var status = referencia.status(
                instrumento == null ? null : instrumento.getVigenciaContratualFinal());
        return new ProcessoResponse(
                processo.getId(),
                processo.getNumero(),
                processo.getOrigem(),
                processo.getNumeroProjeto(),
                status,
                processo.getDataCadastro(),
                processo.isAtivo(),
                processo.getResponsavel() == null
                        ? null : usuario(processo.getResponsavel()),
                setorAtual,
                instrumento == null ? null : instrumento(instrumento, referencia));
    }

    public ProcessoPublicoResponse processoPublico(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            ReferenciaDeVigencia referencia) {
        if (instrumento == null) {
            return new ProcessoPublicoResponse(
                    processo.getNumero(),
                    "Ainda não formalizado",
                    processo.getOrigem(),
                    "Ainda não formalizado",
                    referencia.status(null),
                    null,
                    null);
        }
        return new ProcessoPublicoResponse(
                processo.getNumero(),
                instrumento.getTipo().name(),
                processo.getOrigem(),
                instrumento.getCoordenador(),
                referencia.status(instrumento.getVigenciaContratualFinal()),
                instrumento.getVigenciaContratualFinal(),
                instrumento.getVigenciaTedFinal());
    }

    public InstrumentoResponse instrumento(
            InstrumentoContratual instrumento, ReferenciaDeVigencia referencia) {
        return new InstrumentoResponse(
                instrumento.getId(),
                instrumento.getProcesso().getId(),
                instrumento.getNumero(),
                instrumento.getTipo(),
                instrumento.getObjeto(),
                instrumento.getDescricao(),
                instrumento.getNatureza(),
                instrumento.getCoordenador(),
                List.of(instrumento.getParticipes().split("\\n")),
                instrumento.getValorAtual(),
                instrumento.getVigenciaContratualFinal(),
                instrumento.getVigenciaTedFinal(),
                instrumento.getDataFormalizacao(),
                instrumento.getDocumentoAssinado().getId(),
                instrumento.getDocumentoAssinadoVersao().getVersao(),
                instrumento.getDocumentoAssinadoVersao().getChecksumSha256(),
                referencia.situacao(instrumento.getVigenciaContratualFinal()),
                referencia.situacao(instrumento.getVigenciaTedFinal()));
    }

    public MovimentacaoResponse movimentacao(Movimentacao movimentacao) {
        return new MovimentacaoResponse(
                movimentacao.getId(),
                movimentacao.getContextoTipo(),
                movimentacao.getContextoId(),
                movimentacao.getDataMovimentacao(),
                movimentacao.getSequenciaDiaria(),
                setor(movimentacao.getSetorDestino()),
                usuario(movimentacao.getAutor()),
                movimentacao.getObservacao(),
                movimentacao.getInseridoEm());
    }

    public List<PermanenciaSetorResponse> permanencias(List<Periodo> periodos) {
        return periodos.stream()
                .map(periodo -> new PermanenciaSetorResponse(
                        setor(periodo.setor()),
                        periodo.dataChegada(),
                        periodo.dataSaida(),
                        periodo.diasCorridos(),
                        periodo.aberta()))
                .toList();
    }

    public NotificacaoResponse notificacao(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getTipo().name(),
                notificacao.getMensagem(),
                notificacao.getProcesso() == null
                        ? null : notificacao.getProcesso().getId(),
                notificacao.isLida(),
                notificacao.getCriadaEm());
    }

    public ResponsavelProcessoResponse responsavel(UsuarioInterno usuario) {
        return new ResponsavelProcessoResponse(
                usuario.getId(), usuario.getNome(), usuario.getPerfil());
    }

    public UsuarioResponse usuario(UsuarioInterno usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getLogin(),
                usuario.getPerfil(),
                usuario.isAtivo(),
                usuario.isSenhaTemporaria());
    }

    public SetorResponse setor(Setor setor) {
        return new SetorResponse(
                setor.getId(), setor.getSigla(), setor.getNome(), setor.isAtivo());
    }
}
