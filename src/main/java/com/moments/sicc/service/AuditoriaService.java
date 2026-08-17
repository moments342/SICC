package com.moments.sicc.service;

import com.moments.sicc.api.ApiDtos.AtorAuditoriaResponse;
import com.moments.sicc.api.ApiDtos.ObjetoAuditoriaResponse;
import com.moments.sicc.api.ApiDtos.RegistroAuditoriaResponse;
import com.moments.sicc.domain.Enums.ResultadoAuditoria;
import com.moments.sicc.domain.RegistroAuditoria;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.RegistroAuditoriaRepository;
import com.moments.sicc.shared.exception.DomainException;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuditoriaService {
    private final RegistroAuditoriaRepository registros;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        salvar(usuario, acao, entidade, entidadeId, sucesso, detalhes, ip);
    }

    @Transactional
    public void registrarNaTransacaoAtual(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        salvar(usuario, acao, entidade, entidadeId, sucesso, detalhes, ip);
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoriaResponse> consultar(
            String acao,
            ResultadoAuditoria resultado,
            String usuario,
            LocalDate dataInicial,
            LocalDate dataFinal,
            int pagina,
            int tamanho) {
        validarConsulta(dataInicial, dataFinal, pagina, tamanho);
        Specification<RegistroAuditoria> filtros = (root, query, criteria) -> criteria.conjunction();
        if (StringUtils.hasText(acao)) {
            filtros = filtros.and((root, query, criteria) ->
                    criteria.equal(root.get("acao"), normalizarAcao(acao)));
        }
        if (resultado != null) {
            boolean sucesso = resultado == ResultadoAuditoria.SUCESSO;
            filtros = filtros.and((root, query, criteria) ->
                    criteria.equal(root.get("sucesso"), sucesso));
        }
        if (StringUtils.hasText(usuario)) {
            String termo = "%" + usuario.trim().toLowerCase(Locale.ROOT) + "%";
            filtros = filtros.and((root, query, criteria) -> {
                var ator = root.join("usuario", JoinType.LEFT);
                return criteria.or(
                        criteria.like(criteria.lower(ator.get("login")), termo),
                        criteria.like(criteria.lower(ator.get("nome")), termo));
            });
        }
        if (dataInicial != null) {
            filtros = filtros.and((root, query, criteria) ->
                    criteria.greaterThanOrEqualTo(root.get("criadoEm"), dataInicial.atStartOfDay()));
        }
        if (dataFinal != null) {
            filtros = filtros.and((root, query, criteria) ->
                    criteria.lessThan(root.get("criadoEm"), dataFinal.plusDays(1).atStartOfDay()));
        }
        var ordenacao = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));
        return registros.findAll(filtros, PageRequest.of(pagina, tamanho, ordenacao))
                .map(this::resposta);
    }

    private void salvar(UsuarioInterno usuario, String acao, String entidade, Long entidadeId,
            boolean sucesso, String detalhes, String ip) {
        registros.save(new RegistroAuditoria(
                usuario, normalizarAcao(acao), entidade, entidadeId, sucesso, detalhes, ip));
    }

    private String normalizarAcao(String acao) {
        return acao.trim().toUpperCase(Locale.ROOT);
    }

    private RegistroAuditoriaResponse resposta(RegistroAuditoria registro) {
        UsuarioInterno usuario = registro.getUsuario();
        AtorAuditoriaResponse ator = usuario == null
                ? null
                : new AtorAuditoriaResponse(usuario.getId(), usuario.getLogin(), usuario.getNome());
        return new RegistroAuditoriaResponse(
                registro.getId(),
                registro.getAcao(),
                registro.isSucesso() ? ResultadoAuditoria.SUCESSO : ResultadoAuditoria.FALHA,
                ator,
                new ObjetoAuditoriaResponse(registro.getEntidade(), registro.getEntidadeId()),
                registro.getDetalhes(),
                registro.getIpOrigem(),
                registro.getCriadoEm());
    }

    private void validarConsulta(LocalDate dataInicial, LocalDate dataFinal, int pagina, int tamanho) {
        if (dataInicial != null && dataFinal != null && dataInicial.isAfter(dataFinal)) {
            throw new DomainException("A data inicial não pode ser posterior à data final.");
        }
        if (pagina < 0) {
            throw new DomainException("A página deve ser maior ou igual a zero.");
        }
        if (tamanho < 1 || tamanho > 100) {
            throw new DomainException("O tamanho da página deve estar entre 1 e 100.");
        }
    }
}
