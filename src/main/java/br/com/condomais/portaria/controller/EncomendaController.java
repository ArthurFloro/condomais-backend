package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.EncomendaResponseDTO;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.service.EncomendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/portaria/encomendas")
@Tag(name = "Módulo 3 - Portaria e Operações", description = "Controle operacional de encomendas, recebimento simplificado e retirada.")
public class EncomendaController {

    @Autowired
    private EncomendaService encomendaService;

    private UsuarioAutenticado getUsuarioLogado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UsuarioAutenticado) authentication.getPrincipal();
    }

    @GetMapping
    @Operation(
            summary = "Listar encomendas",
            description = "Mais recentes primeiro. Portaria e administração veem as do condomínio, com filtros opcionais por status (AGUARDANDO_RETIRADA ou RETIRADA) e apartamentoId; o morador vê só as da própria unidade (apartamentoId é ignorado)."
    )
    public ResponseEntity<List<EncomendaResponseDTO>> listar(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) UUID apartamentoId) {
        return ResponseEntity.ok(encomendaService.listar(status, apartamentoId, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar encomenda", description = "O morador só consulta encomendas da própria unidade; as demais respondem 404.")
    public ResponseEntity<EncomendaResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(encomendaService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping("/recebimento")
    @Operation(
            summary = "Registrar recebimento de encomenda (portaria ou administração)",
            description = "Fluxo simplificado da V1.1 onde a portaria informa o apartamento. Vincula a encomenda à unidade e registra o porteiro recebedor (RF-POR-009, RN-POR-007)."
    )
    public ResponseEntity<EncomendaResponseDTO> registrarRecebimento(@RequestBody RegistroEncomendaDTO dados) {
        return ResponseEntity.ok(encomendaService.registrarRecebimento(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}/retirada")
    @Operation(
            summary = "Registrar retirada de encomenda (portaria ou administração)",
            description = "Atualiza o status para RETIRADA, registrando data, hora e o porteiro responsável pela entrega (RF-POR-012, RN-POR-009). Uma encomenda já retirada responde 409."
    )
    public ResponseEntity<EncomendaResponseDTO> registrarRetirada(@PathVariable UUID id) {
        return ResponseEntity.ok(encomendaService.registrarRetirada(id, getUsuarioLogado()));
    }
}
