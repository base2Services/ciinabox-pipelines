/***********************************
codeArtifactPipLogin DSL

login to a code artifact repository and setup pip config global.extra-index-url for the repository

Requires pip to be avaialable in the path

example usage
codeArtifactPipLogin(
    region: 'us-east-1', // (optional, AWS region)
    domain: 'mydomain', // (required)
    duration: 900, // (optional, The time, in seconds, that the generated authorization token is valid. defaults to 900)
    repository: 'mypackage' // (required)
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CodeArtifactLogin

def call(config) {
    def clientBuilder = new AwsClientBuilder([region: config.region, env: env])
    def login = new CodeArtifactLogin(clientBuilder, config.domain, config.repository, 'pypi', config.get('duration', 900))
    def token = login.getToken()
    def endpoint = login.getEndpoint()

    withEnv([
        "CODEARTIFACT_DOMAIN=${config.domain}",
        "CODEARTIFACT_REPOSITORY=${config.repository}",
        "CODEARTIFACT_TOKEN=${token}",
        "CODEARTIFACT_ENDPOINT=${endpoint - ~/^https:\/\//}"
    ]) {
        sh '''
            set +x
            pip config --user set global.extra-index-url https://aws:${CODEARTIFACT_TOKEN}@${CODEARTIFACT_ENDPOINT}simple/
        '''
    }
}
