/***********************************
 Docker Tag Step DSL

 tags a docker image

example usage
dockerBuild {
  repo = 'myrepo'
  image = 'myimage'
  baseTag = 'latest'
  tags = [
    '${BUILD_NUMBER}',
    '${GIT_SHA}'
  ]
  push = true
  pull = false
  cleanup = true
}
************************************/

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = body

  def baseTag = config.get('baseTag', 'latest')
  def tags = config.get('tags',['latest'])
  def dockerRepo = "${config.repo}/${config.image}"
  def push = config.get('push', false)
  def pull = config.get('push', false)
  def cleanup = config.get('cleanup', false)

  if(pull) {
    sh "docker pull ${dockerRepo}:${baseTag}"
  }

  if(tags) {
    tags.each { tag ->
      sh "docker tag ${dockerRepo}:${baseTag} ${dockerRepo}:${tag}"
    }
  }

  dockerPush(repo: config.repo, image: config.image, tags: tags, cleanup: cleanup)
}
