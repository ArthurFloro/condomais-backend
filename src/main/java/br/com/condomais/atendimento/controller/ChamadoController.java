package br.com.condomais.atendimento.controller;

import br.com.condomais.atendimento.dto.AberturaChamadoDTO;
import br.com.condomais.atendimento.model.Chamado;
import br.com.condomais.atendimento.service.ChamadoService;
import br.com.condomais.core.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/atendimento/chamados")
@Tag(name = "Módulo 5 - Chamados e Ocorrências", description = "Gerenciamento de chamados, ocorrências da portaria e registro de manutenções vinculadas.")
public class ChamadoController {

    @Autowired
    private ChamadoService chamadoService;

    @PostMapping
    @Operation(
            summary = "Abrir um novo chamado ou ocorrência",
            description = "Permite que Moradores e Portaria registrem um chamado (RF-CHA-001). A requisição gera o status inicial 'ABERTO' e salva o registro obrigatório no histórico de chamados."
    )
    public ResponseEntity<Chamado> abrirChamado(@RequestBody AberturaChamadoDTO dados) {
        var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Chamado chamado = chamadoService.abrirChamado(dados, auth.getId(), auth.getCondominioId());
        return ResponseEntity.ok(chamado);
    }
}