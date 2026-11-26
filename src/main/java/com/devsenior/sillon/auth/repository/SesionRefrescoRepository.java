package com.devsenior.sillon.auth.repository;

import com.devsenior.sillon.auth.model.SesionRefresco;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionRefrescoRepository extends JpaRepository<SesionRefresco, Long> {

    /** Trae también el usuario y su salón: se necesitan para armar la respuesta fuera de la transacción. */
    @EntityGraph(attributePaths = {"usuario", "usuario.salon"})
    Optional<SesionRefresco> findByHash(String hash);

    /** Atómico: si dos peticiones llegan a la vez con el mismo token, solo una obtiene 1. */
    @Modifying
    @Query("update SesionRefresco s set s.usadoEn = :ahora "
            + "where s.id = :id and s.usadoEn is null and s.revocadoEn is null")
    int marcarUsada(@Param("id") Long id, @Param("ahora") Instant ahora);

    @Modifying
    @Query("update SesionRefresco s set s.revocadoEn = :ahora "
            + "where s.familia = :familia and s.revocadoEn is null")
    int revocarFamilia(@Param("familia") String familia, @Param("ahora") Instant ahora);

    /** Cierra todas las sesiones del usuario: al cambiarle la contraseña o desactivarlo (R43). */
    @Modifying
    @Query("update SesionRefresco s set s.revocadoEn = :ahora "
            + "where s.usuario.id = :usuarioId and s.revocadoEn is null")
    int revocarDelUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("delete from SesionRefresco s where s.expiraAbsoluto < :limite")
    int borrarVencidas(@Param("limite") Instant limite);
}
