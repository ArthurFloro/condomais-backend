package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.model.Visita;
import br.com.condomais.portaria.service.VisitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/portaria/visitas")
public class VisitaController {

    @Autowired
    private VisitaService visitaService;

    private UUID getUsuarioIdLogado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var usuarioAutenticado = (UsuarioAutenticado) authentication.getPrincipal();
        return usuarioAutenticado.getId();
    }

    private UUID getCondominioIdLogado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var usuarioAutenticado = (UsuarioAutenticado) authentication.getPrincipal();
        return usuarioAutenticado.getCondominioId();
    }

    @PostMapping("/entrada")
    public ResponseEntity<Visita> registrarEntrada(@RequestBody RegistroEntradaDTO dados) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Visita visita = visitaService.registrarEntrada(dados, porteiroId, condominioId);
        return ResponseEntity.ok(visita);
    }

    @PutMapping("/{id}/saida")
    public ResponseEntity<Visita> registrarSaida(@PathVariable UUID id) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Visita visita = visitaService.registrarSaida(id, porteiroId, condominioId);
        return ResponseEntity.ok(visita);
    }
}