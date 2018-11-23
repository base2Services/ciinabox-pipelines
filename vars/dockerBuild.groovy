/***********************************
 Docker Build Step DSL

 builds a docker image

example usage
dockerBuild {
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
  cleanup = true
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
  def cleanup = config.get('cleanup', false)
  def forceTag = config.get('forcetag','')
  def noCache = config.get('noCache', false)
  def target = config.get('target', false)
  def buildArgs = ""
  config.get('args',[:]).each { arg, value ->
     buildArgs += "--build-arg ${arg}=${value} "
  }

  println "config:${config}"
 
  def cliOpts = "-t ${dockerRepo}:${tags[0]} "
  cliOpts += "-f ${dockerfile} "
  if(noCache) {
    cliOpts += " --no-cache "
  }

  if(target) {
    cliOpts += " --target ${target} "
  }

  cliOpts += " ${buildArgs} ${buildDir} "

  sh "docker build ${cliOpts}"
  if(tags.size() > 1) {
    tags.each { tag ->
      sh "docker tag ${forceTag} ${dockerRepo}:${tags[0]} ${dockerRepo}:${tag}"
    }
  }
  if(push) {
    tags.each { tag ->
      sh "docker push ${dockerRepo}:${tag}"
    }
  }
  if(cleanup) {
    tags.each { tag ->
      sh "docker rmi ${dockerRepo}:${tag}"
    }
  }
}