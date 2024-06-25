/***********************************
takeSnapshot DSL
Take RDS/DBCluster/Redshift snapshots
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
import com.amazonaws.services.redshift.model.CreateClusterSnapshotRequest
import com.amazonaws.services.redshift.model.SnapshotSortingEntity
import com.amazonaws.services.redshift.model.SortByOrder
import com.amazonaws.services.redshift.model.SnapshotAttributeToSortBy

import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest
import com.amazonaws.services.rds.model.CreateDBSnapshotRequest
import com.amazonaws.services.rds.model.CreateDBClusterSnapshotRequest

import com.base2.ciinabox.aws.AwsClientBuilder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for takeSnapshot()")
  }

  LocalDateTime localDate = LocalDateTime.now()
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
  String formattedString = localDate.format(formatter)
  snapshot_identifier = "${config.resource}-ondemand-${formattedString}"

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

  def create_request = new CreateDBClusterSnapshotRequest().withDBClusterIdentifier(config.resource).withDBClusterSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createDBClusterSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeDBClusterSnapshotsRequest()
      .withDBClusterSnapshotIdentifier(snapshot_identifier)  

    def snapshotsResult = client.describeDBClusterSnapshots(describe_request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()

    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getDBClusterSnapshotIdentifier()
        env["${outputName}_ARN"] = snapshots.get(0).getSourceDBClusterSnapshotArn()
        echo("DBCluster snapshot for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      } 
    } catch(InterruptedException ex) {
        // suppress and continue
    }
  }  
}

@NonCPS
def handleRds(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')

  def create_request = new CreateDBSnapshotRequest().withDBInstanceIdentifier(config.resource).withDBSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createDBSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeDBSnapshotsRequest()
      .withDBSnapshotIdentifier(snapshot_identifier)
    
    def snapshotsResult = client.describeDBSnapshots(describe_request)
    def snapshots = snapshotsResult.getDBSnapshots()

    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getDBSnapshotIdentifier()
        env["${outputName}_ARN"] = snapshots.get(0).getDBSnapshotArn()
        echo("RDS snapshot created for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      }
    } catch(InterruptedException ex) {
      // suppress and continue
    }
  }
}

@NonCPS
def handleRedshift(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')

  def create_request = new CreateClusterSnapshotRequest().withClusterIdentifier(config.resource).withSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createClusterSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeClusterSnapshotsRequest()
      .withSnapshotIdentifier(snapshot_identifier)

    def snapshotsResult = client.describeClusterSnapshots(describe_request)
    def snapshots = snapshotsResult.getSnapshots()
    
    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getSnapshotIdentifier()
        env["${outputName}_OWNER"] = snapshots.get(0).getOwnerAccount()
        env["${outputName}_CLUSTER_ID"] = snapshots.get(0).getClusterIdentifier()
        echo("Redshift snapshot for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      }
    } catch(InterruptedException ex) {
      // suppress and continue
    }
  }
}