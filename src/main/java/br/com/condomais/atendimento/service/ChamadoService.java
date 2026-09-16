package br.com.condomais.atendimento.service;

import br.com.condomais.atendimento.dto.AberturaChamadoDTO;
import br.com.condomais.atendimento.model.*;
import br.com.condomais.atendimento.repository.*;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChamadoService {

    @Autowired private ChamadoRepository chamadoRepository;
    @Autowired private CategoriaChamadoRepository categoriaRepository;
    @Autowired private ApartamentoRepository apartamentoRepository;
    @Autowired private HistoricoChamadoRepository historicoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @Transactional
    public Chamado abrirChamado(AberturaChamadoDTO dados, UUID solicitanteId, UUID condominioId) {
        var solicitante = usuarioRepository.findById(solicitanteId).orElseThrow();
        var categoria = categoriaRepository.findByIdAndCondominioId(dados.categoriaId(), condominioId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        var chamado = new Chamado();
        chamado.setTitulo(dados.titulo());
        chamado.setDescricao(dados.descricao());
        chamado.setPrioridade(dados.prioridade().toUpperCase()); // Ex: BAIXA, NORMAL (RF-CHA-006)
        chamado.setStatus("ABERTO"); // Status padrão (RF-CHA-008)
        chamado.setDataAbertura(LocalDateTime.now());
        chamado.setCategoria(categoria);
        chamado.setSolicitante(solicitante);
        chamado.setCondominio(solicitante.getCondominio());
        chamado.setImagemUrl(dados.imagemUrl()); // Anexo opcional (RN-CHA-005)

        if (dados.apartamentoId() != null) {
            var apto = apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), condominioId).orElseThrow();
            chamado.setApartamento(apto);
        }

        var chamadoSalvo = chamadoRepository.save(chamado);

        // Gera o log obrigatório de histórico (RF-CHA-012)
        var historico = new HistoricoChamado();
        historico.setAcao("ABERTURA");
        historico.setComentario("Chamado aberto no sistema.");
        historico.setDataHora(LocalDateTime.now());
        historico.setChamado(chamadoSalvo);
        historico.setUsuario(solicitante);
        historicoRepository.save(historico);

        return chamadoSalvo;
    }
}