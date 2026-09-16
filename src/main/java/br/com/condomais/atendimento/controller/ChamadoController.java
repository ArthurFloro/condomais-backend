package br.com.condomais.atendimento.controller;

import br.com.condomais.atendimento.dto.AberturaChamadoDTO;
import br.com.condomais.atendimento.model.Chamado;
import br.com.condomais.atendimento.service.ChamadoService;
import br.com.condomais.core.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/atendimento/chamados")
public class ChamadoController {

    @Autowired
    private ChamadoService chamadoService;

    @PostMapping
    public ResponseEntity<Chamado> abrirChamado(@RequestBody AberturaChamadoDTO dados) {
        var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Chamado chamado = chamadoService.abrirChamado(dados, auth.getId(), auth.getCondominioId());
        return ResponseEntity.ok(chamado);
    }
}