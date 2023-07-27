/***********************************
lookupSnapshot DSL

lookup up RDS/DBCluster/Redshift snapshots

example usage
  lookupSnapshot(
    type: 'redshift|rds|dbcluster',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    resource: 'my-redshift-cluster',
    snapshotType: 'manual|automated|shared',
    snapshot: 'latest',
    envVarName: 'REDSHIFT_SNAPSHOT_ID'
  )

************************************/

import com.amazonaws.services.redshift.model.DescribeClusterSnapshotsRequest
import com.amazonaws.services.redshift.model.SnapshotSortingEntity
import com.amazonaws.services.redshift.model.SortByOrder
import com.amazonaws.services.redshift.model.SnapshotAttributeToSortBy

import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest

import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for lookupSnapshot()")
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
    error("lookupSnapshot() doesn't support lookup of type ${config.type}")
  }

}

@NonCPS
def handleDBCluster(client, config) {
    def outputName = config.get('envVarName', 'SNAPSHOT_ID')
    def sortBy = config.get('snapshot', 'latest')
    def request = new DescribeDBClusterSnapshotsRequest().withDBClusterIdentifier(config.resource)

    if (config.snapshotType) {
        request.setSnapshotType(config.snapshotType)
    }

    List<DBClusterSnapshot> allSnapshots = []
    String marker = null

    do {
        request.setMarker(marker)
        DescribeDBClusterSnapshotsResult snapshotsResult = client.describeDBClusterSnapshots(request)
        List<DBClusterSnapshot> snapshots = snapshotsResult.getDBClusterSnapshots()

        allSnapshots.addAll(snapshots)

        marker = snapshotsResult.getMarker()
    } while (marker)

    if (allSnapshots.size() > 0) {
        if (sortBy.toLowerCase() == 'latest') {
            allSnapshots = allSnapshots.sort { a, b -> b.getSnapshotCreateTime().compareTo(a.getSnapshotCreateTime()) }
            env[outputName] = allSnapshots.get(0).getDBClusterSnapshotIdentifier()
            env["${outputName}_ARN"] = allSnapshots.get(0).getSourceDBClusterSnapshotArn()
            echo("Latest DBCluster snapshot found for ${config.resource} is ${allSnapshots.get(0).getDBClusterSnapshotIdentifier()} created on ${allSnapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')}")
        } else {
            error("Currently only snapshot 'latest' is supported")
        }
    } else {
        error("Unable to find DBCluster snapshots for resource ${config.resource}")
    }
}

@NonCPS
def handleRds(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')
  def sortBy = config.get('snapshot', 'latest')

  def request = new DescribeDBSnapshotsRequest()
    .withDBInstanceIdentifier(config.resource)

  if(config.snapshotType) {
    request.setSnapshotType(config.snapshotType)
  } 

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

  if(config.snapshotType) {
    request.setSnapshotType(config.snapshotType)
  } 
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