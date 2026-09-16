package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.model.Visita;
import br.com.condomais.portaria.service.VisitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Módulo 3 - Portaria e Operações", description = "Controle operacional de visitantes, registro de entradas, saídas e rastreabilidade por funcionário.")
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
    @Operation(
            summary = "Registrar entrada de visitante",
            description = "Permite à portaria registrar a entrada de um visitante (cadastrando-o caso não exista previamente) vinculando ao apartamento e registrando o porteiro responsável (RF-POR-006, RF-POR-014)[cite: 4]."
    )
    public ResponseEntity<Visita> registrarEntrada(@RequestBody RegistroEntradaDTO dados) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Visita visita = visitaService.registrarEntrada(dados, porteiroId, condominioId);
        return ResponseEntity.ok(visita);
    }

    @PutMapping("/{id}/saida")
    @Operation(
            summary = "Registrar saída de visitante",
            description = "Atualiza o status da visita para 'SAIU', registrando a data/hora e o porteiro responsável pela saída (RF-POR-007, RF-POR-014)[cite: 4]."
    )
    public ResponseEntity<Visita> registrarSaida(@PathVariable UUID id) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Visita visita = visitaService.registrarSaida(id, porteiroId, condominioId);
        return ResponseEntity.ok(visita);
    }
}