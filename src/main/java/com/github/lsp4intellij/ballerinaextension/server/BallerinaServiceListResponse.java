package com.github.lsp4intellij.ballerinaextension.server;

/**
 * Represents a Ballerina service list reuqest.
 *
 * @since 0.981.2
 */
public class BallerinaServiceListResponse {

    private String[] services;

    public String[] getServices() {
        return this.services;
    }

    public void setServices(String[] services) {
        this.services = services;
    }

}
