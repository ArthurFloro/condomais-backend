package br.com.condomais.atendimento.controller;

import br.com.condomais.atendimento.dto.CategoriaChamadoDTO;
import br.com.condomais.atendimento.dto.CategoriaChamadoResponseDTO;
import br.com.condomais.atendimento.service.CategoriaChamadoService;
import br.com.condomais.core.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/atendimento/categorias")
@Tag(name = "Módulo 5 - Chamados e Ocorrências", description = "Gerenciamento de chamados, ocorrências da portaria e registro de manutenções vinculadas.")
public class CategoriaChamadoController {

    @Autowired
    private CategoriaChamadoService categoriaService;

    private UsuarioAutenticado getUsuarioLogado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    @Operation(summary = "Listar categorias de chamado", description = "Em ordem alfabética. Aberto a todos do condomínio, para escolher ao abrir um chamado. Sempre inclui a categoria Outros (criada aqui, se faltar).")
    public ResponseEntity<List<CategoriaChamadoResponseDTO>> listar() {
        return ResponseEntity.ok(categoriaService.listar(getUsuarioLogado()));
    }

    @PostMapping
    @Operation(summary = "Cadastrar categoria (administração)", description = "Nome único no condomínio, sem diferenciar maiúsculas.")
    public ResponseEntity<CategoriaChamadoResponseDTO> cadastrar(@RequestBody CategoriaChamadoDTO dados) {
        return ResponseEntity.ok(categoriaService.cadastrar(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renomear categoria (administração)")
    public ResponseEntity<CategoriaChamadoResponseDTO> renomear(@PathVariable UUID id, @RequestBody CategoriaChamadoDTO dados) {
        return ResponseEntity.ok(categoriaService.renomear(id, dados, getUsuarioLogado()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir categoria (administração)", description = "Bloqueado (409) se algum chamado usa a categoria.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        categoriaService.excluir(id, getUsuarioLogado());
        return ResponseEntity.noContent().build();
    }
}
