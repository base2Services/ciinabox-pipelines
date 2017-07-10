/***********************************
 Shell out DSL

 returns shell out for given command, stripped of trailing
 line feed

 example usage
 def awsAccountId=shellOut('aws sts get-caller-identity --query Account --output text)
 ************************************/

def call(cmd) {

  def out = sh script: cmd, returnStdout: true

  return out.replace('\n', '')
}