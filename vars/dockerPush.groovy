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
  cleanup = true
}
************************************/

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = body

  def dockerRepo = "${config.repo}/${config.image}"
  def tags = config.get('tags',['latest'])
  def cleanup = config.get('cleanup', false)

  tags.each { tag ->
    sh "docker push ${dockerRepo}:${tag}"
  }

  if(cleanup) {
    tags.each { tag ->
      sh "docker rmi ${dockerRepo}:${tag}"
    }
  }

}
