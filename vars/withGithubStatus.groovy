/***********************************
withGithubStatus(
  credentialsId: 'github',
  context: 'deployment/environment/dev',
  description: 'dev deployment'
) {
  doDeployment()
}
Assumes you have GIT_URL and GIT_BRANCH environment variables available
which are set with using the git branch plugin otherwise you pass them a config
for example 
withGithubStatus(
  credentialsId: 'github',
  account: 'base2services',
  repo: 'ciinabox-pipelines',
  gitSha: '75b8f75df0b2fc50e066f2fd5375bef9908d3a4c'
  context: 'deployment/environment/dev',
  description: 'dev deployment'
) {
  doDeployment()
}
************************************/

def call(config, body) {
  try {
    notifyGH(config, "${config.description} - PENDING", 'PENDING')
    body()
    notifyGH(config, "${config.description} - SUCCESS", 'SUCCESS')
  } catch(ex) {
    notifyGH(config, "${config.description} - FAILED", 'FAILURE')
    throw ex
  }
}

def notifyGH(config, description, status) {
  def creds = config.get('credentialsId', 'github')
  def ghAccount = config.get('account', githubRepoFromUrl(env.GIT_URL)[0])
  def ghRepo = config.get('repo',  githubRepoFromUrl(env.GIT_URL)[1])

  githubNotify credentialsId: creds,
    account: ghAccount, 
    repo: ghRepo, 
    context: config.context,
    description: description,
    sha: config.get('gitSha', getRealLastCommit(env.GIT_BRANCH)), 
    status: status
}

@NonCPS
def githubRepoFromUrl(githubUrl) {
  //GIT_URL=https://github.com/base2services/ciinabox-pipelines.git
  return githubUrl.replace('https://github.com/','').replace('.git', '').split('/')
}

@NonCPS
def githubStatusFromBuildResult(result) {
  switch(result) {
    case 'SUCCESS':
      return 'SUCCESS'
    default:
      return 'FAILURE'
  }
}

@NonCPS
def String getRealLastCommit(branch_name) {
    def lastCommit=""
    
    if(steps.isUnix()){
        lastCommit = steps.sh([script: "git log -1  --pretty=format:\"%H\" origin/${branch_name}", returnStdout: true]).trim()
    }
    else{
        lastCommit = steps.bat([script: "git log -1 --pretty=format=\"%%H\" origin/${branch_name}", returnStdout: true]).split( '\n' )[2].trim()
    }
    
    steps.echo("DEBUG: Last Commit ID:${lastCommit}")
    return lastCommit
}
