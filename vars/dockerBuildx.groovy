/***********************************
 Docker Buildx Step DSL
 THIS STEP REQUIRES CIINABOX2 AND WORKER AMIS FROM 20-08-2021 AND ONWARDS

 builds a docker image using docker buildx for multiple arch support

example usage
dockerBuildx {
  dir = '.'
  repo = 'myrepo'
  image = 'myimage'
  tags = [
    '${BUILD_NUMBER}',
    'latest'
  ]
  args = [
    'nodeVersion':'0.10.33'
  ]
  push = true
  pull = true
  archTypes = [
    'linux/arm/v7',
    'linux/arm64/v8',
    'linux/amd64'
  ]
}
************************************/

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = body

  def tags = config.get('tags',['latest'])
  def dockerRepo = "${config.repo}/${config.image}"
  def buildDir = config.get('dir', '.')
  def dockerfile = config.get('dockerfile', 'Dockerfile')
  def push = config.get('push', false)
  def noCache = config.get('noCache', false)
  def target = config.get('target', false)
  def pull = config.get('pull', false)
  def buildArgs = ""
  config.get('args',[:]).each { arg, value ->
     buildArgs += "--build-arg ${arg}=${value} "
  }

  println "config:${config}"

  def cliOpts = "-f ${dockerfile} "
  if(noCache) {
    cliOpts += " --no-cache "
  }

  if(target) {
    cliOpts += " --target ${target} "
  }

  if(pull) {
    cliOpts += " --pull "
  }

  cliOpts += " --platform ${config.archTypes.join(',')}"


  if(tags.size() > 1) {
    tags.each { tag ->
      cliOpts += " -t ${dockerRepo}:${tag} "
    }
  }

  if(push) {
    cliOpts += " --push "
  }

  cliOpts += " ${buildArgs} ${buildDir} "
  sh "docker buildx build ${cliOpts}"

}
