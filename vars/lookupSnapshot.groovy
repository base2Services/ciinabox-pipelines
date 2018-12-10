/***********************************
lookupSnapshot DSL

lookup up RDS/Redshift/ESB snapshots

example usage
  lookupSnapshot(
    type: 'redshift',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    resource: 'my-redshift-cluster',
    snapshotType: 'manual',
    snapshot: 'latest',
    envVarName: 'REDSHIFT_SNAPSHOT_ID'
  )

************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-iam', version='1.11.466')
@Grab(group='com.amazonaws', module='aws-java-sdk-sts', version='1.11.466')
@Grab(group='com.amazonaws', module='aws-java-sdk-ssm', version='1.11.466')
@Grab(group='com.amazonaws', module='aws-java-sdk-redshift', version='1.11.466')

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.services.redshift.*
import com.amazonaws.services.redshift.model.*


def call(body) {
  def config = body

  if(!(config.type)){
    throw new GroovyRuntimeException("type must be specified")
  }

  if(config.type.toLowerCase() == 'redshift'){
    handleRedshift(config)
  } else {
    throw new GroovyRuntimeException("type ${config.type} currently not supported")
  }

}


@NonCPS
def setupRedshiftClient(region, awsAccountId = null, role =  null) {
  def cb = AmazonRedshiftClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def getCredentials(awsAccountId, region, roleName) {
  if(env['AWS_SESSION_TOKEN'] != null) {
    return new BasicSessionCredentials(
      env['AWS_ACCESS_KEY_ID'],
      env['AWS_SECRET_ACCESS_KEY'],
      env['AWS_SESSION_TOKEN']
    )
  } else if(awsAccountId != null && roleName != null) {
    def stsCreds = assumeRole(awsAccountId, region, roleName)
    return new BasicSessionCredentials(
      stsCreds.getAccessKeyId(),
      stsCreds.getSecretAccessKey(),
      stsCreds.getSessionToken()
    )
  } else {
    return null
  }
}

@NonCPS
def assumeRole(awsAccountId, region, roleName) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(3600)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}

@NonCPS
def handleRedshift(config) {
  def redshift = setupRedshiftClient(config.region, config.accountId, config.role)

  def outputName = config.get('envVarName', 'SNAPSHOT_ID')
  def snapshot = config.get('snapshot', 'latest')

  def request = new DescribeClusterSnapshotsRequest()
    .withClusterIdentifier(config.resource)
    .withSortingEntities(new SnapshotSortingEntity()
      .withAttribute(SnapshotAttributeToSortBy.CREATE_TIME)
      .withSortOrder(SortByOrder.DESC)
    )

  if(config.snapshotType) {
    request.setSnapshotType(config.snapshotType)
  } 
  def snapshots = redshift.describeClusterSnapshots(request)
  if(snapshot.toLowerCase() == 'latest') {
    if(snapshots.getSnapshots().size() > 0) {
      env[outputName] = snapshots.getSnapshots().get(0).getSnapshotIdentifier()
      env["${outputName}_OWNER"] = snapshots.getSnapshots().get(0).getOwnerAccount()
      env["${outputName}_CLUSTER_ID"] = snapshots.getSnapshots().get(0).getClusterIdentifier()
    }
  } else {
    throw new GroovyRuntimeException("current only snapshot 'latest' is supported")
  }
}