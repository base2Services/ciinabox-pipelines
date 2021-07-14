/***********************************
withCodeArtifactTwine DSL

login to a code artifact repository and exports twine environment variables

Requires twine to be avaialable in the path

example usage
withCodeArtifactTwine(
    region: 'us-east-1', // (optional, AWS region)
    domain: 'mydomain', // (required)
    duration: 900, // (optional, The time, in seconds, that the generated authorization token is valid. defaults to 900)
    repository: 'mypackage' // (required)
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.aws.CodeArtifactLogin

def call(config, body) {
    def clientBuilder = new AwsClientBuilder([region: config.region, env: env])
    def login = new CodeArtifactLogin(clientBuilder, config.domain, config.repository, 'pypi', config.get('duration', 900))
    def token = login.getToken()
    def endpoint = login.getEndpoint()

    withEnv([
        "TWINE_USERNAME=aws",
        "TWINE_PASSWORD=${token}",
        "TWINE_REPOSITORY_URL=${endpoint}"
    ]) {
        body()
    }
}
