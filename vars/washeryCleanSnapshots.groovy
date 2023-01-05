

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest

def call(body) {
    def config = body
   
    def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.accountId,
    role: config.role
    ])  

    def client = clientBuilder.rds()

    listWasherySnapshots(client)
}


def listWasherySnapshots(client){
    def describeDBSnapshotsRequest = new DescribeDBSnapshotsRequest()
            .withSnapshotType("manual")
    
    def val = client.describeDBSnapshots(describeDBSnapshotsRequest)

    echo val
}