

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

    for (int i = 0; i < snapshots.size(); i++) {
        echo "${i}: ${snapshots[i]}}"
    }

}

def listClusterWasherySnapshots(client){

    def request = new DescribeDBClusterSnapshotsRequest()
            .withSnapshotType("manual")
    
    def snapshotsResult = client.describeDBClusterSnapshots(request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()

    for (int i = 0; i < snapshots.size(); i++) {
        echo "${i}: ${snapshots[i]}}"
    }

}