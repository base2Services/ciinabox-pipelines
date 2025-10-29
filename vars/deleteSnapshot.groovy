/***********************************
deleteSnapshot DSL
Delete RDS/DBCluster/Redshift snapshots
example usage
  deleteSnapshot(
    type: 'redshift|rds|dbcluster',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    snapshot: 'my-redshift-cluster-snapshot'
  )
************************************/

import com.amazonaws.services.redshift.model.DeleteClusterSnapshotRequest
import com.amazonaws.services.rds.model.DeleteDBSnapshotRequest
import com.amazonaws.services.rds.model.DeleteDBClusterSnapshotRequest

import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for deleteSnapshot()")
  }

  if(!(config.resource)){
    error("resource must be specified for deleteSnapshot()")
  }

  if(!(config.region)){
    error("region must be specified for deleteSnapshot()")
  }

  if(!(config.snapshot)){
    error("snapshot must be specified for deleteSnapshot()")
  }
  else {
    snapshot_identifier = config.snapshot
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
    error("deleteSnapshot() doesn't support deleting a snapshot of type ${config.type}")
  }

}

@NonCPS
def handleDBCluster(client, config) {
  try {
    def delete_request = new DeleteDBClusterSnapshotRequest().withDBClusterSnapshotIdentifier(snapshot_identifier)
    def delete_snapshot_result = client.deleteDBClusterSnapshot(delete_request)
    echo("Snapshot ${snapshot_identifier} deleted")
  } catch (Exception e) {
    error("Error deleting DB Cluster snapshot ${snapshot_identifier}: ${e.getMessage()}")
  }
}

@NonCPS
def handleRds(client, config) {
  try {
    def delete_request = new DeleteDBSnapshotRequest().withDBSnapshotIdentifier(snapshot_identifier)
    def delete_snapshot_result = client.deleteDBSnapshot(delete_request)
    echo("Snapshot ${snapshot_identifier} deleted")
  } catch (Exception e) {
    error("Error deleting RDS snapshot ${snapshot_identifier}: ${e.getMessage()}")
  }
}

@NonCPS
def handleRedshift(client, config) {
  try {
    def delete_request = new DeleteClusterSnapshotRequest().withSnapshotIdentifier(snapshot_identifier)
    def delete_snapshot_result = client.deleteClusterSnapshot(delete_request)
    echo("Snapshot ${snapshot_identifier} deleted")
  } catch (Exception e) {
    error("Error deleting Redshift snapshot ${snapshot_identifier}: ${e.getMessage()}")
  }
}