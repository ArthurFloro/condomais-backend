package br.com.condomais.interativo.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.interativo.dto.HorarioOcupadoDTO;
import br.com.condomais.interativo.dto.ReservaResponseDTO;
import br.com.condomais.interativo.dto.SolicitacaoReservaDTO;
import br.com.condomais.interativo.service.ReservaService;
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
@RequestMapping("/interativo/reservas")
@Tag(name = "Módulo 4 - Interativo e Comunicação", description = "Gerenciamento de reservas de áreas comuns, calendário, bloqueio de conflitos e avisos.")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    private UsuarioAutenticado getUsuarioLogado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    @Operation(
            summary = "Listar reservas",
            description = "Data mais recente primeiro. Administração e portaria veem as do condomínio; o morador vê as da própria unidade. Filtros opcionais: areaId, data (AAAA-MM-DD) e status (CONFIRMADA, AGUARDANDO_APROVACAO, CANCELADA, RECUSADA)."
    )
    public ResponseEntity<List<ReservaResponseDTO>> listar(@RequestParam(required = false) UUID areaId,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                                           @RequestParam(required = false) String status) {
        return ResponseEntity.ok(reservaService.listar(areaId, data, status, getUsuarioLogado()));
    }

    @GetMapping("/ocupacao")
    @Operation(summary = "Horários ocupados de uma área num dia", description = "Agenda para escolher um horário livre: só início, fim e status das reservas que ocupam, sem dizer de quem são.")
    public ResponseEntity<List<HorarioOcupadoDTO>> ocupacao(@RequestParam UUID areaId,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(reservaService.ocupacao(areaId, data, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar reserva", description = "O morador só consulta reservas da própria unidade; as demais respondem 404.")
    public ResponseEntity<ReservaResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(reservaService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping
    @Operation(
            summary = "Solicitar reserva de área comum (morador)",
            description = "Verifica conflito de horário. Em área que exige aprovação, o proprietário confirma direto e o inquilino fica AGUARDANDO APROVACAO (RF-RES-001, RF-RES-004, V1.1)."
    )
    public ResponseEntity<ReservaResponseDTO> solicitar(@RequestBody SolicitacaoReservaDTO dados) {
        return ResponseEntity.ok(reservaService.solicitar(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar reserva", description = "Quem fez a reserva, ou a administração, enquanto ela não começou. Libera o horário.")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable UUID id) {
        return ResponseEntity.ok(reservaService.cancelar(id, getUsuarioLogado()));
    }

    @PutMapping("/{id}/aprovar")
    @Operation(summary = "Aprovar reserva", description = "Administração ou o proprietário da unidade de quem pediu. Só reservas AGUARDANDO APROVACAO.")
    public ResponseEntity<ReservaResponseDTO> aprovar(@PathVariable UUID id) {
        return ResponseEntity.ok(reservaService.aprovar(id, getUsuarioLogado()));
    }

    @PutMapping("/{id}/recusar")
    @Operation(summary = "Recusar reserva", description = "Administração ou o proprietário da unidade de quem pediu. Libera o horário.")
    public ResponseEntity<ReservaResponseDTO> recusar(@PathVariable UUID id) {
        return ResponseEntity.ok(reservaService.recusar(id, getUsuarioLogado()));
    }
}
