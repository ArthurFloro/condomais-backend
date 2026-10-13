package br.com.condomais.assistente.controller;

import br.com.condomais.assistente.dto.ConversaAssistenteDTO;
import br.com.condomais.assistente.dto.RespostaAssistenteDTO;
import br.com.condomais.assistente.service.AssistenteService;
import br.com.condomais.core.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assistente")
@Tag(name = "Assistente virtual", description = "Assistente de IA que responde dúvidas do morador consultando os dados dele (encomendas, reservas, visitas, chamados e comunicados). A IA roda no serviço Python condomais-assistente.")
public class AssistenteController {

    @Autowired
    private AssistenteService assistenteService;

    @PostMapping("/mensagens")
    @Operation(
            summary = "Conversar com o assistente (somente MORADOR)",
            description = "Recebe o histórico da conversa (papel USUARIO ou ASSISTENTE, a última sendo do morador) e devolve a resposta. "
                    + "A conversa é repassada ao serviço condomais-assistente (Python), que consulta somente os dados do morador autenticado e não altera nada. "
                    + "Responde 503 se o serviço não estiver configurado (ASSISTENTE_API_URL) ou estiver fora do ar."
    )
    public ResponseEntity<RespostaAssistenteDTO> conversar(@RequestBody ConversaAssistenteDTO conversa,
                                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(assistenteService.responder(conversa.mensagens(), auth, authorization));
    }
}
