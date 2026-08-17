package com.moments.sicc.service;

import com.moments.sicc.api.ApiDtos.AtualizarSetorRequest;
import com.moments.sicc.api.ApiDtos.CriarSetorRequest;
import com.moments.sicc.api.ApiDtos.SetorResponse;
import com.moments.sicc.domain.IdentidadeSetor;
import com.moments.sicc.domain.Setor;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.SetorRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogoSetorService {
    private final SetorRepository setores;
    private final AuditoriaService auditoria;

    @Transactional
    public SetorResponse criar(CriarSetorRequest request, UsuarioInterno autor, String ip) {
        IdentidadeSetor identidade = IdentidadeSetor.de(request.sigla(), request.nome());
        validarUnicidade(identidade, null);
        Setor setor = new Setor();
        setor.atualizarIdentidade(identidade);
        salvarComIdentidadeUnica(setor);
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_SETOR", "SETOR", setor.getId(), true, null, ip);
        return resposta(setor);
    }

    @Transactional(readOnly = true)
    public List<SetorResponse> listarTodos() {
        return setores.findAllByOrderBySiglaAsc().stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public List<SetorResponse> listarAtivos() {
        return setores.findByAtivoTrueOrderBySigla().stream().map(this::resposta).toList();
    }

    @Transactional
    public SetorResponse atualizar(
            Long id, AtualizarSetorRequest request, UsuarioInterno autor, String ip) {
        Setor setor = buscar(id);
        IdentidadeSetor identidade = IdentidadeSetor.de(request.sigla(), request.nome());
        validarUnicidade(identidade, id);
        if (setor.getSigla().equals(identidade.sigla())
                && setor.getNome().equals(identidade.nome())) {
            return resposta(setor);
        }
        String detalhes = "sigla: %s -> %s; nome: %s -> %s"
                .formatted(setor.getSigla(), identidade.sigla(), setor.getNome(), identidade.nome());
        setor.atualizarIdentidade(identidade);
        salvarComIdentidadeUnica(setor);
        auditoria.registrarNaTransacaoAtual(
                autor, "ALTERAR_SETOR", "SETOR", setor.getId(), true, detalhes, ip);
        return resposta(setor);
    }

    @Transactional
    public SetorResponse definirAtivo(Long id, boolean ativo, UsuarioInterno autor, String ip) {
        Setor setor = buscar(id);
        if (setor.isAtivo() == ativo) {
            return resposta(setor);
        }
        boolean estadoAnterior = setor.isAtivo();
        setor.definirAtivo(ativo);
        auditoria.registrarNaTransacaoAtual(
                autor,
                ativo ? "REATIVAR_SETOR" : "DESATIVAR_SETOR",
                "SETOR",
                id,
                true,
                "ativo: %s -> %s".formatted(estadoAnterior, ativo),
                ip);
        return resposta(setor);
    }

    private void validarUnicidade(IdentidadeSetor identidade, Long idAtual) {
        boolean siglaDuplicada = idAtual == null
                ? setores.existsBySiglaNormalizada(identidade.siglaNormalizada())
                : setores.existsBySiglaNormalizadaAndIdNot(identidade.siglaNormalizada(), idAtual);
        if (siglaDuplicada) {
            throw new DomainException("Já existe setor com esta sigla.");
        }
        boolean nomeDuplicado = idAtual == null
                ? setores.existsByNomeNormalizado(identidade.nomeNormalizado())
                : setores.existsByNomeNormalizadoAndIdNot(identidade.nomeNormalizado(), idAtual);
        if (nomeDuplicado) {
            throw new DomainException("Já existe setor com este nome.");
        }
    }

    private void salvarComIdentidadeUnica(Setor setor) {
        try {
            setores.saveAndFlush(setor);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException("Já existe setor com esta sigla ou nome.");
        }
    }

    private Setor buscar(Long id) {
        return setores.findById(id)
                .orElseThrow(() -> new NotFoundException("Setor não encontrado."));
    }

    private SetorResponse resposta(Setor setor) {
        return new SetorResponse(
                setor.getId(), setor.getSigla(), setor.getNome(), setor.isAtivo());
    }
}
