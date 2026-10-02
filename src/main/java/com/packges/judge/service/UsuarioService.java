package com.packges.judge.service;

import com.packges.judge.dto.usuario.UsuarioCreateRequest;
import com.packges.judge.dto.usuario.UsuarioResponse;
import com.packges.judge.entity.Usuario;
import com.packges.judge.exception.ConflitoException;
import com.packges.judge.exception.RecursoNaoEncontradoException;
import com.packges.judge.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse cadastrar(UsuarioCreateRequest request) {
        if (usuarioRepository.existsByLogin(request.login())) {
            throw new ConflitoException("Login já está em uso");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setLogin(request.login());
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuarioRepository.save(usuario);

        log.info("Usuário cadastrado: {}", usuario.getId());
        return UsuarioResponse.from(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(UUID id) {
        return UsuarioResponse.from(obterEntidade(id));
    }

    Usuario obterEntidade(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }
}
