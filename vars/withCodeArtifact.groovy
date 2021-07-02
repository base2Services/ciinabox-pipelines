/***********************************
withCodeArtifact DSL

login to a code artifact repository and exports the code artifact token and endpoint

example usage
withCodeArtifact(
    region: 'us-east-1', // (optional, AWS region)
    domain: 'mydomain', // (required)
    duration: 900, // (optional, The time, in seconds, that the generated authorization token is valid. defaults to 900)
    repository: 'mypackage', // (required)
    format: 'npm'|'pypi'|'maven'|'nuget' // (required)
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CodeArtifactLogin

def call(config, body) {
    def clientBuilder = new AwsClientBuilder([region: config.region, env: env])
    def login = new CodeArtifactLogin(clientBuilder, config.domain, config.repository, config.format, config.get('duration', 900))
    def token = login.getToken()
    def endpoint = login.getEndpoint()

    withEnv([
      "CODEARTIFACT_TOKEN=${token}",
      "CODEARTIFACT_ENDPOINT=${endpoint}"
    ]) {
      body()
    }
}
