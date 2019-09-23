/***********************************
dockerScan()

Scan a Docker image in a remote registry for security vulnerabilities using Clair.

example usage

dockerScan(
  threshold: params.CLAIR_THRESHOLD,
  accountId: env.OPS_ACCOUNT_ID,
  image: "frontend:latest",
  repo: env.ECR_REPO,
  action: params.CLAIR_RESULT,
  region: env.REGION
)

Available actions:
  Ignore | Fail

Available thresholds:
  Unknown | Negligible | Low | Medium | High | Critical | Defcon1

************************************/

def call(body) {
  sh "/bin/start-clair.sh"
  if (body.accountId && body.region) {
    body.image = "${body.repo}/${body.image}"
    body.password = sh (script: "aws ecr get-login --region ${body.region} --no-include-email", returnStdout: true).split()[5]
  }

  runKlar(body)
}

def runKlar(body) {
  def threshold = body.get('threshold', 'Critical')

  if (body.action.toLowerCase() == 'ignore') {
    sh "DOCKER_USER=AWS DOCKER_PASSWORD=${body.password} CLAIR_OUTPUT=${threshold} /bin/klar ${body.image} || true"
  } else {
    sh "DOCKER_USER=AWS DOCKER_PASSWORD=${body.password} CLAIR_OUTPUT=${threshold} /bin/klar ${body.image}"
  }
}
