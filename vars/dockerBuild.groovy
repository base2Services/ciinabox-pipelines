/***********************************
 Docker Build Step DSL

 builds a docker image

example usage
dockerBuild(
  dir: '.', // (optional, defaults to the current directory)
  repo: 'myrepo', // required
  image: 'myimage', // required
  tags: [ // (optional, defaults to 'latest')
    env.BUILD_NUMBER,
    'latest'
  ],
  args: [ // (optional dependent on your Dockerfile. pass in arguments to your docker build)
    'nodeVersion': '0.10.33'
  ],
  push: true | false, // (optional, pushes the image to the remote repository. defaults to false)
  cleanup: true | false, // (optional, remove the image post build. defaults to false)
  pull: true | false, // (optional, Always attempt to pull a newer version of the image. defaults to false)
  noCache: true | false // (optional, Do not use cache when building the image. defaults to false)
  target: true | false // (optional, Set the target build stage to build. defaults to false)
)
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
  def pull = config.get('pull', false)
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
 
  if(pull) {
    cliOpts += " --pull "
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
