

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

    cleanInstanceWasherySnapshots(client)
    cleanClusterWasherySnapshots(client)
}


def cleanInstanceWasherySnapshots(client){
    
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

    //Delete snapshot's until only 3 remain
    while (sortedWasherySnapshots.size() > 3){

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

def cleanClusterWasherySnapshots(client){

    //RDS Cluster
    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()
    def sortedWasherySnapshots = []
    
    //Retrieve snapshots prefixed with `washery-scrubbed` and sort them into a new list
    if(snapshots.size() > 0) {
       snapshots.sort {a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
       for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots[i].getDBClusterSnapshotIdentifier().startsWith("washery-scrubbed-")){
                sortedWasherySnapshots.add(snapshots[i])
            }
       }
    }

    //Delete snapshot's until only 3 remain
    while (sortedWasherySnapshots.size() > 3){

        //Get oldest snapshot and remove it
        current_snapshot = sortedWasherySnapshots.get(0)
        sortedWasherySnapshots.remove(0)
        snapshot_identifier = current_snapshot.getDBClusterSnapshotIdentifier()
        
        //Send delete request
        def delete_request = new DeleteDBClusterSnapshotRequest().withDBClusterSnapshotIdentifier(snapshot_identifier)
        def response = client.deleteDBClusterSnapshot(delete_request)
        echo "Deleted Snapshot - ${snapshot_identifier} created on ${current_snapshot.getSnapshotCreateTime()}"
    }
}