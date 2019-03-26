/***********************************
 Docker Build Step DSL

 builds a docker image

example usage
dockerBuild {
  repo = 'myrepo'
  image = 'myimage'
  tags = [
    '${BUILD_NUMBER}',
    'latest'
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
  def push = config.get('push', false)
  def cleanup = config.get('cleanup', false)
  def forceTag = config.get('forcetag','')

  if(tags.size() > 1) {
    tags.each { tag ->
      sh "docker tag ${forceTag} ${dockerRepo}:${tags[0]} ${dockerRepo}:${tag}"
    }
  }

  dockerPush(
    repo: config.repo,
    image: config.image,
    tags: tags,
    cleanup: cleanup
  )
}
