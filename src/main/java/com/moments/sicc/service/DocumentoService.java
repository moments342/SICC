package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.domain.VersaoDocumento;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.shared.ChecksumArquivo;
import com.moments.sicc.shared.exception.ArmazenamentoException;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentoService {
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final DocumentoRepository documentos;
    private final VersaoDocumentoRepository versoes;
    private final ProcessoAdministrativoRepository processos;
    private final InstrumentoContratualRepository instrumentos;
    private final AlteracaoContratualRepository alteracoes;
    private final ArmazenamentoArquivo storage;
    private final AuditoriaService auditoria;
    private final ValidadorConteudoDocumento validadorConteudo;

    @Transactional
    public DocumentoResponse criar(CriarDocumentoRequest request, MultipartFile arquivo,
            UsuarioInterno autor, String ip) {
        validarProprietario(request);
        Documento documento = new Documento();
        documento.setProprietarioTipo(request.proprietarioTipo());
        documento.setProprietarioId(request.proprietarioId());
        documento.setCategoria(request.categoria());
        documento.setTitulo(request.titulo().trim());
        documento.setCriadoPor(autor);
        documentos.save(documento);
        VersaoDocumento versao;
        try {
            versao = salvarVersao(documento, arquivo, autor);
        } catch (ArmazenamentoException e) {
            auditarFalhaArmazenamento(
                    autor, "CRIAR_DOCUMENTO", documento.getId(), ip);
            throw e;
        }
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_DOCUMENTO", "DOCUMENTO", documento.getId(), true,
                "Versão " + versao.getVersao(), ip);
        return response(documento);
    }

    @Transactional
    public DocumentoResponse adicionarVersao(Long documentoId, MultipartFile arquivo,
            UsuarioInterno autor, String ip) {
        Documento documento = documentoAtivoEditavel(documentoId);
        VersaoDocumento versao;
        try {
            versao = salvarVersao(documento, arquivo, autor);
        } catch (ArmazenamentoException e) {
            auditarFalhaArmazenamento(
                    autor, "CRIAR_VERSAO_DOCUMENTO", documentoId, ip);
            throw e;
        }
        auditoria.registrarNaTransacaoAtual(
                autor, "CRIAR_VERSAO_DOCUMENTO", "DOCUMENTO", documentoId, true,
                "Versão " + versao.getVersao(), ip);
        return response(documento);
    }

    private VersaoDocumento salvarVersao(
            Documento documento, MultipartFile arquivo, UsuarioInterno autor) {
        byte[] content = bytes(arquivo);
        FormatoDocumento formato = validadorConteudo.detectar(content);
        if (!formato.permitidoPara(documento.getCategoria())) {
            throw new DomainException("Documento Assinado deve ser PDF.");
        }
        int numero = versoes.maiorNumeroPorDocumentoId(documento.getId()) + 1;
        VersaoDocumento versao = new VersaoDocumento();
        versao.setDocumento(documento);
        versao.setVersao(numero);
        versao.setNomeArquivo(nomeSeguro(arquivo.getOriginalFilename(), formato));
        versao.setTipoMime(formato.mime());
        versao.setTamanho(content.length);
        versao.setChecksumSha256(ChecksumArquivo.sha256(content));
        versao.setChaveArmazenamento(
                storage.armazenar(content, "documentos/" + documento.getId()));
        versao.setCriadoPor(autor);
        return versoes.save(versao);
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(com.moments.sicc.domain.Enums.ProprietarioDocumento tipo, Long id) {
        return documentos.findByProprietarioTipoAndProprietarioIdAndAtivoTrue(tipo, id).stream()
                .map(this::response).toList();
    }

    @Transactional
    public DocumentoResponse desativar(Long id, UsuarioInterno autor, String ip) {
        Documento documento = documentoAtivoEditavel(id);
        documento.setAtivo(false);
        auditoria.registrarNaTransacaoAtual(
                autor, "DESATIVAR_DOCUMENTO", "DOCUMENTO", id, true, null, ip);
        return response(documento);
    }

    @Transactional
    public Download download(Long documentoId, int numero, UsuarioInterno autor, String ip) {
        VersaoDocumento versao = versoes.findByDocumentoIdAndVersao(documentoId, numero)
                .orElseThrow(() -> new NotFoundException("Versão de documento não encontrada."));
        Resource resource;
        try {
            resource = storage.carregar(versao.getChaveArmazenamento());
        } catch (ArmazenamentoException e) {
            auditarFalhaArmazenamento(
                    autor, "DOWNLOAD_DOCUMENTO", documentoId, ip);
            throw e;
        }
        auditoria.registrarNaTransacaoAtual(
                autor, "DOWNLOAD_DOCUMENTO", "DOCUMENTO", documentoId, true,
                "Versão " + numero, ip);
        return new Download(resource, versao.getNomeArquivo(), versao.getTipoMime());
    }

    private void auditarFalhaArmazenamento(
            UsuarioInterno autor, String acao, Long documentoId, String ip) {
        auditoria.registrar(
                autor, acao, "DOCUMENTO", documentoId, false, "Falha de armazenamento.", ip);
    }

    private DocumentoResponse response(Documento d) {
        List<VersaoDocumentoResponse> items = versoes.findByDocumentoIdOrderByVersaoDesc(d.getId()).stream()
                .map(v -> new VersaoDocumentoResponse(v.getVersao(), v.getNomeArquivo(), v.getTipoMime(),
                        v.getTamanho(), v.getChecksumSha256(), autor(v.getCriadoPor()), v.getCriadoEm()))
                .toList();
        return new DocumentoResponse(d.getId(), d.getProprietarioTipo(), d.getProprietarioId(),
                d.getCategoria(), d.getTitulo(), d.isAtivo(),
                autor(d.getCriadoPor()), d.getCriadoEm(), items);
    }

    private AutorDocumentoResponse autor(UsuarioInterno usuario) {
        return new AutorDocumentoResponse(usuario.getId(), usuario.getNome());
    }

    private void validarProprietario(CriarDocumentoRequest request) {
        if (request.categoria() == CategoriaDocumento.ADMINISTRATIVO
                && request.proprietarioTipo() != ProprietarioDocumento.PROCESSO) {
            throw new DomainException(
                    "Documento administrativo deve pertencer a um processo.");
        }
        boolean existe = switch (request.proprietarioTipo()) {
            case PROCESSO -> processos.existsById(request.proprietarioId());
            case INSTRUMENTO -> instrumentos.existsById(request.proprietarioId());
            case TERMO_ADITIVO -> alteracoes.findById(request.proprietarioId())
                    .map(a -> a.getTipo() == com.moments.sicc.domain.Enums.TipoAlteracao.TERMO_ADITIVO).orElse(false);
            case APOSTILAMENTO -> alteracoes.findById(request.proprietarioId())
                    .map(a -> a.getTipo() == com.moments.sicc.domain.Enums.TipoAlteracao.APOSTILAMENTO).orElse(false);
        };
        if (!existe) throw new NotFoundException("Proprietário do documento não encontrado.");
    }

    private byte[] bytes(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) throw new DomainException("O arquivo é obrigatório.");
            if (file.getSize() > MAX_BYTES) throw new DomainException("Cada versão deve ter no máximo 20 MB.");
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    (int) Math.min(file.getSize(), MAX_BYTES));
            byte[] buffer = new byte[8192];
            int total = 0;
            try (var input = file.getInputStream()) {
                int lidos;
                while ((lidos = input.read(buffer)) != -1) {
                    total += lidos;
                    if (total > MAX_BYTES) {
                        throw new DomainException("Cada versão deve ter no máximo 20 MB.");
                    }
                    output.write(buffer, 0, lidos);
                }
            }
            return output.toByteArray();
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Não foi possível ler o arquivo.");
        }
    }

    private String nomeSeguro(String original, FormatoDocumento formato) {
        String fallback = "arquivo." + formato.extensao();
        if (original == null || original.isBlank()) return fallback;
        return original.replace("\\", "_").replace("/", "_").trim();
    }

    private Documento documentoAtivoEditavel(Long id) {
        Documento documento = documentos.findByIdComBloqueio(id)
                .orElseThrow(() -> new NotFoundException("Documento não encontrado."));
        if (!documento.isAtivo()) throw new DomainException("O documento está inativo.");
        validarDocumentoEditavel(documento);
        return documento;
    }

    private void validarDocumentoEditavel(Documento documento) {
        if (alteracoes.existsByDocumentoAssinadoId(documento.getId())) {
            throw new DomainException("Documento oficial de alteração efetivada é imutável.");
        }
    }

    public record Download(Resource resource, String filename, String mimeType) {}
}
