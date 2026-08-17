package com.moments.sicc.service;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Documento;
import com.moments.sicc.domain.Enums.CategoriaDocumento;
import com.moments.sicc.domain.VersaoDocumento;
import com.moments.sicc.domain.UsuarioInterno;
import com.moments.sicc.repository.DocumentoRepository;
import com.moments.sicc.repository.AlteracaoContratualRepository;
import com.moments.sicc.repository.InstrumentoContratualRepository;
import com.moments.sicc.repository.ProcessoAdministrativoRepository;
import com.moments.sicc.repository.VersaoDocumentoRepository;
import com.moments.sicc.shared.exception.DomainException;
import com.moments.sicc.shared.exception.NotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    private final StorageService storage;
    private final AuditoriaService auditoria;

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
        adicionarVersao(documento.getId(), arquivo, autor, ip);
        auditoria.registrar(autor, "CRIAR_DOCUMENTO", "DOCUMENTO", documento.getId(), true, null, ip);
        return response(documento);
    }

    @Transactional
    public DocumentoResponse adicionarVersao(Long documentoId, MultipartFile arquivo,
            UsuarioInterno autor, String ip) {
        Documento documento = documentoAtivo(documentoId);
        byte[] content = bytes(arquivo);
        String mime = detectarTipo(content);
        validarTipo(documento.getCategoria(), mime);
        int numero = versoes.countByDocumentoId(documentoId) + 1;
        VersaoDocumento versao = new VersaoDocumento();
        versao.setDocumento(documento);
        versao.setVersao(numero);
        versao.setNomeArquivo(nomeSeguro(arquivo.getOriginalFilename(), mime));
        versao.setTipoMime(mime);
        versao.setTamanho(content.length);
        versao.setChecksumSha256(sha256(content));
        versao.setChaveArmazenamento(storage.armazenar(content, "documentos/" + documentoId));
        versao.setCriadoPor(autor);
        versoes.save(versao);
        auditoria.registrar(autor, "CRIAR_VERSAO_DOCUMENTO", "DOCUMENTO", documentoId, true,
                "Versão " + numero, ip);
        return response(documento);
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(com.moments.sicc.domain.Enums.ProprietarioDocumento tipo, Long id) {
        return documentos.findByProprietarioTipoAndProprietarioIdAndAtivoTrue(tipo, id).stream()
                .map(this::response).toList();
    }

    @Transactional
    public DocumentoResponse desativar(Long id, UsuarioInterno autor, String ip) {
        Documento documento = documentoAtivo(id);
        documento.setAtivo(false);
        auditoria.registrar(autor, "DESATIVAR_DOCUMENTO", "DOCUMENTO", id, true, null, ip);
        return response(documento);
    }

    @Transactional
    public Download download(Long documentoId, int numero, UsuarioInterno autor, String ip) {
        VersaoDocumento versao = versoes.findByDocumentoIdAndVersao(documentoId, numero)
                .orElseThrow(() -> new NotFoundException("Versão de documento não encontrada."));
        auditoria.registrar(autor, "DOWNLOAD_DOCUMENTO", "DOCUMENTO", documentoId, true,
                "Versão " + numero, ip);
        return new Download(storage.carregar(versao.getChaveArmazenamento()),
                versao.getNomeArquivo(), versao.getTipoMime());
    }

    private DocumentoResponse response(Documento d) {
        List<VersaoDocumentoResponse> items = versoes.findByDocumentoIdOrderByVersaoDesc(d.getId()).stream()
                .map(v -> new VersaoDocumentoResponse(v.getVersao(), v.getNomeArquivo(), v.getTipoMime(),
                        v.getTamanho(), v.getChecksumSha256(), v.getCriadoEm()))
                .toList();
        return new DocumentoResponse(d.getId(), d.getProprietarioTipo(), d.getProprietarioId(),
                d.getCategoria(), d.getTitulo(), d.isAtivo(), items);
    }

    private void validarProprietario(CriarDocumentoRequest request) {
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
            return file.getBytes();
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("Não foi possível ler o arquivo.");
        }
    }

    private String detectarTipo(byte[] content) {
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (content.length >= 4 && content[0] == 'P' && content[1] == 'K') {
            boolean word = false;
            boolean xl = false;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    word |= entry.getName().startsWith("word/");
                    xl |= entry.getName().startsWith("xl/");
                }
            } catch (Exception e) {
                throw new DomainException("Arquivo compactado inválido.");
            }
            if (word) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (xl) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.contains("\u0000") && (text.contains(",") || text.contains(";") || text.contains("\n"))) {
            return "text/csv";
        }
        throw new DomainException("Formato real não permitido. Use PDF, DOCX, XLSX ou CSV.");
    }

    private void validarTipo(CategoriaDocumento categoria, String mime) {
        if (categoria == CategoriaDocumento.ASSINADO && !"application/pdf".equals(mime)) {
            throw new DomainException("Documento Assinado deve ser PDF.");
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (content[i] != prefix[i]) return false;
        return true;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String nomeSeguro(String original, String mime) {
        String fallback = "arquivo." + switch (mime) {
            case "application/pdf" -> "pdf";
            case "text/csv" -> "csv";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            default -> "xlsx";
        };
        if (original == null || original.isBlank()) return fallback;
        return original.replace("\\", "_").replace("/", "_").trim();
    }

    private Documento documentoAtivo(Long id) {
        Documento documento = documentos.findById(id)
                .orElseThrow(() -> new NotFoundException("Documento não encontrado."));
        if (!documento.isAtivo()) throw new DomainException("O documento está inativo.");
        return documento;
    }

    public record Download(Resource resource, String filename, String mimeType) {}
}
