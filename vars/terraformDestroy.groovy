/***********************************
terraformDestroy DSL

plans a terraform change

example usage
terraformDestroy(
  workspace: 'dev', // (required, workspace name)
)

************************************/

def call(body) {
  def config = body
  
  if (!config.workspace) {
    error('workspace name must be supplied')
  }
  
  sh "terraform workspace select ${config.workspace} -no-color"
  
  sh "terraform destroy -no-color -auto-approve"
}
