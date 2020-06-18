/***********************************
terraformApply DSL

apllies a terraform plan

example usage
terraformApply(
  workspace: 'dev', // (required, workspace name)
  plan: 'plan.out' // (optional, defaults to tfplan-${workspace})
)

************************************/

def call(body) {
  def config = body
  
  if (!config.workspace) {
    error('workspace name must be supplied')
  }
  
  sh "terraform workspace select ${config.workspace} -no-color"
  
  def plan = config.get('plan', "tfplan-${config.workspace}")
  
  unstash name: plan
  
  sh "terraform apply -auto-approve -lock=true -no-color -input=false ${plan}"
}
