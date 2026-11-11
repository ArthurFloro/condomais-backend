package br.com.condomais.atendimento.controller;

import br.com.condomais.atendimento.dto.AberturaChamadoDTO;
import br.com.condomais.atendimento.dto.AtualizacaoChamadoDTO;
import br.com.condomais.atendimento.dto.ChamadoResponseDTO;
import br.com.condomais.atendimento.dto.ComentarioChamadoDTO;
import br.com.condomais.atendimento.service.ChamadoService;
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
@RequestMapping("/atendimento/chamados")
@Tag(name = "Módulo 5 - Chamados e Ocorrências", description = "Gerenciamento de chamados, ocorrências e registro de manutenções vinculadas. A portaria não tem acesso (403).")
public class ChamadoController {

    @Autowired
    private ChamadoService chamadoService;

    private UsuarioAutenticado getUsuarioLogado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    @Operation(
            summary = "Listar chamados",
            description = "Mais recentes primeiro. A administração vê todos do condomínio (a portaria não tem acesso); o morador vê só os que ele abriu (ocorrências privadas). Filtros opcionais: status (ABERTO, EM_ANALISE, EM_ATENDIMENTO, RESOLVIDO, ENCERRADO) e categoriaId."
    )
    public ResponseEntity<List<ChamadoResponseDTO>> listar(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) UUID categoriaId) {
        return ResponseEntity.ok(chamadoService.listar(status, categoriaId, getUsuarioLogado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar chamado com histórico", description = "Traz o histórico completo (abertura, mudanças de status, comentários). Para o morador, chamado que ele não abriu responde 404.")
    public ResponseEntity<ChamadoResponseDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(chamadoService.buscar(id, getUsuarioLogado()));
    }

    @PostMapping
    @Operation(
            summary = "Abrir um novo chamado ou ocorrência",
            description = "Administração ou morador (a portaria não tem acesso às ocorrências). Status inicial ABERTO, prioridade NORMAL se não informada, registro no histórico. Sem categoriaId, vai para a categoria Outros. O morador só aponta a própria unidade (ou nenhuma, para área comum)."
    )
    public ResponseEntity<ChamadoResponseDTO> abrir(@RequestBody AberturaChamadoDTO dados) {
        return ResponseEntity.ok(chamadoService.abrir(dados, getUsuarioLogado()));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Atualizar andamento (administração)",
            description = "Muda o status, recategoriza (categoriaId) e/ou registra o prestador externo e a previsão de atendimento; campos nulos não mudam e o comentário (opcional, serve de solução aplicada ao resolver) vai para o histórico. RESOLVIDO não é reaberto, só encerrado; ENCERRADO não muda mais."
    )
    public ResponseEntity<ChamadoResponseDTO> atualizar(@PathVariable UUID id, @RequestBody AtualizacaoChamadoDTO dados) {
        return ResponseEntity.ok(chamadoService.atualizar(id, dados, getUsuarioLogado()));
    }

    @PostMapping("/{id}/comentarios")
    @Operation(summary = "Comentar um chamado", description = "Quem pode ver o chamado pode comentar (o morador nos dele, a equipe em todos). Chamado ENCERRADO não aceita comentários.")
    public ResponseEntity<ChamadoResponseDTO> comentar(@PathVariable UUID id, @RequestBody ComentarioChamadoDTO dados) {
        return ResponseEntity.ok(chamadoService.comentar(id, dados, getUsuarioLogado()));
    }
}
