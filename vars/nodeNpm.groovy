/***********************************
 Docker Build Step DSL

 builds a docker image

example usage
nodeNpm {
  version = '0.10.33'
  sshAgent = true
  dir = 'app/'
  env = 'production'
  tasks = [
    'install'
  ]
  archive = [
    'path' : '.',
    'file' : 'source.tar.gz'
  ]
}
************************************/

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def appVolume = "-v ${config.get('dir', '`pwd`')}:/data/node"
  def nodeEnv = config.get('env', 'production')
  def nodeVersion = config.get('version', 'node:latest')
  def archive = config.get('archive', [:])
  def archiveFile = archive.get('file','source.tar.gz')
  def archivePath = archive.get('path', '.')
  def sshAgentEnabled = config.get('sshAgent',false)
  def sshAgent = ''
  if(sshAgentEnabled) {
    sshAgent = '-v ${SSH_AUTH_SOCK}:/tmp/ssh_auth_sock -e SSH_AUTH_SOCK=/tmp/ssh_auth_sock'
  }
  sh """
    #!/bin/bash
    docker run --rm ${appVolume} ${sshAgent} -e "NODE_ENV=${nodeEnv}" ${nodeVersion} npm install
    if [ \$? -ne 0 ]; then
	    docker run --rm ${appVolume} ${nodeVersion} rm -rf node_modules
	    echo 'Build Failed!'
      exit 2
    fi
    docker run --rm ${appVolume} ${nodeVersion} chown -R 1000:1000 .
    if [ \$? -ne 0 ]; then
    	echo 'failed to chown!'
        exit 2
    fi
    tar -czf ${archiveFile} --exclude-vcs --exclude '${archiveFile}' ${archivePath}
  """
}
