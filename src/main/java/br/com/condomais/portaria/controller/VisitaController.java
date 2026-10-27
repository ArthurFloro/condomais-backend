package br.com.condomais.portaria.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.portaria.dto.AutorizacaoVisitaDTO;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.dto.VisitaResponseDTO;
import br.com.condomais.portaria.service.VisitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/portaria/visitas")
@Tag(name = "Módulo 3 - Portaria e Operações", description = "Controle operacional de visitantes, registro de entradas, saídas e rastreabilidade por funcionário.")
public class VisitaController {

    @Autowired
    private VisitaService visitaService;

    private UsuarioAutenticado getUsuarioLogado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return (UsuarioAutenticado) authentication.getPrincipal();
    }

    @GetMapping
    @Operation(
            summary = "Listar visitas",
            description = "Cadastradas mais recentemente primeiro. Portaria e administração veem as do condomínio, com filtros opcionais por status (PENDENTE, AUTORIZADO, RECUSADO, ENTROU, SAIU ou EXPIRADO), apartamentoId e data (visitas cujo período cobre o dia); o morador vê só as da própria unidade (apartamentoId é ignorado). EXPIRADO é calculado: pendente ou autorizada com o período vencido."
    )
    public ResponseEntity<List<VisitaResponseDTO>> listar(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) UUID apartamentoId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(visitaService.listar(status, apartamentoId, data, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar visita", description = "O morador só consulta visitas da própria unidade; as demais respondem 404.")
    public ResponseEntity<VisitaResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(visitaService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping
    @Operation(
            summary = "Cadastrar visitante com período de entrada",
            description = "O morador autoriza para a própria unidade (apartamentoId, moradorId e autorizado são ignorados) e a visita já nasce AUTORIZADO. A portaria ou a administração informa a unidade e, opcionalmente, o morador responsável: com autorizado=true nasce AUTORIZADO, senão PENDENTE. O visitante é reaproveitado pelo CPF dentro do condomínio."
    )
    public ResponseEntity<VisitaResponseDTO> cadastrar(@RequestBody AutorizacaoVisitaDTO dados) {
        return ResponseEntity.ok(visitaService.cadastrar(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}/autorizar")
    @Operation(summary = "Autorizar visita pendente", description = "Portaria, administração ou morador da unidade. Uma visita que não está pendente, ou com o período vencido, responde 409.")
    public ResponseEntity<VisitaResponseDTO> autorizar(@PathVariable UUID id) {
        return ResponseEntity.ok(visitaService.autorizar(id, getUsuarioLogado()));
    }

    @PutMapping("/{id}/recusar")
    @Operation(summary = "Recusar visita", description = "Portaria, administração ou morador da unidade. Vale para visitas pendentes ou autorizadas que ainda não entraram (revoga a autorização).")
    public ResponseEntity<VisitaResponseDTO> recusar(@PathVariable UUID id) {
        return ResponseEntity.ok(visitaService.recusar(id, getUsuarioLogado()));
    }

    @PutMapping("/{id}/entrada")
    @Operation(
            summary = "Registrar entrada de visita autorizada (portaria ou administração)",
            description = "Exige status AUTORIZADO e o horário atual dentro do período; registra data, hora e o porteiro responsável (RF-POR-006, RF-POR-014). Fora disso, 409."
    )
    public ResponseEntity<VisitaResponseDTO> registrarEntrada(@PathVariable UUID id) {
        return ResponseEntity.ok(visitaService.registrarEntrada(id, getUsuarioLogado()));
    }

    @PostMapping("/entrada")
    @Operation(
            summary = "Registrar entrada imediata (portaria ou administração)",
            description = "Para o visitante que chega sem cadastro prévio: cadastra (ou reaproveita pelo CPF) e já registra a entrada, vinculando ao apartamento e ao porteiro responsável (RF-POR-006, RF-POR-014)."
    )
    public ResponseEntity<VisitaResponseDTO> registrarEntradaImediata(@RequestBody RegistroEntradaDTO dados) {
        return ResponseEntity.ok(visitaService.registrarEntradaImediata(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}/saida")
    @Operation(
            summary = "Registrar saída de visitante (portaria ou administração)",
            description = "Atualiza o status para SAIU, registrando a data/hora e o porteiro responsável pela saída (RF-POR-007, RF-POR-014). Só para quem entrou e ainda não saiu; senão, 409."
    )
    public ResponseEntity<VisitaResponseDTO> registrarSaida(@PathVariable UUID id) {
        return ResponseEntity.ok(visitaService.registrarSaida(id, getUsuarioLogado()));
    }
}
