/***********************************
 Docker Tag Step DSL

 tags a docker image

example usage
dockerTag {
  repo = 'myrepo'
  image = 'myimage'
  newImage = 'newImage'
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
  def image = config.get('image')
  def newImage = config.get('newImage', image)
  def push = config.get('push', false)
  def pull = config.get('pull', false)
  def cleanup = config.get('cleanup', false)

  if(pull) {
    sh "docker pull ${config.repo}/${image}:${baseTag}"
  }

  if(tags) {
    tags.each { tag ->
      sh "docker tag ${config.repo}/${image}:${baseTag} ${config.repo}/${newImage}:${tag}"
    }
  }

  dockerPush(repo: config.repo, image: config.newImage, tags: tags, cleanup: cleanup)
}
