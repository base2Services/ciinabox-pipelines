/***********************************
washeryCleanSnapshots DSL

Cleans old washery snapshots from RDS Snapshot storage

example usage
executeChangeSet(
  region: 'us-east-1', // (required, aws region to deploy the stack)
  accountId: env.DEV_ACCOUNT_ID,
  role: env.CIINABOXV2_ROLE, // IAM role to assume
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

    cleanInstanceWasherySnapshots(client, config.snapshotRetainCount)
    cleanClusterWasherySnapshots(client, config.snapshotRetainCount)
}


def cleanInstanceWasherySnapshots(client, snapshotRetainCount){
    
    //RDS Instance
    def request = new DescribeDBSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBSnapshots(request)
    def snapshots = snapshotsResult.getDBSnapshots()
    def sortedWasherySnapshots = []

    if(snapshots.size() > 0) {
        snapshots.sort{a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
        for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots[i].getDBSnapshotIdentifier().startsWith("washery-scrubbed-")){
                sortedWasherySnapshots.add(snapshots[i])
            }
        }
    }

    //Delete snapshot's until only the snapshotRetainCount amount remains
    while (sortedWasherySnapshots.size() > snapshotRetainCount){

        //Get oldest snapshot and remove it
        current_snapshot = sortedWasherySnapshots.get(0)
        sortedWasherySnapshots.remove(0)
        snapshot_identifier = current_snapshot.getDBSnapshotIdentifier()
        
        //Send delete request
        def delete_request = new DeleteDBSnapshotRequest().withDBSnapshotIdentifier(snapshot_identifier)
        def response = client.deleteDBSnapshot(delete_request)
        echo "Deleted Snapshot - ${snapshot_identifier} created on ${current_snapshot.getSnapshotCreateTime()}"
    }

}

def cleanClusterWasherySnapshots(client, snapshotRetainCount){

    //RDS Cluster
    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()
    def washerySnapshots = []
    
    //Retrieve snapshots prefixed with `washery-scrubbed`
    if(snapshots.size() > 0) {
       for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots[i].getDBClusterSnapshotIdentifier().startsWith("washery-scrubbed-")){
                washerySnapshots.add(snapshots[i])
            }
       }

        //Sort washery snapshots based on snapshot create time
        washerySnapshots.sort{a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
        for (int i = 0; i < washerySnapshots.size(); i++) {
            echo "${washerySnapshots[i]}"
        }
    }


    //Delete snapshot's until only the snapshotRetainCount amount remains
    // while (washerySnapshots.size() > snapshotRetainCount){

    //     //Get oldest snapshot and remove it
    //     current_snapshot = washerySnapshots.get(0)
    //     washerySnapshots.remove(0)
    //     snapshot_identifier = current_snapshot.getDBClusterSnapshotIdentifier()
        
    //     //Send delete request
    //     def delete_request = new DeleteDBClusterSnapshotRequest().withDBClusterSnapshotIdentifier(snapshot_identifier)
    //     def response = client.deleteDBClusterSnapshot(delete_request)
    //     echo "Deleted Snapshot - ${snapshot_identifier} created on ${current_snapshot.getSnapshotCreateTime()}"
    // }
}