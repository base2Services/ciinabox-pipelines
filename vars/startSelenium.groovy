/***********************************
startSelenium DSL

starts a selenium hub on fargate

example usage

startSelenium(
  nodes: ['chrome','firefox','opera'], // (optional, defaults to chrome and firefox nodes)
  version: '3', // (optional, defaults to 3)
  region: 'us-east-1', // (optional, defaults to look up the current region)
  subnet: 'subnet-123456789', // (optional, defaults to look up the current subnet)
  securityGroup: 'sg-123456789', // (optional, defaults to look up the current security group)
  executionRole: 'arn:aws:iam:accountid::role/executionRole', // (optional, defaults to look up the current executionRole)
  cluster: 'my-cluster', // (optional, defaults to look up the current ecs cluster)
  cpu: '512', // (optional, defaults to 512)
  memory: '1024', // (optional, defaults to 1024)
)


************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import com.base2.ciinabox.GetEcsContainerDetails
import com.base2.ciinabox.EcsTaskRunner

def call(body=[:]) {
  def config = body
  def details = [:]
  
  def metadata = new InstanceMetadata()
  // if the node is a ec2 instance using the ec2 plugin
  def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)
    
  if (!instanceId) {
    instanceId = metadata.instanceId()
  }
  
  def region = config.get('region', metadata.region())
  
  def instance = new GetInstanceDetails(region, instanceId)
  details.subnet = config.get('subnet', instance.subnet())
  details.securityGroup = config.get('securityGroup', instance.securityGroup())
  
  def task = new GetEcsContainerDetails(region)
  details.executionRole = config.get('executionRole', task.executionRole())
  details.cluster = config.get('cluster', task.cluster())
  
  details.cpu = config.get('cpu', '512')
  details.memory = config.get('memory', '1024')

  def allowedNodes = ['chrome','firefox','opera']
  def version = config.get('version', '3')
  
  def taskDef = []
  taskDef << [
    name: 'selenium-hub',
    image: "selenium/hub:${version}",
    ports: [[container: 4444, host: 4444]],
  ]
  
  def nodes = config.get('nodes',['chrome','firefox'])
  def nodePort = 5555
  
  nodes.eachWithIndex { node, index ->
    if (allowedNodes.contains(node)) {
      nodePort += index
      
      taskDef << [
        name: node,
        image: "selenium/node-${node}:${version}",
        environment: [
          [key: 'HUB_HOST', value: 'localhost'],
          [key: 'HUB_PORT', value: '4444'],
          [key: 'START_XVFB', value: 'false'],
          [key: 'SE_OPTS', value: "-port ${nodePort}"]
        ]
      ]
    } else {
      echo "WARNING: ${node} is not a known selenium node. Must be one of ${allowedNodes}"
    }
  }
  
  def client = new AwsClientBuilder([region: region]).ecs()
  def runner = new EcsTaskRunner('selenium', client)
  def taskArn = runner.startTask(details, taskDef)
  def endpoint = runner.getEndpoint(taskArn, details.cluster)
  
  env['SELENIUM_ENDPOINT'] = endpoint
  env['SELENIUM_PORT'] = '4444'
  env['SELENIUM_SOCKET'] = "${endpoint}:4444"
  env['SELENIUM_TASK'] = taskArn
}