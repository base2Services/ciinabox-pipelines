/***********************************
codeArtifactRepository DSL

manage a AWS Code Artifact Repository

example usage
codeArtifactRepository(
    region: 'us-east-1', // (required, AWS region)
    accountId: '111111111111', // (required, AWS account id)
    description: 'my package description', // (optional)
    domain: 'mydomian', // (required)
    name: 'my-package', // (required)
    upstreams: ['upstream'], // (optional)
    externalConnections: ['public:pypi'] // (optional, supported values -> https://docs.aws.amazon.com/codeartifact/latest/APIReference/API_AssociateExternalConnection.html#API_AssociateExternalConnection_RequestSyntax)
    tags: [key: 'value'] // (optional, additiona resource tags on the repo)
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder

import com.amazonaws.services.codeartifact.model.ListDomainsRequest
import com.amazonaws.services.codeartifact.model.CreateDomainRequest
import com.amazonaws.services.codeartifact.model.DescribeRepositoryRequest
import com.amazonaws.services.codeartifact.model.CreateRepositoryRequest
import com.amazonaws.services.codeartifact.model.UpdateRepositoryRequest
import com.amazonaws.services.codeartifact.model.ListRepositoriesInDomainRequest
import com.amazonaws.services.codeartifact.model.UpstreamRepository
import com.amazonaws.services.codeartifact.model.Tag
import com.amazonaws.services.codeartifact.model.TagResourceRequest
import com.amazonaws.services.codeartifact.model.AssociateExternalConnectionRequest
import com.amazonaws.services.codeartifact.model.DisassociateExternalConnectionRequest

def call(config) {
    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        env: env])
    def client = clientBuilder.codeartifact()
    
    def domainArn = createDomainIfNotExist(client, config.domain, config.region, config.accountId)
    def repositoryExists = checkIfRepositoryExist(client, config.domain, config.name)    
    def repositoryArn = repositoryExists ? updateRepository(client, config) : createRepository(client, config)

    tagResource(client, domainArn, config.domain, config.get('tags', [:]))
    tagResource(client, repositoryArn, config.name, config.get('tags', [:]))

    def (delConnections, addConnections) = checkExternalConnections(client, config.domain, config.name, config.get('externalConnections', []))
    addConnections.each { connection -> addExternalConnection(client, config.domain, config.name, connection) }
    delConnections.each { connection -> delExternalConnection(client, config.domain, config.name, connection) }
}

def addExternalConnection(client, domain, repository, connection) {
    println("adding external connection ${connection} to codeartifact repository ${repository}")
    client.associateExternalConnection(new AssociateExternalConnectionRequest()
        .withDomain(domain)
        .withRepository(repository)
        .withExternalConnection(connection))
}

def delExternalConnection(client, domain, repository, connection) {
    println("removing external connection ${connection} to codeartifact repository ${repository}")
    client.disassociateExternalConnection(new DisassociateExternalConnectionRequest()
        .withDomain(domain)
        .withRepository(repository)
        .withExternalConnection(connection))
}

@NonCPS
def checkExternalConnections(client, domain, name, connections) {
    def result = client.describeRepository(new DescribeRepositoryRequest()
        .withDomain(domain)
        .withRepository(name))
    def externalConnections = result.getRepository().getExternalConnections()
    def existingConnections = externalConnections*.getExternalConnectionName()
    
    def addConnection = connections.toSet() - existingConnections.toSet()
    def delConnections = existingConnections.toSet() - connections.toSet()

    return [delConnections, addConnection]
}

def createDomainIfNotExist(client, domain, region, accountId) {
    def domains = client.listDomains(new ListDomainsRequest()).getDomains()
    def domainExists = domains.any { it.getName() == domain }

    if (!domainExists) {
        println("creating codeartifact domain ${domain}")
        client.createDomain(new CreateDomainRequest()
            .withDomain(domain))
    }

    return "arn:aws:codeartifact:${region}:${accountId}:domain/${domain}"
}

def checkIfRepositoryExist(client, domain, name) {
    def listRepositoryResult = client.listRepositoriesInDomain(new ListRepositoriesInDomainRequest()
        .withDomain(domain)
        .withRepositoryPrefix(name))
    def repositories = listRepositoryResult.getRepositories()
    return repositories.any { it.getName() == name }
}

def createRepository(client, config) {
    def createRepositoryRequest = new CreateRepositoryRequest()
        .withDomain(config.domain)
        .withRepository(config.name)

    if (config.description) {
        createRepositoryRequest.withDescription(description)
    }

    if (config.upstreams) {
        List<UpstreamRepository> upstreams = new ArrayList<UpstreamRepository>()
        config.upstreams.each { repositoryName ->
            upstreams.add(new UpstreamRepository().withRepositoryName(repositoryName))
        }
        createRepositoryRequest.withUpstreams(upstreams)
    }

    println("creating codeartifact repository ${config.name}")
    def createRepositoryResult = client.createRepository(createRepositoryRequest)

    return createRepositoryResult.getRepository().getArn()
}

def updateRepository(client, config) {
    def updateRepositoryRequest = new UpdateRepositoryRequest()
        .withDomain(config.domain)
        .withRepository(config.name)

    if (config.description) {
        createRepositoryRequest.withDescription(description)
    }

    if (config.upstreams) {
        List<UpstreamRepository> upstreams = new ArrayList<UpstreamRepository>()
        config.upstreams.each { repositoryName ->
            upstreams.add(new UpstreamRepository().withRepositoryName(repositoryName))
        }
        createRepositoryRequest.withUpstreams(upstreams)
    }

    println("Updating codeartifact repository ${config.name}")
    def updateRepositoryResult = client.updateRepository(updateRepositoryRequest)

    return updateRepositoryResult.getRepository().getArn()
}

def tagResource(client, resourceArn, name, extraTags) {
    List<Tag> tags = new ArrayList<Tag>()
    tags.add(new Tag().withKey('Name').withValue(name))
    tags.add(new Tag().withKey('CreatedBy').withValue('ciinabox-pipelines'))

    extraTags.each { key, value ->
        tags.add(new Tag().withKey(key).withValue(value))
    }

    println("tagging codeartifact resource ${resourceArn}")

    client.tagResource(new TagResourceRequest()
        .withResourceArn(resourceArn)
        .withTags(tags))
}