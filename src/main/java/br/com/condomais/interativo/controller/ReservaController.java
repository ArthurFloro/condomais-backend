package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.model.Reserva;
import br.com.condomais.interativo.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interativo/reservas")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    @PostMapping
    public ResponseEntity<Reserva> solicitarReserva(@RequestBody SolicitacaoReservaDTO dados) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var usuarioAutenticado = (UsuarioAutenticado) authentication.getPrincipal();

        Reserva reserva = reservaService.solicitarReserva(dados, usuarioAutenticado.getId(), usuarioAutenticado.getCondominioId());

        return ResponseEntity.ok(reserva);
    }
}