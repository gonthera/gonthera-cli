package br.com.smartverse.gonthera.cli.model;

import br.com.smartverse.gonthera.cli.enuns.DatabaseProvider;
import lombok.Data;

@Data
public class Database {
    private DatabaseProvider provider;
}
