package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.model.Reserva;
import br.com.condomais.interativo.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interativo/reservas")
@Tag(name = "Módulo 4 - Interativo e Comunicação", description = "Gerenciamento de reservas de áreas comuns, calendário, bloqueio de conflitos e avisos.")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    @PostMapping
    @Operation(
            summary = "Solicitar reserva de área comum",
            description = "Permite ao morador solicitar reserva verificando conflitos de horário. Aplica a regra especial do Salão de Festas: proprietário confirma direto, residente precisa de aprovação (RF-RES-001, RF-RES-004, V1.1)[cite: 1, 10]."
    )
    public ResponseEntity<Reserva> solicitarReserva(@RequestBody SolicitacaoReservaDTO dados) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var usuarioAutenticado = (UsuarioAutenticado) authentication.getPrincipal();

        Reserva reserva = reservaService.solicitarReserva(dados, usuarioAutenticado.getId(), usuarioAutenticado.getCondominioId());

        return ResponseEntity.ok(reserva);
    }
}