/***********************************
scanImage()

Scan a Docker image in a remote registry for security vulnerabilities using Clair.

example usage

scanImage(
  failureThreshold: 'Critical',
  repository: '214044641124.dkr.ecr.ap-southeast-2.amazonaws.com',
  image: 'my_repo/nginx',
  tags: ['latest'],
  region: 'ap-southeast-2',
  clairURL: 'clair.base2services.com'   # Optional
)

Available thresholds:
Unknown | Negligible | Low | Medium | High | Critical | Defcon1

************************************/

def call(body) {

  def clairURL = body.get('clairURL', 'coreo-Clair-QDQKVCAO6IRL-2075767864.ap-southeast-2.elb.amazonaws.com')
  def random = Math.abs(new Random().nextInt())

  sh """#!/bin/bash
  set -x
  ECR_LOGIN=`aws ecr get-login --region ${body.region} --no-include-email`

  tee klar-${random}.env <<EOF
CLAIR_ADDR=${body.clairURL}
CLAIR_OUTPUT=${body.failureThreshold}
DOCKER_USER=AWS
DOCKER_PASSWORD=`echo \$ECR_LOGIN | cut -d ' ' -f6`
  EOF
  """

  for (String tag: body.tags) {
    echo "Scanning image: ${body.repository}/${body.image}:${tag} ..."
    sh "docker run --env-file=klar-${random}.env rererecursive/klar ${body.repository}/${body.image}:${tag}"
  }

}

