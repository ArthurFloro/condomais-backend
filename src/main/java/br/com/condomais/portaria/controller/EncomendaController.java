package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.service.EncomendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/portaria/encomendas")
@Tag(name = "Módulo 3 - Portaria e Operações", description = "Controle operacional de encomendas, recebimento simplificado e retirada.")
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
    @Operation(
            summary = "Registrar recebimento de encomenda",
            description = "Fluxo simplificado da V1.1 onde a portaria informa a torre/bloco e apartamento. Vincula a encomenda à unidade e registra o porteiro recebedor (RF-POR-009, RN-POR-007)[cite: 1, 4]."
    )
    public ResponseEntity<Encomenda> registrarRecebimento(@RequestBody RegistroEncomendaDTO dados) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Encomenda encomenda = encomendaService.registrarRecebimento(dados, porteiroId, condominioId);
        return ResponseEntity.ok(encomenda);
    }

    @PutMapping("/{id}/retirada")
    @Operation(
            summary = "Registrar retirada de encomenda",
            description = "Atualiza o status da encomenda para 'RETIRADA', registrando a data, hora e o porteiro responsável pela entrega ao morador (RF-POR-012, RN-POR-009)[cite: 4]."
    )
    public ResponseEntity<Encomenda> registrarRetirada(@PathVariable UUID id) {
        var porteiroId = getUsuarioIdLogado();
        var condominioId = getCondominioIdLogado();

        Encomenda encomenda = encomendaService.registrarRetirada(id, porteiroId, condominioId);
        return ResponseEntity.ok(encomenda);
    }
}