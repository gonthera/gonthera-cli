package br.com.smartverse.gonthera.cli.model;

import lombok.Data;

@Data
public class Authorization {

    private boolean authenticateAbstract;
    private boolean tenantConfigurationAbstract;
}
