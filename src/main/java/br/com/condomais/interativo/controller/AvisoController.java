package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.model.Aviso;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interativo/avisos")
public class AvisoController {

    // ... injeções do Service ...

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PORTARIA')") // RN-AVI-001 restringe o Morador
    public ResponseEntity<Aviso> publicarAviso(@RequestBody RegistroAvisoDTO dados) {
        var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Chamada ao Service que salvará o Aviso associando o autor e validando a prioridade (NORMAL/URGENTE)
        Aviso aviso = avisoService.publicarAviso(dados, auth.getId(), auth.getCondominioId());
        return ResponseEntity.ok(aviso);
    }
}