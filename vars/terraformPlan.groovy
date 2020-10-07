/***********************************
terraformPlan DSL

plans a terraform change

example usage
terraformPlan(
  workspace: 'dev', // (required, workspace name)
  variables: [ // (optional, key/value pairs to pass in to terraform as variable overrides)
    key: 'value'
  ],
  plan: 'plan.out' // (optional, defaults to tfplan-${workspace})
)

************************************/

def call(body) {
  def config = body
  
  if (!config.workspace) {
    error('workspace name must be supplied')
  }

  workspace_status = sh(returnStatus: true, script: "terraform workspace select ${config.workspace} -no-color")
  
  if (workspace_status != 0) {
    sh("terraform workspace new ${config.workspace}")
  }
  
  def plan = "tfplan-${config.workspace}"
  
  def tfPlanCommand = "terraform plan -out ${plan} -no-color -input=false "
  def vars = ""
  if (config.variables) {
    config.variables.each {
      vars += "-var ${it.key}=${it.value} "
    }
    tfPlanCommand += vars
  }

  if (config.varsFile) {
    tfPlanCommand += "-var-file=${config.varsFile} "
  }
  
  sh "${tfPlanCommand}"
  
  sh "terraform show -no-color -json ${plan} -json > ${plan}.json"
  sh "terraform show -no-color ${plan} > ${plan}.out"
  
  archiveArtifacts artifacts: "${plan}.*", fingerprint: true
  
  env["TFPLAN_${config.workspace.toUpperCase()}"] = plan
  stash name: plan, includes: plan
}
