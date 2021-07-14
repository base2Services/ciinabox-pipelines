package com.base2.ciinabox.aws

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.codeartifact.model.GetRepositoryEndpointRequest
import com.amazonaws.services.codeartifact.model.GetAuthorizationTokenRequest

class CodeArtifactLogin implements Serializable {

    AwsClientBuilder clientBuilder
    String domain
    String repository
    String format
    int duration

    CodeArtifactLogin(AwsClientBuilder clientBuilder, String domain, String repository, String format, int duration) {
        this.clientBuilder = clientBuilder
        this.domain = domain
        this.repository = repository
        this.format = format
        this.duration = duration
    }

    def getEndpoint() {
        def client = clientBuilder.codeartifact()
        def getRepositoryEndpointRequest = new GetRepositoryEndpointRequest()
            .withDomain(domain)
            .withFormat(format)
            .withRepository(repository)
        def getRepositoryEndpointResult = client.getRepositoryEndpoint(getRepositoryEndpointRequest)
        return getRepositoryEndpointResult.getRepositoryEndpoint()
    }

    def getToken() {
        def client = clientBuilder.codeartifact()
        def getAuthorizationTokenResult = client.getAuthorizationToken(new GetAuthorizationTokenRequest()
            .withDomain(domain)
            .withDurationSeconds(duration))
        return getAuthorizationTokenResult.getAuthorizationToken()
    }

}