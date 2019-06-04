/***********************************
dockerScan()

Scan a Docker image in a remote registry for security vulnerabilities using Clair.

example usage

dockerScan(
  threshold: 'Critical',
  accountId: '214044641124',
  image: 'my_repo/nginx:latest',
  action: 'Ignore',          // Fail | Ignore
  region: 'ap-southeast-2',
  //whiteList: 's3://...'
)

Available thresholds:
  Unknown | Negligible | Low | Medium | High | Critical | Defcon1

************************************/

def call(body) {
  sh "/bin/start-clair.sh"

  if (body.accountId) {
    def image = "${body.accountId}.dkr.ecr.${body.region}.amazonaws.com"

    withECR(body.accountId, body.region) {
      runKlar(body, image)
    }
  }
  else {
    runKlar(body, body.image)
  }
}

def runKlar(body, image) {
  if (body.action && body.action.toLowerCase() == 'ignore') {
    sh "CLAIR_OUTPUT=${body.get('threshold', 'Critical')} /bin/klar ${image} || true"
  } else {
    sh "CLAIR_OUTPUT=${body.get('threshold', 'Critical')} /bin/klar ${image}"
  }
}
