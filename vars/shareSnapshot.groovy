/***********************************
shareSnapshot DSL

share a DBCluster snapshot with the specified account

example usage
  shareSnapshot(
    type: 'rds|dbcluster',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    snapshotId: 'env.SNAPSHOT_ID',
    shareAccountId: env.SHARE_ACCOUNT_ID
  )

************************************/

import com.amazonaws.services.rds.model.ModifyDBClusterSnapshotAttributeRequest
import com.amazonaws.services.rds.model.ModifyDBSnapshotAttributeRequest
import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for shareSnapshot()")
  }

  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('accountId', null),
    role: config.get('role', null)
  ])

  if (config.type.toLowerCase() == 'dbcluster') {
    def client = clientBuilder.rds()
    handleDBCluster(client, config)
  } else if (config.type.toLowerCase() == 'rds') {
    handleDBInstance(client, config)
  } else {
    throw new GroovyRuntimeException("shareSnapshot() doesn't support share of type ${config.type}")
  }
}

def handleDBCluster(client, config){
    def request = new ModifyDBClusterSnapshotAttributeRequest()
        .withDBClusterSnapshotIdentifier(config.snapshotId).withAttributeName("restore").withValuesToAdd(config.shareAccountId)
    
    client.modifyDBClusterSnapshotAttribute(request);
}

def handleDBInstance(client, config){
    def request = new ModifyDBSnapshotAttributeRequest()
        .withDBSnapshotIdentifier(config.snapshotId).withAttributeName("restore").withValuesToAdd(config.shareAccountId)
    
    client.modifyDBSnapshotAttribute(request);
}