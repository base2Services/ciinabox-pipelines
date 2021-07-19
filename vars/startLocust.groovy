/***********************************
startLocust DSL

starts locust on fargate

example usage

startLocust(
  locustMode: master // (optional, defaults to standalone)
  taskRole: 'arn:aws:iam:accountid::role/taskRole' // required, potentially create if not specified?
  locustS3Path: mybucketname/pathtofile // (Required) S3 bucket name and path to file
  locustFile: fileNameInS3 // (Required) locust tests file name
  hostUrl: https://myhost.example.com // (Required) The applications host name to test
  version: '0.12.5', // (optional, defaults to 0.10.0)
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
  def details = [
    subnet: config.subnet,
    securityGroup: config.securityGroup,
    executionRole: config.executionRole,
    cluster: config.cluster,
    cpu: config.get('cpu', '512'),
    memory: config.get('memory', '1024')
  ]


  def task = new GetEcsContainerDetails(config.region)

  def version = config.get('version', '0.10.0')

  def locustMode = config.get('locustMode', 'standalone')

  def taskDef = []
  taskDef << [
    name: 'locust-master',
    image: "base2/locust:${version}",
    ports: [
      [container: 8089, host: 8089],
      [container: 5557, host: 5557],
      [container: 5558, host: 5558]
    ],
    environment: [
      [key: 'AWS_REGION', value: config.region],
      [key: 'LOCUST_S3_PATH', value: config.locustS3Path],
      [key: 'LOCUST_FILE', value: config.locustFile],
      [key: 'LOCUST_MODE', value: locustMode],
      [key: 'HOST_URL', value: config.hostUrl]
    ],
    logs: [
      driver: 'awslogs'
      options: [
        "awslogs-group": config.logGroup
        "awslogs-region": config.region
        "awslogs-stream-prefix": 'locust-master'
      ]

    ]
  ]

  def client = new AwsClientBuilder([region: config.region]).ecs()
  def runner = new EcsTaskRunner('locust', client)
  def taskArn = runner.startTask(details, taskDef)
  def endpoint = runner.getEndpoint(taskArn, details.cluster)

  env['LOCUST_ENDPOINT'] = endpoint
  env['LOCUST_PORT'] = '8089'
  env['LOCUST_SOCKET'] = "${endpoint}:8089"
  env['LOCUST_TASK'] = taskArn
}