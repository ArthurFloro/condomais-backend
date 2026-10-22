package br.com.condomais.plataforma.controller;

import br.com.condomais.core.security.UsuarioAutenticado;
import br.com.condomais.plataforma.dto.CadastroCondominioDTO;
import br.com.condomais.plataforma.dto.CondominioPlataformaDTO;
import br.com.condomais.plataforma.dto.DadosCondominioDTO;
import br.com.condomais.plataforma.dto.TrocaAdminDTO;
import br.com.condomais.plataforma.service.PlataformaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plataforma/condominios")
@Tag(name = "Plataforma (somente MASTER)", description = "Área dos donos do Condo+: cadastro de condomínios e dos administradores deles. Não acessa os dados internos dos condomínios.")
public class PlataformaController {

    @Autowired
    private PlataformaService plataformaService;

    @GetMapping
    @Operation(summary = "Listar condomínios", description = "Todos os condomínios da plataforma, em ordem alfabética, com seus administradores.")
    public ResponseEntity<List<CondominioPlataformaDTO>> listar() {
        return ResponseEntity.ok(plataformaService.listar(auth()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar condomínio")
    public ResponseEntity<CondominioPlataformaDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(plataformaService.buscar(id, auth()));
    }

    @PostMapping
    @Operation(summary = "Cadastrar condomínio e administrador", description = "Cria o condomínio e o primeiro administrador juntos (RN-CON-002). O administrador nasce sem senha e ativa a conta em /auth/primeiro-acesso.")
    public ResponseEntity<CondominioPlataformaDTO> cadastrar(@RequestBody CadastroCondominioDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plataformaService.cadastrar(dados, auth()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar nome e CNPJ", description = "Só o MASTER altera a identificação do condomínio. O CNPJ não pode pertencer a outro condomínio.")
    public ResponseEntity<CondominioPlataformaDTO> atualizar(@PathVariable UUID id, @RequestBody DadosCondominioDTO dados) {
        return ResponseEntity.ok(plataformaService.atualizar(id, dados, auth()));
    }

    @PutMapping("/{id}/desativar")
    @Operation(summary = "Desativar condomínio", description = "Bloqueia o login de todos os usuários do condomínio, inclusive com token já emitido. Nenhum dado é apagado.")
    public ResponseEntity<CondominioPlataformaDTO> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(plataformaService.alterarStatus(id, false, auth()));
    }

    @PutMapping("/{id}/ativar")
    @Operation(summary = "Reativar condomínio")
    public ResponseEntity<CondominioPlataformaDTO> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(plataformaService.alterarStatus(id, true, auth()));
    }

    @PutMapping("/{id}/admin")
    @Operation(summary = "Trocar o administrador", description = "Com usuarioId, promove um usuário do condomínio (ele perde unidade e vínculo); sem ele, cria um administrador novo sem senha. Os administradores anteriores são desativados.")
    public ResponseEntity<CondominioPlataformaDTO> trocarAdmin(@PathVariable UUID id, @RequestBody TrocaAdminDTO dados) {
        return ResponseEntity.ok(plataformaService.trocarAdmin(id, dados, auth()));
    }

    private UsuarioAutenticado auth() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
