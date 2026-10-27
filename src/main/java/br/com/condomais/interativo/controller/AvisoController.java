package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.AvisoResponseDTO;
import br.com.condomais.interativo.dto.RegistroAvisoDTO;
import br.com.condomais.interativo.service.AvisoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/interativo/avisos")
@Tag(name = "Módulo 4 - Interativo e Comunicação", description = "Gerenciamento de avisos institucionais e comunicados segmentados por torre ou condomínio.")
public class AvisoController {

    @Autowired
    private AvisoService avisoService;

    private UsuarioAutenticado getUsuarioLogado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    @Operation(
            summary = "Listar comunicados",
            description = "Mais recentes primeiro. Administração e portaria veem todos, inclusive vencidos (apenasAtivos=true esconde os vencidos); o morador vê só os ativos gerais e os da torre dele."
    )
    public ResponseEntity<List<AvisoResponseDTO>> listar(@RequestParam(defaultValue = "false") boolean apenasAtivos) {
        return ResponseEntity.ok(avisoService.listar(apenasAtivos, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar comunicado", description = "Para o morador, comunicado vencido ou de outra torre responde 404.")
    public ResponseEntity<AvisoResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(avisoService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping
    @Operation(
            summary = "Publicar comunicado (administração ou portaria)",
            description = "Prioridade NORMAL (padrão) ou URGENTE, para o condomínio inteiro ou uma torre específica, com validade opcional (RF-AVI-001, RF-AVI-002, RN-AVI-001)."
    )
    public ResponseEntity<AvisoResponseDTO> publicar(@RequestBody RegistroAvisoDTO dados) {
        return ResponseEntity.ok(avisoService.publicar(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar comunicado (administração ou portaria)", description = "Altera título, texto, prioridade, validade e torre. Autor e data de publicação não mudam.")
    public ResponseEntity<AvisoResponseDTO> atualizar(@PathVariable UUID id, @RequestBody RegistroAvisoDTO dados) {
        return ResponseEntity.ok(avisoService.atualizar(id, dados, getUsuarioLogado()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir comunicado (administração ou portaria)")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        avisoService.excluir(id, getUsuarioLogado());
        return ResponseEntity.noContent().build();
    }
}
