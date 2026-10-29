package pe.edu.vacantes.domain;
public enum ApplicationStatus {
    ENVIADA("Enviada","enviada"), EN_REVISION("En revisión","en-revision"),
    OBSERVADA("Con observaciones","observada"), APROBADA("Aprobada","aprobada"),
    RECHAZADA("No admitida","rechazada"), RETIRADA("Retirada","retirada");
    private final String label;
    private final String cssClass;
    ApplicationStatus(String label,String cssClass) { this.label=label; this.cssClass=cssClass; }
    public String getLabel() { return label; }
    public String getCssClass() { return cssClass; }
}
