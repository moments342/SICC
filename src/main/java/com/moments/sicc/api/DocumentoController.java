package com.moments.sicc.api;

import static com.moments.sicc.api.ApiDtos.*;

import com.moments.sicc.domain.Enums.ProprietarioDocumento;
import com.moments.sicc.consulta.ConsultaProprietariosDocumento;
import com.moments.sicc.service.DocumentoService;
import com.moments.sicc.service.IdentidadeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class DocumentoController {
    private final DocumentoService service;
    private final IdentidadeService identidade;
    private final ConsultaProprietariosDocumento proprietarios;

    @GetMapping("/proprietarios")
    public PaginaResponse<ConsultaProprietariosDocumento.Proprietario> proprietarios(
            @RequestParam ProprietarioDocumento tipo,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean incluirInativos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PaginaResponse.de(proprietarios.consultar(tipo, busca, incluirInativos, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse criar(@Valid @ModelAttribute CriarDocumentoRequest request,
            @RequestParam MultipartFile arquivo, HttpServletRequest http) {
        return service.criar(request, arquivo, identidade.atual(), http.getRemoteAddr());
    }

    @PostMapping(value = "/{id}/versoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse novaVersao(@PathVariable Long id, @RequestParam MultipartFile arquivo,
            HttpServletRequest http) {
        return service.adicionarVersao(id, arquivo, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping
    public List<DocumentoResponse> listar(
            @RequestParam ProprietarioDocumento proprietarioTipo,
            @RequestParam Long proprietarioId,
            @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.listar(proprietarioTipo, proprietarioId, incluirInativos);
    }

    @DeleteMapping("/{id}")
    public DocumentoResponse desativar(@PathVariable Long id, HttpServletRequest http) {
        return service.desativar(id, identidade.atual(), http.getRemoteAddr());
    }

    @GetMapping("/{id}/versoes/{versao}/arquivo")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id, @PathVariable int versao, HttpServletRequest http) {
        DocumentoService.Download download = service.download(
                id, versao, identidade.atual(), http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .body(download.resource());
    }
}
