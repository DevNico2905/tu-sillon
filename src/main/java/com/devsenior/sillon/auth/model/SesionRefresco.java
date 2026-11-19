package com.devsenior.sillon.auth.model;

import com.devsenior.sillon.usuario.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un refresh token emitido. Nunca se guarda el valor: solo su hash SHA-256.
 * Todos los tokens de una misma sesión (login en un dispositivo) comparten "familia".
 */
@Entity
@Table(name = "sesiones_refresco", indexes = {
        @Index(name = "idx_sesion_familia", columnList = "familia"),
        @Index(name = "idx_sesion_expira_absoluto", columnList = "expiraAbsoluto")
})
public class SesionRefresco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** SHA-256 en hexadecimal: 64 caracteres. */
    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(nullable = false, length = 36)
    private String familia;

    @Column(nullable = false)
    private Instant creadoEn;

    /** Vence por inactividad: se renueva en cada rotación. */
    @Column(nullable = false)
    private Instant expiraEn;

    /** Máximo absoluto de la familia: nunca se extiende. */
    @Column(nullable = false)
    private Instant expiraAbsoluto;

    private Instant usadoEn;

    private Instant revocadoEn;

    protected SesionRefresco() {
    }

    public SesionRefresco(Usuario usuario, String hash, String familia, Instant creadoEn,
                          Instant expiraEn, Instant expiraAbsoluto) {
        this.usuario = usuario;
        this.hash = hash;
        this.familia = familia;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
        this.expiraAbsoluto = expiraAbsoluto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getHash() {
        return hash;
    }

    public String getFamilia() {
        return familia;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getExpiraAbsoluto() {
        return expiraAbsoluto;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(Instant usadoEn) {
        this.usadoEn = usadoEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public void setRevocadoEn(Instant revocadoEn) {
        this.revocadoEn = revocadoEn;
    }
}
