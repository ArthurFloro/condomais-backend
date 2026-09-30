package br.com.condomais.auth.controller;

import br.com.condomais.auth.dto.UsuarioDTO;
import br.com.condomais.auth.dto.UsuarioResponseDTO;
import br.com.condomais.auth.service.UsuarioService;
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
@RequestMapping("/usuarios")
@Tag(name = "Módulo 1 - Usuários, Autenticação e Permissões", description = "Gerenciamento de identificação, cadastro, autenticação (CPF e senha) e autorização de acesso.")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuários", description = "Lista os usuários do condomínio do administrador autenticado, em ordem alfabética.")
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar(auth()));
    }

    @GetMapping("/me")
    @Operation(summary = "Meus dados", description = "Dados do usuário autenticado (qualquer perfil): nome, perfil, unidade, vínculo e condomínio.")
    public ResponseEntity<UsuarioResponseDTO> meusDados() {
        return ResponseEntity.ok(usuarioService.meusDados(auth()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar usuário (somente ADMIN)")
    public ResponseEntity<UsuarioResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.buscar(id, auth()));
    }

    @PostMapping
    @Operation(summary = "Cadastrar usuário", description = "A Administração pré-cadastra o usuário sem senha (RN-AUT-001); ele cria a senha em /auth/primeiro-acesso. Morador exige unidade e vínculo.")
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@RequestBody UsuarioDTO dados) {
        return ResponseEntity.ok(usuarioService.cadastrar(dados, auth()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar usuário", description = "Atualiza dados, perfil e unidade. O condomínio e a senha não mudam por aqui.")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable UUID id, @RequestBody UsuarioDTO dados) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dados, auth()));
    }

    @PutMapping("/{id}/desativar")
    @Operation(summary = "Desativar usuário", description = "Usuário inativo não consegue logar e perde o acesso imediatamente. O histórico é preservado.")
    public ResponseEntity<UsuarioResponseDTO> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.alterarStatus(id, false, auth()));
    }

    @PutMapping("/{id}/ativar")
    @Operation(summary = "Reativar usuário")
    public ResponseEntity<UsuarioResponseDTO> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.alterarStatus(id, true, auth()));
    }

    private UsuarioAutenticado auth() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
