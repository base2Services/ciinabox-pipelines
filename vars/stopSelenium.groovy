/***********************************
stopSelenium DSL

stops selenium hub on fargate

example usage

stopSelenium(
  region: 'us-east-1', //(optional, defaults to lookup current region)
  cluster: 'my-cluster', //(optional, defaults to lookup current ecs cluster)
  taskArn: 'arn:aws:ecs:us-east-1:123456789:task/my-cluster/id' //(optional, defaults to the SELENIUM_TASK environment set by the startSelenium() method)
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.GetEcsContainerDetails
import com.base2.ciinabox.EcsTaskRunner
import com.base2.ciinabox.aws.Util

def call(body=[:]) {
  def config = body
  def details = [:]
  
  // get the local region if not set by the method
  def region = config.get('region', Util.getRegion())
  if (!region) {
    throw new GroovyRuntimeException("no AWS region found")
  }
  
  def task = new GetEcsContainerDetails(region)
  details.cluster = config.get('cluster', task.cluster())
  
  def envTaskArn = env.getAt('SELENIUM_TASK')
  details.taskArn = config.get('taskArn', envTaskArn)
  
  if (!details.taskArn) {
    println "WARNING: no taskArn found, unable to clean up."
    return
  }
  
  def client = new AwsClientBuilder([region: region]).ecs()
  def runner = new EcsTaskRunner('selenium', client)
  runner.stopTask(details)
}