

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest

def call(body) {
    def config = body
   
    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        awsAccountId: config.accountId,
        role: config.role
    ])  

    def client = clientBuilder.rds()

    listInstanceWasherySnapshots(client)
    listClusterWasherySnapshots(client)
}


def listInstanceWasherySnapshots(client){
    
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
                washerySnapshots.add(snapshots[i])
            }
        }
    }

    for (int i = 0; i < washerySnapshots.size(); i++) {
        echo "${washerySnapshots[i]}"
    }

}

def listClusterWasherySnapshots(client){

    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()
    def sortedWasherySnapshots = []

    if(snapshots.size() > 0) {
       snapshots.sort {a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
       for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots[i].getDBClusterSnapshotIdentifier().startsWith("washery-scrubbed-")){
                washerySnapshots.add(snapshots[i])
            }
       }
    }

    //Delete snapshot's until only 3 remain
    while (sortedWasherySnapshots.size() > 3){
        current_snapshot = sortedWasherySnapshots.shift()
        snapshot_identifier = current_snapshot.getDBClusterSnapshotIdentifier()
        DeleteDBSnapshotRequest request = new DeleteDBSnapshotRequest().withDBSnapshotIdentifier(snapshot_identifier);
        DBSnapshot response = client.deleteDBSnapshot(request);
    }
}