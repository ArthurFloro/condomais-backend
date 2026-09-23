package br.com.condomais.condominio.controller;

import br.com.condomais.condominio.dto.*;
import br.com.condomais.condominio.model.*;
import br.com.condomais.condominio.service.CondominioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "Cadastrar novo condomínio", description = "Permite que a Administradora XYZ (Admin Master) cadastre um novo condomínio na plataforma (RF-CON-001, RN-CON-002)[cite: 4].")
    public ResponseEntity<Condominio> cadastrarCondominio(@RequestBody CondominioDTO dados) {
        Condominio condominio = condominioService.cadastrarCondominio(dados);
        return ResponseEntity.ok(condominio);
    }

    @PostMapping("/torres")
    @Operation(summary = "Cadastrar torre", description = "Permite que o Admin cadastre uma nova torre pertencente ao condomínio (RF-CON-004)[cite: 4].")
    public ResponseEntity<Torre> cadastrarTorre(@RequestBody TorreDTO dados) {
        Torre torre = condominioService.cadastrarTorre(dados);
        return ResponseEntity.ok(torre);
    }

    @PostMapping("/apartamentos")
    @Operation(summary = "Cadastrar apartamento / unidade", description = "Permite que o Admin cadastre apartamentos vinculados à torre ou condomínio horizontal (RF-CON-005)[cite: 4].")
    public ResponseEntity<Apartamento> cadastrarApartamento(@RequestBody ApartamentoDTO dados) {
        Apartamento apartamento = condominioService.cadastrarApartamento(dados);
        return ResponseEntity.ok(apartamento);
    }

    @PostMapping("/areas-comuns")
    @Operation(summary = "Cadastrar área comum", description = "Permite que o Admin cadastre áreas comuns e configure se são reserváveis ou exigem aprovação (RF-CON-008, RF-CON-009)[cite: 4].")
    public ResponseEntity<AreaComum> cadastrarAreaComum(@RequestBody AreaComumDTO dados) {
        AreaComum area = condominioService.cadastrarAreaComum(dados);
        return ResponseEntity.ok(area);
    }

    // ---------------------------------------------------------------------
    // Condomínio
    // ---------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Listar condomínios", description = "Retorna todos os condomínios cadastrados na plataforma.")
    public ResponseEntity<List<Condominio>> listarCondominios() {
        return ResponseEntity.ok(condominioService.listarCondominios());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar condomínio por ID", description = "Retorna os dados de um condomínio específico.")
    public ResponseEntity<Condominio> buscarCondominio(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarCondominio(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar condomínio", description = "Atualiza nome, CNPJ e status do condomínio. O CNPJ não pode pertencer a outro condomínio.")
    public ResponseEntity<Condominio> atualizarCondominio(@PathVariable UUID id, @RequestBody CondominioDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarCondominio(id, dados));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir condomínio", description = "Remove o condomínio. Bloqueado se houver torres, apartamentos, áreas comuns ou outros registros vinculados.")
    public ResponseEntity<Void> excluirCondominio(@PathVariable UUID id) {
        condominioService.excluirCondominio(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Torre
    // ---------------------------------------------------------------------

    @GetMapping("/torres")
    @Operation(summary = "Listar torres", description = "Lista todas as torres ou, informando condominioId, apenas as torres do condomínio.")
    public ResponseEntity<List<TorreResponseDTO>> listarTorres(@RequestParam(required = false) UUID condominioId) {
        return ResponseEntity.ok(condominioService.listarTorres(condominioId));
    }

    @GetMapping("/torres/{id}")
    @Operation(summary = "Buscar torre por ID", description = "Retorna os dados de uma torre específica.")
    public ResponseEntity<TorreResponseDTO> buscarTorre(@PathVariable UUID id) {
        return ResponseEntity.ok(condominioService.buscarTorre(id));
    }

    @PutMapping("/torres/{id}")
    @Operation(summary = "Editar torre", description = "Atualiza o nome da torre. O condomínio da torre não pode ser alterado (condominioId do corpo é ignorado).")
    public ResponseEntity<TorreResponseDTO> atualizarTorre(@PathVariable UUID id, @RequestBody TorreDTO dados) {
        return ResponseEntity.ok(condominioService.atualizarTorre(id, dados));
    }

    @DeleteMapping("/torres/{id}")
    @Operation(summary = "Excluir torre", description = "Remove a torre. Bloqueado se houver apartamentos ou outros registros vinculados.")
    public ResponseEntity<Void> excluirTorre(@PathVariable UUID id) {
        condominioService.excluirTorre(id);
        return ResponseEntity.noContent().build();
    }
}