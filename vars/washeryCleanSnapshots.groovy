/***********************************
washeryCleanSnapshots DSL

Cleans old washery snapshots from RDS Snapshot storage

example usage
washeryCleanSnapshots(
  region: 'us-east-1', // (required, aws region to deploy the stack)
  accountId: env.DEV_ACCOUNT_ID,
  role: env.CIINABOXV2_ROLE, // IAM role to assume
  type: 'rds|dbcluster',
  snapshotRetainCount: 3 // The number of washery snapshots to keep stored
)
************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DeleteDBSnapshotRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest
import com.amazonaws.services.rds.model.DeleteDBClusterSnapshotRequest

def call(body) {
    def config = body
    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        awsAccountId: config.accountId,
        role: config.role
    ])  

    def client = clientBuilder.rds()

    if (config.type.toLowerCase() == 'rds') {
        cleanInstanceWasherySnapshots(client, config.snapshotRetainCount)
    } else if (config.type.toLowerCase() == 'dbcluster') {
        cleanClusterWasherySnapshots(client, config.snapshotRetainCount)
    } else {
        throw new GroovyRuntimeException("washeryCleanSnapshots() doesn't support type ${config.type}")
    }
}

@NonCPS
def cleanInstanceWasherySnapshots(client, snapshotRetainCount){
    //RDS Instance
    def request = new DescribeDBSnapshotsRequest()
            .withSnapshotType("manual")
    def snapshotsResult = client.describeDBSnapshots(request)
    def snapshots = snapshotsResult.getDBSnapshots()
    def washerySnapshots = []

    //Retrieve snapshots prefixed with `washery-scrubbed`
    for (snapshot in snapshots) {
        if (snapshot.getDBSnapshotIdentifier().startsWith("washery-scrubbed-")){
            washerySnapshots.add(snapshot)
        }
    }

    //Sort washery snapshots based on snapshot create time
    washerySnapshots.sort{a,b-> b.getSnapshotCreateTime().compareTo(a.getSnapshotCreateTime())}
    
    //If retain count is less than the size of the total washery snapshots
    if (snapshotRetainCount < washerySnapshots.size()){
        def delete = washerySnapshots[snapshotRetainCount..-1]
        println delete

        //Delete snapshot's until only the snapshotRetainCount amount remains
        // for (snapshot in delete) {
        //     snapshot_identifier = snapshot.getDBSnapshotIdentifier()
        //     def delete_request = new DeleteDBSnapshotRequest().withDBSnapshotIdentifier(snapshot_identifier)
        //     def response = client.deleteDBSnapshot(delete_request)
        //     echo "Deleted Snapshot - ${snapshot_identifier} created on ${current_snapshot.getSnapshotCreateTime()}"
        // }
    }
}

@NonCPS
def cleanClusterWasherySnapshots(client, snapshotRetainCount){
    //RDS Cluster
    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()
    def washerySnapshots = []

    //Retrieve snapshots prefixed with `washery-scrubbed`
    for (snapshot in snapshots) {
        if (snapshot.getDBClusterSnapshotIdentifier().startsWith("washery-scrubbed-")){
            washerySnapshots.add(snapshot)
        }
    }

    //Sort washery snapshots based on snapshot create time
    washerySnapshots.sort{a,b-> b.getSnapshotCreateTime().compareTo(a.getSnapshotCreateTime())}

    //If retain count is less than the size of the total washery snapshots
    if (snapshotRetainCount < washerySnapshots.size()){
        def delete = washerySnapshots[snapshotRetainCount..-1]
        println delete

        //Delete snapshot's until only the snapshotRetainCount amount remains
        // for (snapshot in delete) {
        //     snapshot_identifier = snapshot.getDBClusterSnapshotIdentifier()
        //     def delete_request = new DeleteDBSnapshotRequest().withDBClusterSnapshotIdentifier(snapshot_identifier)
        //     def response = client.deleteDBClusterSnapshot(delete_request)
        //     echo "Deleted Snapshot - ${snapshot_identifier} created on ${current_snapshot.getSnapshotCreateTime()}"
        // }
    }
}