

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

    if(snapshots.size() > 0) {
        def sorted_snaps = snapshots.sort{snap.getSnapshotCreateTime()}
        echo "${sorted_snaps}"
        for (int i = 0; i < sorted_snaps.size(); i++) {
            if (sorted_snaps[i].getDBSnapshotIdentifier().startsWith("washery-scrubbed-")){
                echo "${sorted_snaps[i]}"
            }
        }
    }

}

def listClusterWasherySnapshots(client){

    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()

    if(snapshots.size() > 0) {
        def sorted_snaps = snapshots.sort {a,b-> b.getSnapshotCreateTime()<=>a.getSnapshotCreateTime()}
        for (int i = 0; i < sorted_snaps.size(); i++) {
            if (sorted_snaps[i].getDBClusterSnapshotIdentifier().startsWith("washery-scrubbed-")){
                echo "${sorted_snaps[i]}"
            }
        }
    }
}