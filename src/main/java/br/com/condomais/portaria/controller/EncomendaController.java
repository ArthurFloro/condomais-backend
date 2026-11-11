package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.EncomendaResponseDTO;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.dto.RetiradaEncomendaDTO;
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
            description = "Mais recentes primeiro. Portaria e administração veem as do condomínio, com filtros opcionais por status (AGUARDANDO_RETIRADA ou RETIRADA), apartamentoId, codigo (parte do código, sem diferenciar maiúsculas e espaços) e semDestinatario=true; o morador vê só as da própria unidade (apartamentoId é ignorado; as sem destinatário não aparecem)."
    )
    public ResponseEntity<List<EncomendaResponseDTO>> listar(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) UUID apartamentoId,
                                                             @RequestParam(required = false) String codigo,
                                                             @RequestParam(defaultValue = "false") boolean semDestinatario) {
        return ResponseEntity.ok(encomendaService.listar(status, apartamentoId, codigo, semDestinatario, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar encomenda", description = "O morador só consulta encomendas da própria unidade; as demais respondem 404.")
    public ResponseEntity<EncomendaResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(encomendaService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping("/recebimento")
    @Operation(
            summary = "Registrar recebimento de encomenda (portaria)",
            description = "A portaria informa o apartamento, ou nenhum quando o destinatário não está identificado (CONDO-05.1). Registra o código da etiqueta, a observação e o porteiro recebedor (RF-POR-009, RN-POR-007)."
    )
    public ResponseEntity<EncomendaResponseDTO> registrarRecebimento(@RequestBody RegistroEncomendaDTO dados) {
        return ResponseEntity.ok(encomendaService.registrarRecebimento(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}/retirada")
    @Operation(
            summary = "Registrar retirada de encomenda (portaria)",
            description = "Atualiza o status para RETIRADA, registrando data, hora, o porteiro e o nome de quem retirou (retiranteNome, obrigatório). Sem destinatário identificado, exige também codigoConferencia (igual ao código cadastrado, senão 400) e apartamentoInformadoId (só registrado). Uma encomenda já retirada responde 409."
    )
    public ResponseEntity<EncomendaResponseDTO> registrarRetirada(@PathVariable UUID id, @RequestBody(required = false) RetiradaEncomendaDTO dados) {
        return ResponseEntity.ok(encomendaService.registrarRetirada(id, dados, getUsuarioLogado()));
    }
}
