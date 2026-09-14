package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.service.EncomendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/portaria/encomendas")
public class EncomendaController {

    @Autowired
    private EncomendaService encomendaService;

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

    @PostMapping("/recebimento")
    public ResponseEntity<Encomenda> registrarRecebimento(@RequestBody RegistroEncomendaDTO dados) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Encomenda encomenda = encomendaService.registrarRecebimento(dados, porteiroId, condominioId);
        return ResponseEntity.ok(encomenda);
    }

    @PutMapping("/{id}/retirada")
    public ResponseEntity<Encomenda> registrarRetirada(@PathVariable UUID id) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Encomenda encomenda = encomendaService.registrarRetirada(id, porteiroId, condominioId);
        return ResponseEntity.ok(encomenda);
    }
}