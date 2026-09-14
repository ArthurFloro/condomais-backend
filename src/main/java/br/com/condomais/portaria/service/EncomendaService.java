package br.com.condomais.portaria.service;

import br.com.condomais.auth.repository.UsuarioApartamentoRepository;
import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.ApartamentoRepository;
import br.com.condomais.portaria.dto.RegistroEncomendaDTO;
import br.com.condomais.portaria.model.Encomenda;
import br.com.condomais.portaria.repository.EncomendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EncomendaService {

    @Autowired
    private EncomendaRepository encomendaRepository;
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioApartamentoRepository usuarioApartamentoRepository;

    @Transactional
    public Encomenda registrarRecebimento(RegistroEncomendaDTO dados, UUID porteiroId, UUID condominioId) {
        // Valida o apartamento e o isolamento do condomínio (RN-CON-004)
        var apartamento = apartamentoRepository.findByIdAndCondominioId(dados.apartamentoId(), condominioId)
                .orElseThrow(() -> new IllegalArgumentException("Apartamento não encontrado neste condomínio."));

        var porteiro = usuarioRepository.findById(porteiroId)
                .orElseThrow(() -> new IllegalArgumentException("Porteiro não encontrado."));

        var encomenda = new Encomenda();
        encomenda.setApartamento(apartamento);
        encomenda.setCondominio(apartamento.getCondominio());
        encomenda.setCodigoIdentificacao(dados.codigoIdentificacao());
        encomenda.setObservacao(dados.observacao());
        encomenda.setStatus("AGUARDANDO RETIRADA"); // Status inicial simplificado
        encomenda.setDataHoraRecebimento(LocalDateTime.now());
        encomenda.setPorteiroRecebimento(porteiro); // Rastreabilidade de quem recebeu

        // Verifica se existe morador cadastrado para notificação futura (V1.1)
        var moradores = usuarioApartamentoRepository.findByApartamentoId(apartamento.getId());
        if (!moradores.isEmpty()) {
            // Associa ao primeiro morador principal encontrado (simplificação)
            encomenda.setMorador(moradores.get(0).getUsuario());
            // TODO: Aqui faremos a integração com o Módulo 4 para disparar a Notificação ao morador
        }

        return encomendaRepository.save(encomenda);
    }

    @Transactional
    public Encomenda registrarRetirada(UUID encomendaId, UUID porteiroId, UUID condominioId) {
        var encomenda = encomendaRepository.findById(encomendaId)
                .orElseThrow(() -> new IllegalArgumentException("Encomenda não encontrada."));

        if (!encomenda.getCondominio().getId().equals(condominioId)) {
            throw new SecurityException("Acesso negado a dados de outro condomínio.");
        }

        var porteiro = usuarioRepository.findById(porteiroId)
                .orElseThrow(() -> new IllegalArgumentException("Porteiro não encontrado."));

        // Atualiza status para RETIRADA e registra a rastreabilidade (RF-POR-012, RN-POR-009)
        encomenda.setStatus("RETIRADA");
        encomenda.setDataHoraRetirada(LocalDateTime.now());
        encomenda.setPorteiroRetirada(porteiro);

        return encomendaRepository.save(encomenda);
    }
}