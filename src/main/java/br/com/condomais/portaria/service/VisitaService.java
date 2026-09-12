package br.com.condomais.portaria.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.portaria.dto.RegistroEntradaDTO;
import br.com.condomais.portaria.model.Visita;
import br.com.condomais.portaria.model.Visitante;
import br.com.condomais.portaria.repository.VisitaRepository;
import br.com.condomais.portaria.repository.VisitanteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VisitaService {

    @Autowired
    private VisitaRepository visitaRepository;
    @Autowired
    private VisitanteRepository visitanteRepository;
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public Visita registrarEntrada(RegistroEntradaDTO dados, UUID porteiroId, UUID condominioId) {
        // Valida o apartamento respeitando o isolamento do condomínio (RN-CON-004)
        var apartamento = apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), condominioId)
                .orElseThrow(() -> new IllegalArgumentException("Apartamento não encontrado neste condomínio."));

        var porteiro = usuarioRepository.findById(porteiroId)
                .orElseThrow(() -> new IllegalArgumentException("Porteiro não encontrado."));

        // Busca ou cadastra o visitante na hora (RF-POR-004)
        var visitante = visitanteRepository.findByCpf(dados.cpf()).orElseGet(() -> {
            var novoVisitante = new Visitante();
            novoVisitante.setCpf(dados.cpf());
            novoVisitante.setNome(dados.nome());
            novoVisitante.setTelefone(dados.telefone());
            return visitanteRepository.save(novoVisitante);
        });

        // Cria o registro da visita
        var visita = new Visita();
        visita.setVisitante(visitante);
        visita.setApartamento(apartamento);
        visita.setCondominio(apartamento.getCondominio());
        visita.setStatus("ENTROU");
        visita.setObservacao(dados.observacao());
        visita.setDataHoraEntrada(LocalDateTime.now());
        visita.setPorteiroEntrada(porteiro); // Rastreabilidade (RF-POR-014)

        return visitaRepository.save(visita);
    }

    @Transactional
    public Visita registrarSaida(UUID visitaId, UUID porteiroId, UUID condominioId) {
        var visita = visitaRepository.findById(visitaId)
                .orElseThrow(() -> new IllegalArgumentException("Visita não encontrada."));

        // Valida se a visita pertence ao condomínio do porteiro logado
        if (!visita.getCondominio().getId().equals(condominioId)) {
            throw new SecurityException("Acesso negado a dados de outro condomínio.");
        }

        var porteiro = usuarioRepository.findById(porteiroId)
                .orElseThrow(() -> new IllegalArgumentException("Porteiro não encontrado."));

        // Atualiza status e horários (RF-POR-007)
        visita.setStatus("SAIU");
        visita.setDataHoraSaida(LocalDateTime.now());
        visita.setPorteiroSaida(porteiro);

        return visitaRepository.save(visita);
    }
}