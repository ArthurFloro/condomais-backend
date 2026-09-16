package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.RegistroAvisoDTO;
import br.com.condomais.interativo.model.Aviso;
import br.com.condomais.interativo.service.AvisoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interativo/avisos")
@Tag(name = "Módulo 4 - Interativo e Comunicação", description = "Gerenciamento de avisos institucionais e comunicados segmentados por torre ou condomínio.")
public class AvisoController {

    AvisoService avisoService = new AvisoService();

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PORTARIA')") // RN-AVI-001 restringe o Morador
    @Operation(
            summary = "Publicar aviso institucional",
            description = "Permite que Admin e Portaria publiquem avisos com prioridade NORMAL ou URGENTE, direcionados para o condomínio inteiro ou torre específica (RF-AVI-001, RF-AVI-002, V1.1)[cite: 1, 10]."
    )
    public ResponseEntity<Aviso> publicarAviso(@RequestBody RegistroAvisoDTO dados) {
        var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Chamada ao Service que salvará o Aviso associando o autor e validando a prioridade (NORMAL/URGENTE)
        Aviso aviso = avisoService.publicarAviso(dados, auth.getId(), auth.getCondominioId());
        return ResponseEntity.ok(aviso);
    }
}