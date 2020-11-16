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
  def branch = config.get('branch', env.GIT_BRANCH)
  try {
    notifyGH(config, "${config.description} - PENDING", 'PENDING', branch, env.GIT_URL)
    body()
    notifyGH(config, "${config.description} - SUCCESS", 'SUCCESS', branch, env.GIT_URL)
  } catch(ex) {
    notifyGH(config, "${config.description} - FAILED", 'FAILURE', branch, env.GIT_URL)
    throw ex
  }
}

def notifyGH(config, description, status, branch, githubUrl = null) {
  def creds = config.get('credentialsId', 'github')
  def ghAccount = null
  def ghRepo = null
  if(config.account && config.repo) {
    ghAccount = config.account
    ghRepo = config.repo
  } else if(githubUrl) {
    def gh = githubRepoFromUrl(githubUrl)
    ghAccount = gh[0]
    ghRepo = gh[1]
  } else {
    error('must supply account/repo or env.GIT_URL')
  }

  def sha = null
  if(config.gitSha) {
    sha = config.gitSha
  } else {
    sha = getRealLastCommit(branch)
  }

  githubNotify credentialsId: creds,
    account: ghAccount, 
    repo: ghRepo, 
    context: config.context,
    description: description,
    sha: sha, 
    status: status
}

def githubRepoFromUrl(githubUrl) {
  //GIT_URL=https://github.com/base2services/ciinabox-pipelines.git
  return githubUrl.replace('https://github.com/','').replace('.git', '').split('/')
}

def githubStatusFromBuildResult(result) {
  switch(result) {
    case 'SUCCESS':
      return 'SUCCESS'
    default:
      return 'FAILURE'
  }
}

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
