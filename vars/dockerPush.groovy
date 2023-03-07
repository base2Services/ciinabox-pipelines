/***********************************
 Docker Push Step DSL

 pushes a docker image to a repote repository

example usage
dockerPush(
  repo: 'myrepo' // required
  image: 'myimage' // required
  tags: [ // (optional, tags to push to the remote repository. defaults to 'latest')
    env.BUILD_NUMBER,
    'latest'
  ]
  cleanup: true | false // (optional, removes the image once push is complete. defaults to false)
)

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
