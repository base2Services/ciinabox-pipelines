/***********************************
takeSnapshot DSL
Take RDS/DBCluster/Redshift/EBS? snapshots
example usage
  takeSnapshot(
    type: 'redshift|rds|dbcluster',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    resource: 'my-redshift-cluster',
    envVarName: 'REDSHIFT_SNAPSHOT_ID'
  )
************************************/

import com.amazonaws.services.redshift.model.DescribeClusterSnapshotsRequest
import com.amazonaws.services.redshift.model.SnapshotSortingEntity
import com.amazonaws.services.redshift.model.SortByOrder
import com.amazonaws.services.redshift.model.SnapshotAttributeToSortBy

// start with RDS snapshots
import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest
import com.amazonaws.services.rds.model.CreateDBSnapshotRequest
import com.amazonaws.services.rds.model.CreateDBClusterSnapshotRequest

import com.base2.ciinabox.aws.AwsClientBuilder
import groovy.time.*
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter;

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for takeSnapshot()")
  }

  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('accountId', null),
    role: config.get('role', null)
  ])

  if(config.type.toLowerCase() == 'redshift') {
    def client = clientBuilder.redshift()
    handleRedshift(client, config)
  } else if (config.type.toLowerCase() == 'rds') {
    def client = clientBuilder.rds()
    handleRds(client, config)
  } else if (config.type.toLowerCase() == 'dbcluster') {
    def client = clientBuilder.rds()
    handleDBCluster(client, config)
  } else {
    error("takeSnapshot() doesn't support taking a snapshot of type ${config.type}")
  }

}

@NonCPS
def handleDBCluster(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')
  //def sortBy = config.get('snapshot', 'latest')

  // create a cluster snapshot
  LocalDate localDate = LocalDate.now()
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-hhmm")
  String formattedString = localDate.format(formatter)
  println(config.resource)
  String snapshot_identifier = "${config.resource}-ondemand-${formattedString}"
  def create_request = new CreateDBClusterSnapshotRequest().withDBClusterIdentifier(config.resource).withDBClusterSnapshotIdentifier(snapshot_identifier)
  println(create_request)
  def create_snapshot_result = client.createDBClusterSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")
  // query for the newly taken snapshot and only return once it's available
  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeDBClusterSnapshotsRequest()
      .withDBClusterSnapshotIdentifier(snapshot_identifier)  

    def snapshotsResult =  client.describeDBClusterSnapshots(describe_request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()

    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    sleep(10000)
  }
  if(snapshot_status == "available") {
    env[outputName] = snapshots.get(0).getDBClusterSnapshotIdentifier()
    env["${outputName}_ARN"] = snapshots.get(0).getSourceDBClusterSnapshotArn()
    echo("DBCluster snapshot for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
  } else {
    echo("An error occurred somewhere")
  }
}

@NonCPS
def handleRds(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')
  def sortBy = config.get('snapshot', 'latest')

  def request = new DescribeDBSnapshotsRequest()
    .withDBInstanceIdentifier(config.resource)

  

  def snapshotsResult = client.describeDBSnapshots(request)
  def snapshots = snapshotsResult.getDBSnapshots()

  if(snapshots.size() > 0) {
    if(sortBy.toLowerCase() == 'latest') {
      def sorted_snaps = snapshots.sort {a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
      env[outputName] = sorted_snaps.get(0).getDBSnapshotIdentifier()
      env["${outputName}_ARN"] = sorted_snaps.get(0).getDBSnapshotArn()
      echo("Latest snapshot found for ${config.resource} is ${sorted_snaps.get(0).getDBSnapshotIdentifier()} created on ${sorted_snaps.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')}")
    } else {
      error("currently only snapshot 'latest' is supported")
    }
  } else {
    error("unable to find RDS snapshots for resource ${config.resource}")
  }
}

@NonCPS
def handleRedshift(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')
  def snapshot = config.get('snapshot', 'latest')

  def request = new DescribeClusterSnapshotsRequest()
    .withClusterIdentifier(config.resource)
    .withSortingEntities(new SnapshotSortingEntity()
      .withAttribute(SnapshotAttributeToSortBy.CREATE_TIME)
      .withSortOrder(SortByOrder.DESC)
    )

  
  def snapshots = client.describeClusterSnapshots(request)
  if(snapshot.toLowerCase() == 'latest') {
    if(snapshots.getSnapshots().size() > 0) {
      env[outputName] = snapshots.getSnapshots().get(0).getSnapshotIdentifier()
      env["${outputName}_OWNER"] = snapshots.getSnapshots().get(0).getOwnerAccount()
      env["${outputName}_CLUSTER_ID"] = snapshots.getSnapshots().get(0).getClusterIdentifier()
    } else {
      error("unable to find redshift snapshots for resource ${config.resource}")
    }
  } else {
    error("currently only snapshot 'latest' is supported")
  }
}