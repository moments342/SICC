package com.moments.sicc.service;

import com.moments.sicc.domain.AlteracaoContratual;
import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.Enums.TipoAlteracao;
import com.moments.sicc.domain.InstrumentoContratual;
import com.moments.sicc.domain.ProcessoAdministrativo;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProcessoMutacaoGuard {
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final AlteracaoContratualRepository alteracoes;
    private final DocumentoRepository documentos;

    @Transactional(propagation = Propagation.MANDATORY)
    public ContextoProcesso processoAtivo(Long processoId) {
        return bloquearProcesso(processoId, this::processoNaoEncontrado);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContextoInstrumento instrumentoEmProcessoAtivo(Long instrumentoId) {
        InstrumentoContratualRepository.HierarquiaInstrumento hierarquia = instrumentos
                .findHierarquiaById(instrumentoId)
                .orElseThrow(this::instrumentoNaoEncontrado);
        return bloquearInstrumento(hierarquia, this::instrumentoNaoEncontrado);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContextoAlteracao alteracaoEmProcessoAtivo(Long alteracaoId) {
        AlteracaoContratualRepository.HierarquiaAlteracao hierarquia = alteracoes
                .findHierarquiaById(alteracaoId)
                .orElseThrow(this::alteracaoNaoEncontrada);
        return bloquearAlteracao(hierarquia, this::alteracaoNaoEncontrada);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContextoProprietario proprietarioEmProcessoAtivo(
            ProprietarioDocumento tipo, Long proprietarioId) {
        return bloquearProprietario(
                tipo, proprietarioId, this::proprietarioNaoEncontrado);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContextoDocumento documentoEmProcessoAtivo(Long documentoId) {
        DocumentoRepository.HierarquiaDocumento hierarquiaInicial = documentos
                .findHierarquiaById(documentoId)
                .orElseThrow(this::documentoNaoEncontrado);
        ContextoProprietario proprietario = bloquearProprietario(
                hierarquiaInicial.getProprietarioTipo(),
                hierarquiaInicial.getProprietarioId(),
                this::documentoNaoEncontrado);
        DocumentoRepository.HierarquiaDocumento hierarquiaAtual = documentos
                .findHierarquiaById(documentoId)
                .orElseThrow(this::documentoNaoEncontrado);
        if (!mesmoProprietario(hierarquiaInicial, hierarquiaAtual)) {
            proprietario = bloquearInstrumentoAposFormalizacao(
                    hierarquiaInicial, hierarquiaAtual, proprietario);
        }
        Documento documento = documentos.findByIdComBloqueio(hierarquiaAtual.getDocumentoId())
                .orElseThrow(this::documentoNaoEncontrado);
        if (documento.getProprietarioTipo() != hierarquiaAtual.getProprietarioTipo()
                || !Objects.equals(
                        documento.getProprietarioId(), hierarquiaAtual.getProprietarioId())) {
            throw documentoNaoEncontrado();
        }
        return new ContextoDocumento(proprietario, documento);
    }

    private boolean mesmoProprietario(
            DocumentoRepository.HierarquiaDocumento esperado,
            DocumentoRepository.HierarquiaDocumento atual) {
        return esperado.getProprietarioTipo() == atual.getProprietarioTipo()
                && Objects.equals(esperado.getProprietarioId(), atual.getProprietarioId());
    }

    private ContextoInstrumento bloquearInstrumentoAposFormalizacao(
            DocumentoRepository.HierarquiaDocumento hierarquiaInicial,
            DocumentoRepository.HierarquiaDocumento hierarquiaAtual,
            ContextoProprietario proprietarioInicial) {
        if (hierarquiaInicial.getProprietarioTipo() != ProprietarioDocumento.PROCESSO
                || hierarquiaAtual.getProprietarioTipo() != ProprietarioDocumento.INSTRUMENTO) {
            throw documentoNaoEncontrado();
        }
        InstrumentoContratualRepository.HierarquiaInstrumento instrumentoAtual = instrumentos
                .findHierarquiaById(hierarquiaAtual.getProprietarioId())
                .orElseThrow(this::documentoNaoEncontrado);
        if (!Objects.equals(
                instrumentoAtual.getProcessoId(), proprietarioInicial.processo().getId())) {
            throw documentoNaoEncontrado();
        }
        InstrumentoContratual instrumento = instrumentos
                .findByIdForUpdate(instrumentoAtual.getInstrumentoId())
                .orElseThrow(this::documentoNaoEncontrado);
        if (!Objects.equals(
                instrumento.getProcesso().getId(), proprietarioInicial.processo().getId())) {
            throw documentoNaoEncontrado();
        }
        return new ContextoInstrumento(proprietarioInicial.processo(), instrumento);
    }

    private ContextoProprietario bloquearProprietario(
            ProprietarioDocumento tipo,
            Long proprietarioId,
            Supplier<NotFoundException> naoEncontrado) {
        return switch (tipo) {
            case PROCESSO -> bloquearProcesso(proprietarioId, naoEncontrado);
            case INSTRUMENTO -> {
                InstrumentoContratualRepository.HierarquiaInstrumento hierarquia = instrumentos
                        .findHierarquiaById(proprietarioId)
                        .orElseThrow(naoEncontrado);
                yield bloquearInstrumento(hierarquia, naoEncontrado);
            }
            case TERMO_ADITIVO -> bloquearAlteracao(
                    proprietarioId, TipoAlteracao.TERMO_ADITIVO, naoEncontrado);
            case APOSTILAMENTO -> bloquearAlteracao(
                    proprietarioId, TipoAlteracao.APOSTILAMENTO, naoEncontrado);
        };
    }

    private ContextoAlteracao bloquearAlteracao(
            Long alteracaoId,
            TipoAlteracao tipoEsperado,
            Supplier<NotFoundException> naoEncontrado) {
        AlteracaoContratualRepository.HierarquiaAlteracao hierarquia = alteracoes
                .findHierarquiaById(alteracaoId)
                .orElseThrow(naoEncontrado);
        if (hierarquia.getTipo() != tipoEsperado) throw naoEncontrado.get();
        ContextoAlteracao contexto = bloquearAlteracao(hierarquia, naoEncontrado);
        if (contexto.alteracao().getTipo() != tipoEsperado) throw naoEncontrado.get();
        return contexto;
    }

    private ContextoAlteracao bloquearAlteracao(
            AlteracaoContratualRepository.HierarquiaAlteracao hierarquia,
            Supplier<NotFoundException> naoEncontrado) {
        ContextoInstrumento instrumento = bloquearInstrumento(
                hierarquia.getInstrumentoId(),
                hierarquia.getProcessoId(),
                naoEncontrado);
        AlteracaoContratual alteracao = alteracoes
                .findByIdForUpdate(hierarquia.getAlteracaoId())
                .orElseThrow(naoEncontrado);
        if (!Objects.equals(
                        alteracao.getInstrumento().getId(), instrumento.instrumento().getId())
                || alteracao.getTipo() != hierarquia.getTipo()) {
            throw naoEncontrado.get();
        }
        return new ContextoAlteracao(
                instrumento.processo(), instrumento.instrumento(), alteracao);
    }

    private ContextoInstrumento bloquearInstrumento(
            InstrumentoContratualRepository.HierarquiaInstrumento hierarquia,
            Supplier<NotFoundException> naoEncontrado) {
        return bloquearInstrumento(
                hierarquia.getInstrumentoId(),
                hierarquia.getProcessoId(),
                naoEncontrado);
    }

    private ContextoInstrumento bloquearInstrumento(
            Long instrumentoId,
            Long processoId,
            Supplier<NotFoundException> naoEncontrado) {
        ContextoProcesso processo = bloquearProcesso(processoId, naoEncontrado);
        InstrumentoContratual instrumento = instrumentos.findByIdForUpdate(instrumentoId)
                .orElseThrow(naoEncontrado);
        if (!Objects.equals(instrumento.getProcesso().getId(), processo.processo().getId())) {
            throw naoEncontrado.get();
        }
        return new ContextoInstrumento(processo.processo(), instrumento);
    }

    private ContextoProcesso bloquearProcesso(
            Long processoId, Supplier<NotFoundException> naoEncontrado) {
        ProcessoAdministrativo processo = processos.findByIdForUpdate(processoId)
                .orElseThrow(naoEncontrado);
        if (!processo.isAtivo()) {
            throw new DomainException("O Processo Administrativo está inativo.");
        }
        return new ContextoProcesso(processo);
    }

    private NotFoundException processoNaoEncontrado() {
        return new NotFoundException("Processo Administrativo não encontrado.");
    }

    private NotFoundException instrumentoNaoEncontrado() {
        return new NotFoundException("Instrumento Contratual não encontrado.");
    }

    private NotFoundException alteracaoNaoEncontrada() {
        return new NotFoundException("Alteração contratual não encontrada.");
    }

    private NotFoundException proprietarioNaoEncontrado() {
        return new NotFoundException("Proprietário do documento não encontrado.");
    }

    private NotFoundException documentoNaoEncontrado() {
        return new NotFoundException("Documento não encontrado.");
    }

    public sealed interface ContextoProprietario
            permits ContextoProcesso, ContextoInstrumento, ContextoAlteracao {
        ProcessoAdministrativo processo();
    }

    public record ContextoProcesso(ProcessoAdministrativo processo)
            implements ContextoProprietario {}

    public record ContextoInstrumento(
            ProcessoAdministrativo processo, InstrumentoContratual instrumento)
            implements ContextoProprietario {}

    public record ContextoAlteracao(
            ProcessoAdministrativo processo,
            InstrumentoContratual instrumento,
            AlteracaoContratual alteracao)
            implements ContextoProprietario {}

    public record ContextoDocumento(
            ContextoProprietario proprietario, Documento documento) {}
}
