package pe.edu.vacantes.domain;
public enum Grade {
    INICIAL_4("Inicial · 4 años","Inicial"), INICIAL_5("Inicial · 5 años","Inicial"),
    PRIMARIA_1("1.º de primaria","Primaria"), PRIMARIA_2("2.º de primaria","Primaria"),
    PRIMARIA_3("3.º de primaria","Primaria"), PRIMARIA_4("4.º de primaria","Primaria"),
    PRIMARIA_5("5.º de primaria","Primaria"), PRIMARIA_6("6.º de primaria","Primaria"),
    SECUNDARIA_1("1.º de secundaria","Secundaria"), SECUNDARIA_2("2.º de secundaria","Secundaria"),
    SECUNDARIA_3("3.º de secundaria","Secundaria"), SECUNDARIA_4("4.º de secundaria","Secundaria"),
    SECUNDARIA_5("5.º de secundaria","Secundaria");
    private final String label;
    private final String level;
    Grade(String label,String level) { this.label=label; this.level=level; }
    public String getLabel() { return label; }
    public String getLevel() { return level; }
}
