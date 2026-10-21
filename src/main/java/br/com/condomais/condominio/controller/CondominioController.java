package br.com.condomais.condominio.controller;

import br.com.condomais.condominio.dto.*;
import br.com.condomais.condominio.model.*;
import br.com.condomais.condominio.service.CondominioService;
import br.com.condomais.core.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/condominios")
@Tag(name = "Módulo 2 - Estrutura Condominial", description = "Gerenciamento de condomínios, torres, apartamentos e áreas comuns (Modelo Multi-Condomínio).")
public class CondominioController {

    @Autowired
    private CondominioService condominioService;

    @PostMapping
    @Operation(summary = "Cadastrar novo condomínio (somente ADMIN)", description = "Cadastra um novo condomínio na plataforma (RF-CON-001, RN-CON-002). Enquanto não existe perfil de administradora, exige ADMIN.")
    public ResponseEntity<Condominio> cadastrarCondominio(@RequestBody CondominioDTO dados) {
        Condominio condominio = condominioService.cadastrarCondominio(dados, auth());
        return ResponseEntity.ok(condominio);
    }

    @PostMapping("/torres")
    @Operation(summary = "Cadastrar torre (somente ADMIN)", description = "Cadastra uma torre no condomínio do usuário autenticado; o condominioId do corpo é ignorado (RF-CON-004).")
    public ResponseEntity<Torre> cadastrarTorre(@RequestBody TorreDTO dados) {
        Torre torre = condominioService.cadastrarTorre(dados, auth());
        return ResponseEntity.ok(torre);
    }

    @PostMapping("/apartamentos")
    @Operation(summary = "Cadastrar apartamento / unidade (somente ADMIN)", description = "Cadastra a unidade no condomínio do usuário autenticado, com torre opcional do mesmo condomínio; o condominioId do corpo é ignorado (RF-CON-005).")
    public ResponseEntity<Apartamento> cadastrarApartamento(@RequestBody ApartamentoDTO dados) {
        Apartamento apartamento = condominioService.cadastrarApartamento(dados, auth());
        return ResponseEntity.ok(apartamento);
    }

    @PostMapping("/areas-comuns")
    @Operation(summary = "Cadastrar área comum (somente ADMIN)", description = "Cadastra a área no condomínio do usuário autenticado e configura se é reservável ou exige aprovação; o condominioId do corpo é ignorado (RF-CON-008, RF-CON-009).")
    public ResponseEntity<AreaComum> cadastrarAreaComum(@RequestBody AreaComumDTO dados) {
        AreaComum area = condominioService.cadastrarAreaComum(dados, auth());
        return ResponseEntity.ok(area);
    }

    // ---------------------------------------------------------------------
    // Condomínio
    // ---------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Listar condomínios", description = "Retorna o condomínio do usuário autenticado (lista com um item).")
    public ResponseEntity<List<Condominio>> listarCondominios() {
        return ResponseEntity.ok(condominioService.listarCondominios(auth()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar condomínio por ID", description = "Retorna o condomínio do usuário autenticado; outro id responde 404.")
    public ResponseEntity<Condominio> buscarCondominio(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarCondominio(id, auth()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar condomínio (somente ADMIN)", description = "Atualiza nome, CNPJ e status do condomínio. O CNPJ não pode pertencer a outro condomínio.")
    public ResponseEntity<Condominio> atualizarCondominio(@PathVariable UUID id, @RequestBody CondominioDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarCondominio(id, dados, auth()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir condomínio (somente ADMIN)", description = "Remove o condomínio. Bloqueado se houver torres, apartamentos, áreas comuns ou outros registros vinculados.")
    public ResponseEntity<Void> excluirCondominio(@PathVariable UUID id) {
        condominioService.excluirCondominio(id, auth());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Torre
    // ---------------------------------------------------------------------

    @GetMapping("/torres")
    @Operation(summary = "Listar torres", description = "Lista as torres do condomínio do usuário autenticado.")
    public ResponseEntity<List<TorreResponseDTO>> listarTorres() {
        return ResponseEntity.ok(condominioService.listarTorres(auth()));
    }

    @GetMapping("/torres/{id}")
    @Operation(summary = "Buscar torre por ID", description = "Retorna uma torre do condomínio do usuário autenticado.")
    public ResponseEntity<TorreResponseDTO> buscarTorre(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarTorre(id, auth()));
    }

    @PutMapping("/torres/{id}")
    @Operation(summary = "Editar torre (somente ADMIN)", description = "Atualiza o nome da torre. O condomínio da torre não pode ser alterado (condominioId do corpo é ignorado).")
    public ResponseEntity<TorreResponseDTO> atualizarTorre(@PathVariable UUID id, @RequestBody TorreDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarTorre(id, dados, auth()));
    }

    @DeleteMapping("/torres/{id}")
    @Operation(summary = "Excluir torre (somente ADMIN)", description = "Remove a torre. Bloqueado se houver apartamentos ou outros registros vinculados.")
    public ResponseEntity<Void> excluirTorre(@PathVariable UUID id) {
        condominioService.excluirTorre(id, auth());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Apartamento
    // ---------------------------------------------------------------------

    @GetMapping("/apartamentos")
    @Operation(summary = "Listar apartamentos", description = "Lista os apartamentos do condomínio do usuário autenticado, com filtro opcional por torreId.")
    public ResponseEntity<List<ApartamentoResponseDTO>> listarApartamentos(@RequestParam(required = false) UUID torreId) {
        return ResponseEntity.ok(condominioService.listarApartamentos(torreId, auth()));
    }

    @GetMapping("/apartamentos/{id}")
    @Operation(summary = "Buscar apartamento por ID", description = "Retorna um apartamento do condomínio do usuário autenticado.")
    public ResponseEntity<ApartamentoResponseDTO> buscarApartamento(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarApartamento(id, auth()));
    }

    @PutMapping("/apartamentos/{id}")
    @Operation(summary = "Editar apartamento (somente ADMIN)", description = "Atualiza número, status e torre do apartamento. A torre deve pertencer ao mesmo condomínio; torreId nulo remove o vínculo (condominioId do corpo é ignorado).")
    public ResponseEntity<ApartamentoResponseDTO> atualizarApartamento(@PathVariable UUID id, @RequestBody ApartamentoDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarApartamento(id, dados, auth()));
    }

    @DeleteMapping("/apartamentos/{id}")
    @Operation(summary = "Excluir apartamento (somente ADMIN)", description = "Remove o apartamento. Bloqueado se houver moradores, visitas, encomendas ou chamados vinculados.")
    public ResponseEntity<Void> excluirApartamento(@PathVariable UUID id) {
        condominioService.excluirApartamento(id, auth());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Área comum
    // ---------------------------------------------------------------------

    @GetMapping("/areas-comuns")
    @Operation(summary = "Listar áreas comuns", description = "Lista as áreas comuns do condomínio do usuário autenticado. Use apenasAtivas=true para omitir as desativadas.")
    public ResponseEntity<List<AreaComumResponseDTO>> listarAreasComuns(@RequestParam(defaultValue = "false") boolean apenasAtivas) {
        return ResponseEntity.ok(condominioService.listarAreasComuns(apenasAtivas, auth()));
    }

    @GetMapping("/areas-comuns/{id}")
    @Operation(summary = "Buscar área comum por ID", description = "Retorna uma área comum do condomínio do usuário autenticado.")
    public ResponseEntity<AreaComumResponseDTO> buscarAreaComum(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarAreaComum(id, auth()));
    }

    @PutMapping("/areas-comuns/{id}")
    @Operation(summary = "Editar área comum (somente ADMIN)", description = "Atualiza nome, descrição, regras e configurações de reserva/aprovação (condominioId do corpo é ignorado).")
    public ResponseEntity<AreaComumResponseDTO> atualizarAreaComum(@PathVariable UUID id, @RequestBody AreaComumDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarAreaComum(id, dados, auth()));
    }

    @PutMapping("/areas-comuns/{id}/desativar")
    @Operation(summary = "Desativar área comum (somente ADMIN)", description = "Soft-delete: mantém o histórico de reservas e marca a área como inativa.")
    public ResponseEntity<AreaComumResponseDTO> desativarAreaComum(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.alterarStatusAreaComum(id, false, auth()));
    }

    @PutMapping("/areas-comuns/{id}/ativar")
    @Operation(summary = "Reativar área comum (somente ADMIN)", description = "Marca novamente como ativa uma área comum desativada.")
    public ResponseEntity<AreaComumResponseDTO> ativarAreaComum(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.alterarStatusAreaComum(id, true, auth()));
    }

    private UsuarioAutenticado auth() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
