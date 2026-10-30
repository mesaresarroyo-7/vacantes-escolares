package pe.edu.vacantes.model;
public enum Role {
    APODERADO, ADMISION, ADMIN;
    public String getLabel() {
        return switch (this) { case APODERADO -> "Apoderado"; case ADMISION -> "Admisiones"; case ADMIN -> "Administrador"; };
    }
}
