package br.com.condomais.interativo.service;

import br.com.condomais.auth.repository.UsuarioRepository;
import br.com.condomais.condominio.repository.TorreRepository;
import br.com.condomais.interativo.dto.RegistroAvisoDTO;
import br.com.condomais.interativo.model.Aviso;
import br.com.condomais.interativo.repository.AvisoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AvisoService {

    @Autowired
    private AvisoRepository avisoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TorreRepository torreRepository;

    @Transactional
    public Aviso publicarAviso(RegistroAvisoDTO dados, UUID autorId, UUID condominioId) {
        var autor = usuarioRepository.findById(autorId)
                .orElseThrow(() -> new IllegalArgumentException("Autor não encontrado."));

        var aviso = new Aviso();
        aviso.setTitulo(dados.titulo());
        aviso.setConteudo(dados.conteudo());

        // Aplicação da regra de prioridade simplificada (V1.1)
        String prioridade = dados.prioridade().toUpperCase();
        if (!prioridade.equals("NORMAL") && !prioridade.equals("URGENTE")) {
            throw new IllegalArgumentException("A prioridade deve ser NORMAL ou URGENTE.");
        }
        aviso.setPrioridade(prioridade);

        // Define a validade após a qual o aviso deixará de ser ativo
        aviso.setDataValidade(dados.dataValidade());

        // Garante o isolamento Multi-Condomínio e vincula o autor
        aviso.setCondominio(autor.getCondominio());
        aviso.setAutor(autor);
        aviso.setCreatedAt(LocalDateTime.now());

        // Regra de direcionamento: verifica se é para uma torre/bloco específico
        if (dados.torreId() != null) {
            var torre = torreRepository.findById(dados.torreId())
                    .orElseThrow(() -> new IllegalArgumentException("Torre não encontrada."));

            // Valida se a torre pertence ao condomínio atual antes de direcionar
            if (!torre.getCondominio().getId().equals(condominioId)) {
                throw new SecurityException("A torre informada não pertence a este condomínio.");
            }
            aviso.setTorre(torre);
        }

        return avisoRepository.save(aviso);
    }
}