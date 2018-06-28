/***********************************
ECR Step DSL

logs docker service into Elastic Container Registry

example usage
withECR(1234567890,region) {
  sh """
  docker build -t 1234567890.dkr.ecr.${region}.amazonaws.com/myrepo/myapp:${env.BUILD_NUMBER} .
  docker push 1234567890.dkr.ecr.${region}.amazonaws.com/myrepo/myapp:${env.BUILD_NUMBER}
  """
}
************************************/

def call(awsAccountId, region, body) {
    withEnv(["REGION=$region"]) {
      sh 'eval $(aws ecr get-login --region ${REGION} --no-include-email)'
      body()
      sh "docker logout  https://${awsAccountId}.dkr.ecr.${region}.amazonaws.com"
    }
}
