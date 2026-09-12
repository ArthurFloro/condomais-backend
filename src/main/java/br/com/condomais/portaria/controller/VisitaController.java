package br.com.condomais.portaria.controller;

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

    // Auxiliar para extrair informações do token JWT validado no SecurityFilter
    private UUID getUsuarioIdLogado() {
        // Implementação simplificada: em um cenário real, você extrairia a claim 'id' do JWT.
        // Aqui assumimos que o ID foi colocado nos detalhes da Autenticação ou extraído via repositório.
        return UUID.randomUUID(); // TODO: Substituir pela extração real do SecurityContext
    }

    private UUID getCondominioIdLogado() {
        return UUID.randomUUID(); // TODO: Substituir pela extração real da claim 'condominio_id' do SecurityContext
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